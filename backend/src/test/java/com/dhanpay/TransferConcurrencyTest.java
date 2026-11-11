package com.dhanpay;

import com.dhanpay.dto.transfer.TransferRequestDto;
import com.dhanpay.entity.Role;
import com.dhanpay.entity.User;
import com.dhanpay.entity.UserStatus;
import com.dhanpay.entity.Wallet;
import com.dhanpay.entity.WalletStatus;
import com.dhanpay.exception.BusinessException;
import com.dhanpay.ledger.EntryDirection;
import com.dhanpay.ledger.LedgerAccount;
import com.dhanpay.ledger.LedgerAccountRepository;
import com.dhanpay.ledger.LedgerAccountStatus;
import com.dhanpay.ledger.LedgerAccountType;
import com.dhanpay.ledger.LedgerEntry;
import com.dhanpay.ledger.LedgerEntryRepository;
import com.dhanpay.ledger.LedgerTransaction;
import com.dhanpay.ledger.LedgerTransactionRepository;
import com.dhanpay.ledger.LedgerTransactionStatus;
import com.dhanpay.ledger.LedgerTransactionType;
import com.dhanpay.repository.UserRepository;
import com.dhanpay.repository.WalletRepository;
import com.dhanpay.security.UserPrincipal;
import com.dhanpay.service.TransferService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DeadlockLoserDataAccessException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * High-concurrency stress and race-condition integration tests for {@link TransferService}.
 */
class TransferConcurrencyTest extends BaseIntegrationTest {

    private static final String SYSTEM_HOLDING_ACCOUNT = "GL-SYS-HOLDING-INR-0001";
    private static final MockHttpServletRequest MOCK_HTTP = new MockHttpServletRequest();

    @Autowired
    private TransferService transferService;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private LedgerAccountRepository ledgerAccountRepository;

    @Autowired
    private LedgerTransactionRepository ledgerTransactionRepository;

    @Autowired
    private LedgerEntryRepository ledgerEntryRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Wallet createFundedWallet(String tag, BigDecimal startingBalance) {
        String uniqueSuffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();

        User user = new User(
                "Concurrency User " + tag,
                "conc+" + tag + "+" + uniqueSuffix.toLowerCase() + "@dhanpay.test",
                passwordEncoder.encode("Pass@1234"),
                Role.ROLE_CUSTOMER,
                UserStatus.ACTIVE
        );
        user = userRepository.save(user);

        Wallet wallet = new Wallet(
                user,
                "WAL-CONC-" + uniqueSuffix,
                startingBalance,
                "INR",
                WalletStatus.ACTIVE
        );
        wallet = walletRepository.save(wallet);

        LedgerAccount ledgerAccount = new LedgerAccount(
                wallet,
                "LA-CONC-" + uniqueSuffix,
                LedgerAccountType.CUSTOMER_WALLET,
                "INR",
                LedgerAccountStatus.ACTIVE
        );
        ledgerAccount = ledgerAccountRepository.save(ledgerAccount);

        if (startingBalance.compareTo(BigDecimal.ZERO) > 0) {
            LedgerAccount systemHolding = ledgerAccountRepository.findByAccountNumber(SYSTEM_HOLDING_ACCOUNT)
                    .orElseThrow(() -> new IllegalStateException("System holding ledger account missing"));

            LedgerTransaction openingTx = ledgerTransactionRepository.save(
                    new LedgerTransaction(
                            null,
                            "LTX-TEST-OPEN-" + uniqueSuffix,
                            LedgerTransactionType.SYSTEM_OPENING,
                            LedgerTransactionStatus.POSTED
                    )
            );
            ledgerEntryRepository.saveAll(List.of(
                    new LedgerEntry(openingTx, systemHolding, EntryDirection.DEBIT, startingBalance, "INR"),
                    new LedgerEntry(openingTx, ledgerAccount, EntryDirection.CREDIT, startingBalance, "INR")
            ));
        }

        return wallet;
    }

