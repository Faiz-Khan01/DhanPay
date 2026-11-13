package com.dhanpay.ledger;

import com.dhanpay.dto.transfer.JournalLineDto;
import com.dhanpay.dto.transfer.TransferJournalDto;
import com.dhanpay.entity.Transfer;
import com.dhanpay.entity.Wallet;
import com.dhanpay.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;

/**
 * Append-only double-entry general ledger posting service.
 */
@Service
public class LedgerService {

    private static final int MONETARY_SCALE = 4;

    private final LedgerAccountRepository ledgerAccountRepository;
    private final LedgerTransactionRepository ledgerTransactionRepository;
    private final LedgerEntryRepository ledgerEntryRepository;

    public LedgerService(
            LedgerAccountRepository ledgerAccountRepository,
            LedgerTransactionRepository ledgerTransactionRepository,
            LedgerEntryRepository ledgerEntryRepository) {
        this.ledgerAccountRepository = ledgerAccountRepository;
        this.ledgerTransactionRepository = ledgerTransactionRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
    }

    /**
     * Records a balanced DEBIT/CREDIT posting pair for a transfer inside the caller's active transaction.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public LedgerTransaction recordTransfer(
            Transfer transfer,
            Wallet senderWallet,
            Wallet recipientWallet,
            BigDecimal amount) {
        return postTransferPair(
                transfer,
                senderWallet,
                recipientWallet,
                amount,
                "LTX-" + transfer.getReference(),
                LedgerTransactionType.P2P_TRANSFER
        );
    }

    /**
     * Records a balanced compensating DEBIT (original recipient) / CREDIT (original sender)
     * entry pair when an admin reverses a settled P2P transfer.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public LedgerTransaction recordReversal(
            Transfer transfer,
            Wallet originalSenderWallet,
            Wallet originalRecipientWallet,
            BigDecimal amount) {
        return postTransferPair(
                transfer,
                originalRecipientWallet,
                originalSenderWallet,
                amount,
                "LTX-REV-" + transfer.getReference(),
                LedgerTransactionType.REVERSAL
        );
    }

    private LedgerTransaction postTransferPair(
            Transfer transfer,
            Wallet debitWallet,
            Wallet creditWallet,
            BigDecimal amount,
            String ledgerReference,
            LedgerTransactionType txType) {
        Objects.requireNonNull(transfer, "transfer must not be null");
        Objects.requireNonNull(debitWallet, "debitWallet must not be null");
        Objects.requireNonNull(creditWallet, "creditWallet must not be null");
        Objects.requireNonNull(amount, "amount must not be null");

        if (Objects.equals(debitWallet.getId(), creditWallet.getId())) {
            throw new IllegalArgumentException("Debit and credit wallets must be distinct");
        }
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Transfer posting amount must be positive");
        }
        if (!Objects.equals(debitWallet.getCurrency(), creditWallet.getCurrency())) {
            throw new IllegalArgumentException("Cross-currency ledger posting is not supported");
        }

        BigDecimal normalizedAmount = amount.setScale(MONETARY_SCALE, RoundingMode.UNNECESSARY);
        String currency = debitWallet.getCurrency();

        LedgerAccount debitAccount = requireWalletLedgerAccount(debitWallet, "Debit");
        LedgerAccount creditAccount = requireWalletLedgerAccount(creditWallet, "Credit");

        LedgerTransaction ledgerTransaction = ledgerTransactionRepository.save(
                new LedgerTransaction(transfer, ledgerReference, txType, LedgerTransactionStatus.POSTED)
        );

        List<LedgerEntry> entries = List.of(
                new LedgerEntry(ledgerTransaction, debitAccount, EntryDirection.DEBIT, normalizedAmount, currency),
                new LedgerEntry(ledgerTransaction, creditAccount, EntryDirection.CREDIT, normalizedAmount, currency)
        );

        assertBalanced(entries);
        ledgerEntryRepository.saveAll(entries);
        return ledgerTransaction;
    }

    private LedgerAccount requireWalletLedgerAccount(Wallet wallet, String roleLabel) {
        return ledgerAccountRepository.findByWalletId(wallet.getId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "LEDGER_ACCOUNT_NOT_FOUND",
                        roleLabel + " ledger account not found for wallet " + wallet.getWalletReference()
                ));
    }

    /**
     * Records a balanced DEBIT (system holding) / CREDIT (customer wallet) posting for a wallet top-up.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public LedgerTransaction recordTopUp(String topUpReference, Wallet targetWallet, BigDecimal amount) {
        Objects.requireNonNull(topUpReference, "topUpReference must not be null");
        Objects.requireNonNull(targetWallet, "targetWallet must not be null");

        LedgerAccount systemHoldingAccount = ledgerAccountRepository.findByAccountNumber("GL-SYS-HOLDING-INR-0001")
                .orElseThrow(() -> new ResourceNotFoundException(
                        "SYSTEM_HOLDING_NOT_FOUND",
                        "System holding ledger account GL-SYS-HOLDING-INR-0001 not found"
                ));
        LedgerAccount targetAccount = requireWalletLedgerAccount(targetWallet, "Target");

        return postSystemAccountPair(
                "LTX-" + topUpReference,
                LedgerTransactionType.ADJUSTMENT,
                systemHoldingAccount,
                targetAccount,
                amount,
                targetWallet.getCurrency()
        );
    }

    /**
     * Records DEBIT (buyer wallet) / CREDIT (GL escrow account) when locking funds in escrow.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public LedgerTransaction recordEscrowHold(
            String escrowReference,
            Wallet buyerWallet,
            LedgerAccount escrowAccount,
            BigDecimal amount) {
        Objects.requireNonNull(buyerWallet, "buyerWallet must not be null");
        LedgerAccount buyerAccount = requireWalletLedgerAccount(buyerWallet, "Buyer");
        return postSystemAccountPair(
                "LTX-ESC-HOLD-" + escrowReference,
                LedgerTransactionType.ADJUSTMENT,
                buyerAccount,
                escrowAccount,
                amount,
                buyerWallet.getCurrency()
        );
    }

    /**
     * Records DEBIT (GL escrow account) / CREDIT (seller wallet) when releasing escrow funds.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public LedgerTransaction recordEscrowRelease(
            String escrowReference,
            LedgerAccount escrowAccount,
            Wallet sellerWallet,
            BigDecimal amount) {
        Objects.requireNonNull(sellerWallet, "sellerWallet must not be null");
        LedgerAccount sellerAccount = requireWalletLedgerAccount(sellerWallet, "Seller");
        return postSystemAccountPair(
                "LTX-ESC-REL-" + escrowReference,
                LedgerTransactionType.ADJUSTMENT,
                escrowAccount,
                sellerAccount,
                amount,
                sellerWallet.getCurrency()
        );
    }

    /**
     * Records DEBIT (GL escrow account) / CREDIT (buyer wallet) when refunding an escrow hold.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public LedgerTransaction recordEscrowRefund(
            String escrowReference,
            LedgerAccount escrowAccount,
            Wallet buyerWallet,
            BigDecimal amount) {
        Objects.requireNonNull(buyerWallet, "buyerWallet must not be null");
        LedgerAccount buyerAccount = requireWalletLedgerAccount(buyerWallet, "Buyer");
        return postSystemAccountPair(
                "LTX-ESC-REF-" + escrowReference,
                LedgerTransactionType.ADJUSTMENT,
                escrowAccount,
                buyerAccount,
                amount,
                buyerWallet.getCurrency()
        );
    }

    private LedgerTransaction postSystemAccountPair(
            String ledgerReference,
            LedgerTransactionType txType,
            LedgerAccount debitAccount,
            LedgerAccount creditAccount,
            BigDecimal amount,
            String currency) {
        Objects.requireNonNull(debitAccount, "debitAccount must not be null");
        Objects.requireNonNull(creditAccount, "creditAccount must not be null");
        Objects.requireNonNull(amount, "amount must not be null");
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Posting amount must be positive");
        }

        BigDecimal normalizedAmount = amount.setScale(MONETARY_SCALE, RoundingMode.UNNECESSARY);
        LedgerTransaction ledgerTransaction = ledgerTransactionRepository.save(
                new LedgerTransaction(null, ledgerReference, txType, LedgerTransactionStatus.POSTED)
        );

        List<LedgerEntry> entries = List.of(
                new LedgerEntry(ledgerTransaction, debitAccount, EntryDirection.DEBIT, normalizedAmount, currency),
                new LedgerEntry(ledgerTransaction, creditAccount, EntryDirection.CREDIT, normalizedAmount, currency)
        );

        assertBalanced(entries);
        ledgerEntryRepository.saveAll(entries);
        return ledgerTransaction;
    }

    @Transactional(readOnly = true)
    public TransferJournalDto getJournalForTransfer(Transfer transfer) {
        List<LedgerTransaction> transactions = ledgerTransactionRepository.findAllByTransferIdOrderByIdAsc(transfer.getId());
        if (transactions.isEmpty()) {
            throw new ResourceNotFoundException(
                    "LEDGER_TRANSACTION_NOT_FOUND",
                    "No ledger journal found for transfer " + transfer.getReference()
            );
        }

        LedgerTransaction latest = transactions.get(transactions.size() - 1);
        List<JournalLineDto> lines = transactions.stream()
                .flatMap(ltx -> ledgerEntryRepository.findAllWithAccountByTransactionId(ltx.getId()).stream())
                .map(entry -> new JournalLineDto(
                        entry.getLedgerAccount().getAccountNumber(),
                        entry.getLedgerAccount().getAccountType(),
                        entry.getLedgerAccount().getWallet() != null
                                ? entry.getLedgerAccount().getWallet().getWalletReference()
                                : null,
                        entry.getDirection(),
                        entry.getAmount(),
                        entry.getCurrency(),
                        entry.getCreatedAt()
                ))
                .toList();

        return new TransferJournalDto(
                transfer.getReference(),
                latest.getReference(),
                latest.getTransactionType(),
                latest.getStatus(),
                transfer.getAmount(),
                transfer.getCurrency(),
                transfer.getSenderWallet().getWalletReference(),
                transfer.getRecipientWallet().getWalletReference(),
                transfer.getDescription(),
                transfer.getCreatedAt(),
                lines
        );
    }

    private static void assertBalanced(List<LedgerEntry> entries) {
        BigDecimal totalDebits = BigDecimal.ZERO.setScale(MONETARY_SCALE, RoundingMode.UNNECESSARY);
        BigDecimal totalCredits = BigDecimal.ZERO.setScale(MONETARY_SCALE, RoundingMode.UNNECESSARY);

        for (LedgerEntry entry : entries) {
            if (entry.getDirection() == EntryDirection.DEBIT) {
                totalDebits = totalDebits.add(entry.getAmount());
            } else if (entry.getDirection() == EntryDirection.CREDIT) {
                totalCredits = totalCredits.add(entry.getAmount());
            }
        }

        if (totalDebits.compareTo(BigDecimal.ZERO) <= 0 || totalDebits.compareTo(totalCredits) != 0) {
            throw new IllegalStateException(
                    "Unbalanced double-entry ledger transaction: debits=" + totalDebits + ", credits=" + totalCredits
            );
        }
    }
}
