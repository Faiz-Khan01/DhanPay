package com.dhanpay.service;

import com.dhanpay.dto.statement.StatementPeriod;
import com.dhanpay.dto.statement.WalletStatementDto;
import com.dhanpay.entity.Role;
import com.dhanpay.entity.Transfer;
import com.dhanpay.entity.TransferStatus;
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
import com.dhanpay.ledger.LedgerTransactionStatus;
import com.dhanpay.ledger.LedgerTransactionType;
import com.dhanpay.repository.WalletRepository;
import com.dhanpay.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StatementServiceTest {

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private LedgerAccountRepository ledgerAccountRepository;

    @Mock
    private LedgerEntryRepository ledgerEntryRepository;

    @InjectMocks
    private StatementService statementService;

    private User user;
    private Wallet wallet;
    private LedgerAccount ledgerAccount;

    @BeforeEach
    void setUp() {
        user = new User("Faiz Khan", "faiz@example.com", "h", Role.ROLE_CUSTOMER, UserStatus.ACTIVE);
        ReflectionTestUtils.setField(user, "id", 1L);

        wallet = new Wallet(user, "WLT-IND-10000001", new BigDecimal("12000.0000"), "INR", WalletStatus.ACTIVE);
        ReflectionTestUtils.setField(wallet, "id", 10L);

        ledgerAccount = new LedgerAccount(wallet, "GL-CUST-INR-0001", LedgerAccountType.CUSTOMER_WALLET, "INR", LedgerAccountStatus.ACTIVE);
        ReflectionTestUtils.setField(ledgerAccount, "id", 100L);
    }

    @Test
    void generateStatement_computesCorrectOpeningAndClosingBalances() {
        when(walletRepository.findByUserId(1L)).thenReturn(Optional.of(wallet));
        when(ledgerAccountRepository.findByWalletId(10L)).thenReturn(Optional.of(ledgerAccount));

        // Opening credits: 10,000, debits: 2,000 => Opening Balance = 8,000
        when(ledgerEntryRepository.sumAmountByAccountIdAndDirectionBefore(eq(100L), eq(EntryDirection.CREDIT), any(Instant.class)))
                .thenReturn(new BigDecimal("10000.0000"));
        when(ledgerEntryRepository.sumAmountByAccountIdAndDirectionBefore(eq(100L), eq(EntryDirection.DEBIT), any(Instant.class)))
                .thenReturn(new BigDecimal("2000.0000"));

        User sender = new User("Alice Sharma", "alice@example.com", "h", Role.ROLE_CUSTOMER, UserStatus.ACTIVE);
        Wallet senderWallet = new Wallet(sender, "WLT-IND-10000002", new BigDecimal("50000.0000"), "INR", WalletStatus.ACTIVE);

        User recipient = new User("Bob Verma", "bob@example.com", "h", Role.ROLE_CUSTOMER, UserStatus.ACTIVE);
        Wallet recipientWallet = new Wallet(recipient, "WLT-IND-10000003", new BigDecimal("50000.0000"), "INR", WalletStatus.ACTIVE);

        Transfer transferIn = new Transfer("TXN-IN-001", senderWallet, wallet, new BigDecimal("5000.0000"), "INR", TransferStatus.SUCCESS, "Freelance payment");
        LedgerTransaction lt1 = new LedgerTransaction(transferIn, "LTX-001", LedgerTransactionType.P2P_TRANSFER, LedgerTransactionStatus.POSTED);
        LedgerEntry entry1 = new LedgerEntry(lt1, ledgerAccount, EntryDirection.CREDIT, new BigDecimal("5000.0000"), "INR");
        ReflectionTestUtils.setField(entry1, "id", 501L);
        ReflectionTestUtils.setField(entry1, "createdAt", Instant.now().minus(2, ChronoUnit.DAYS));

        Transfer transferOut = new Transfer("TXN-OUT-002", wallet, recipientWallet, new BigDecimal("1000.0000"), "INR", TransferStatus.SUCCESS, "Dinner bill");
        LedgerTransaction lt2 = new LedgerTransaction(transferOut, "LTX-002", LedgerTransactionType.P2P_TRANSFER, LedgerTransactionStatus.POSTED);
        LedgerEntry entry2 = new LedgerEntry(lt2, ledgerAccount, EntryDirection.DEBIT, new BigDecimal("1000.0000"), "INR");
        ReflectionTestUtils.setField(entry2, "id", 502L);
        ReflectionTestUtils.setField(entry2, "createdAt", Instant.now().minus(1, ChronoUnit.DAYS));

        when(ledgerEntryRepository.findEntriesForAccountBetween(eq(100L), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(entry1, entry2));

        LocalDate start = LocalDate.now().minusDays(15);
        LocalDate end = LocalDate.now();

        WalletStatementDto result = statementService.generateStatement(
                UserPrincipal.from(user),
                start,
                end,
                StatementPeriod.CUSTOM
        );

        assertThat(result.statementReference()).startsWith("STMT-IND-");
        assertThat(result.account().walletReference()).isEqualTo("WLT-IND-10000001");
        assertThat(result.account().fullName()).isEqualTo("Faiz Khan");

        // Mathematical check: 8000 + 5000 - 1000 = 12000
        assertThat(result.summary().openingBalance()).isEqualByComparingTo("8000.0000");
        assertThat(result.summary().totalCredits()).isEqualByComparingTo("5000.0000");
        assertThat(result.summary().totalDebits()).isEqualByComparingTo("1000.0000");
        assertThat(result.summary().netMovement()).isEqualByComparingTo("4000.0000");
        assertThat(result.summary().closingBalance()).isEqualByComparingTo("12000.0000");
        assertThat(result.summary().totalTransactions()).isEqualTo(2);

        assertThat(result.transactions()).hasSize(2);
        assertThat(result.transactions().get(0).type()).isEqualTo("TRANSFER_IN");
        assertThat(result.transactions().get(0).creditAmount()).isEqualByComparingTo("5000.0000");
        assertThat(result.transactions().get(0).balanceAfter()).isEqualByComparingTo("13000.0000");

        assertThat(result.transactions().get(1).type()).isEqualTo("TRANSFER_OUT");
        assertThat(result.transactions().get(1).debitAmount()).isEqualByComparingTo("1000.0000");
        assertThat(result.transactions().get(1).balanceAfter()).isEqualByComparingTo("12000.0000");

        // Tamper seal check
        assertThat(result.digitalVerificationHash()).matches("^[a-f0-9]{64}$");
    }

    @Test
    void generatePrintableHtml_containsLetterheadAndTransactions() {
        when(walletRepository.findByUserId(1L)).thenReturn(Optional.of(wallet));
        when(ledgerAccountRepository.findByWalletId(10L)).thenReturn(Optional.of(ledgerAccount));
        when(ledgerEntryRepository.sumAmountByAccountIdAndDirectionBefore(eq(100L), any(), any(Instant.class)))
                .thenReturn(BigDecimal.ZERO);
        when(ledgerEntryRepository.findEntriesForAccountBetween(eq(100L), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of());

        WalletStatementDto stmt = statementService.generateStatement(
                UserPrincipal.from(user),
                LocalDate.now().withDayOfMonth(1),
                LocalDate.now(),
                StatementPeriod.CURRENT_MONTH
        );

        String html = statementService.generatePrintableHtml(stmt);

        assertThat(html).contains("<!DOCTYPE html>");
        assertThat(html).contains("Dhan<span>Pay</span>");
        assertThat(html).contains("Official Certified Account Statement");
        assertThat(html).contains("RBI AUTHORIZED PPI");
        assertThat(html).contains("WLT-IND-10000001");
        assertThat(html).contains("Faiz Khan");
        assertThat(html).contains("Cryptographic Ledger Verification Seal");
        assertThat(html).contains("SHA-256:");
    }

    @Test
    void generateStatement_throwsWhenStartDateAfterEndDate() {
        when(walletRepository.findByUserId(1L)).thenReturn(Optional.of(wallet));

        LocalDate start = LocalDate.now().plusDays(5);
        LocalDate end = LocalDate.now();

        assertThatThrownBy(() -> statementService.generateStatement(
                UserPrincipal.from(user),
                start,
                end,
                StatementPeriod.CUSTOM
        )).isInstanceOf(BusinessException.class)
                .hasMessageContaining("Start date cannot be after end date");
    }
}