    @Test
    @DisplayName("Double-spending prevention: 2 concurrent ₹700 transfers from ₹1,000 wallet allow exactly 1 success")
    void doubleSpendPrevention_exactlyOneSucceeds() throws InterruptedException {
        BigDecimal startBalance = new BigDecimal("1000.0000");
        BigDecimal transferAmount = new BigDecimal("700.0000");
        BigDecimal expectedFinalBalance = new BigDecimal("300.0000");

        Wallet senderWallet = createFundedWallet("sender", startBalance);
        Wallet recipientWallet = createFundedWallet("recipient", BigDecimal.ZERO);
        UserPrincipal senderPrincipal = UserPrincipal.from(senderWallet.getUser());

        AtomicInteger successes = new AtomicInteger(0);
        AtomicInteger insufficientFailures = new AtomicInteger(0);
        List<Throwable> unexpectedErrors = new CopyOnWriteArrayList<>();

        CountDownLatch readyLatch = new CountDownLatch(2);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        for (int i = 0; i < 2; i++) {
            final String idempotencyKey = "DBL-SPEND-" + i + "-" + UUID.randomUUID();
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startGate.await();
                    TransferRequestDto request = new TransferRequestDto(
                            recipientWallet.getWalletReference(),
                            transferAmount,
                            "Concurrent ₹700 debit attempt"
                    );
                    transferService.executeTransfer(senderPrincipal, idempotencyKey, request, MOCK_HTTP);
                    successes.incrementAndGet();
                } catch (BusinessException ex) {
                    if ("INSUFFICIENT_FUNDS".equals(ex.getCode())) {
                        insufficientFailures.incrementAndGet();
                    } else {
                        unexpectedErrors.add(ex);
                    }
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    unexpectedErrors.add(ex);
                } catch (Throwable ex) {
                    unexpectedErrors.add(ex);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        readyLatch.await();
        startGate.countDown();
        doneLatch.await();
        executor.shutdown();

        assertThat(unexpectedErrors)
                .as("No unexpected exceptions expected: %s", unexpectedErrors)
                .isEmpty();
        assertThat(successes.get()).isEqualTo(1);
        assertThat(insufficientFailures.get()).isEqualTo(1);

        Wallet updatedSender = walletRepository.findByWalletReference(senderWallet.getWalletReference()).orElseThrow();
        Wallet updatedRecipient = walletRepository.findByWalletReference(recipientWallet.getWalletReference()).orElseThrow();

        assertThat(updatedSender.getBalance()).isEqualByComparingTo(expectedFinalBalance);
        assertThat(updatedSender.getBalance()).isGreaterThanOrEqualTo(BigDecimal.ZERO);
        assertThat(updatedRecipient.getBalance()).isEqualByComparingTo(transferAmount);
    }

    @Test
    @DisplayName("Bidirectional deadlock-freedom: 10 concurrent A↔B transfers complete with zero deadlocks or timeouts")
    void bidirectionalTransfers_noDeadlocks() throws InterruptedException {
        int threadCount = 10;
        BigDecimal walletStart = new BigDecimal("5000.0000");
        BigDecimal transferAmount = new BigDecimal("100.0000");
        BigDecimal expectedTotal = walletStart.add(walletStart);

        Wallet walletA = createFundedWallet("bidirA", walletStart);
        Wallet walletB = createFundedWallet("bidirB", walletStart);
        UserPrincipal principalA = UserPrincipal.from(walletA.getUser());
        UserPrincipal principalB = UserPrincipal.from(walletB.getUser());

        AtomicInteger successes = new AtomicInteger(0);
        AtomicInteger deadlockOrTimeoutCount = new AtomicInteger(0);
        List<Throwable> unexpectedErrors = new CopyOnWriteArrayList<>();

        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startGate.await();
                    boolean aToB = (index % 2 == 0);
                    UserPrincipal sender = aToB ? principalA : principalB;
                    String recipientRef = aToB ? walletB.getWalletReference() : walletA.getWalletReference();

                    TransferRequestDto request = new TransferRequestDto(
                            recipientRef,
                            transferAmount,
                            "Bidirectional stress thread-" + index
                    );
                    transferService.executeTransfer(
                            sender,
                            "BIDIR-" + index + "-" + UUID.randomUUID(),
                            request,
                            MOCK_HTTP
                    );
                    successes.incrementAndGet();
                } catch (CannotAcquireLockException | DeadlockLoserDataAccessException ex) {
                    deadlockOrTimeoutCount.incrementAndGet();
                    unexpectedErrors.add(ex);
                } catch (BusinessException ex) {
                    if (!"INSUFFICIENT_FUNDS".equals(ex.getCode())) {
                        unexpectedErrors.add(ex);
                    }
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    unexpectedErrors.add(ex);
                } catch (Throwable ex) {
                    if (containsLockOrDeadlockCause(ex)) {
                        deadlockOrTimeoutCount.incrementAndGet();
                    }
                    unexpectedErrors.add(ex);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        readyLatch.await();
        startGate.countDown();
        doneLatch.await();
        executor.shutdown();

        assertThat(deadlockOrTimeoutCount.get())
                .as("Deterministic lock ordering must eliminate SQL deadlocks and lock wait timeouts")
                .isZero();
        assertThat(unexpectedErrors)
                .as("Unexpected concurrency failures: %s", unexpectedErrors)
                .isEmpty();
        assertThat(successes.get()).isEqualTo(threadCount);

        BigDecimal balanceA = walletRepository.findByWalletReference(walletA.getWalletReference()).orElseThrow().getBalance();
        BigDecimal balanceB = walletRepository.findByWalletReference(walletB.getWalletReference()).orElseThrow().getBalance();

        assertThat(balanceA.add(balanceB)).isEqualByComparingTo(expectedTotal);
    }

    private static boolean containsLockOrDeadlockCause(Throwable throwable) {
        Throwable cursor = throwable;
        while (cursor != null) {
            String message = cursor.getMessage();
            if (message != null) {
                String lower = message.toLowerCase();
                if (lower.contains("deadlock") || lower.contains("lock wait timeout")) {
                    return true;
                }
            }
            cursor = cursor.getCause();
        }
        return false;
    }
}
