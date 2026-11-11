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
        Objects.requireNonNull(transfer, "transfer must not be null");
        Objects.requireNonNull(senderWallet, "senderWallet must not be null");
        Objects.requireNonNull(recipientWallet, "recipientWallet must not be null");
        Objects.requireNonNull(amount, "amount must not be null");

        if (Objects.equals(senderWallet.getId(), recipientWallet.getId())) {
            throw new IllegalArgumentException("Sender and recipient wallets must be distinct");
        }
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Transfer posting amount must be positive");
        }
        if (!Objects.equals(senderWallet.getCurrency(), recipientWallet.getCurrency())) {
            throw new IllegalArgumentException("Cross-currency ledger posting is not supported");
        }

        BigDecimal normalizedAmount = amount.setScale(MONETARY_SCALE, RoundingMode.UNNECESSARY);
        String currency = senderWallet.getCurrency();

        LedgerAccount senderAccount = ledgerAccountRepository.findByWalletId(senderWallet.getId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "LEDGER_ACCOUNT_NOT_FOUND",
                        "Sender ledger account not found for wallet " + senderWallet.getWalletReference()
                ));

        LedgerAccount recipientAccount = ledgerAccountRepository.findByWalletId(recipientWallet.getId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "LEDGER_ACCOUNT_NOT_FOUND",
                        "Recipient ledger account not found for wallet " + recipientWallet.getWalletReference()
                ));

        String ledgerReference = "LTX-" + transfer.getReference();
        LedgerTransaction ledgerTransaction = ledgerTransactionRepository.save(
                new LedgerTransaction(
                        transfer,
                        ledgerReference,
                        LedgerTransactionType.P2P_TRANSFER,
                        LedgerTransactionStatus.POSTED
                )
        );

        List<LedgerEntry> entries = List.of(
                new LedgerEntry(ledgerTransaction, senderAccount, EntryDirection.DEBIT, normalizedAmount, currency),
                new LedgerEntry(ledgerTransaction, recipientAccount, EntryDirection.CREDIT, normalizedAmount, currency)
        );

        assertBalanced(entries);
        ledgerEntryRepository.saveAll(entries);
        return ledgerTransaction;
    }

    /**
     * Records a balanced DEBIT (system holding) / CREDIT (customer wallet) posting for a wallet top-up.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public LedgerTransaction recordTopUp(String topUpReference, Wallet targetWallet, BigDecimal amount) {
        Objects.requireNonNull(topUpReference, "topUpReference must not be null");
        Objects.requireNonNull(targetWallet, "targetWallet must not be null");
        Objects.requireNonNull(amount, "amount must not be null");

        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Top-up posting amount must be positive");
        }

        BigDecimal normalizedAmount = amount.setScale(MONETARY_SCALE, RoundingMode.UNNECESSARY);
        String currency = targetWallet.getCurrency();

        LedgerAccount systemHoldingAccount = ledgerAccountRepository.findByAccountNumber("GL-SYS-HOLDING-INR-0001")
                .orElseThrow(() -> new ResourceNotFoundException(
                        "SYSTEM_HOLDING_NOT_FOUND",
                        "System holding ledger account GL-SYS-HOLDING-INR-0001 not found"
                ));

        LedgerAccount targetAccount = ledgerAccountRepository.findByWalletId(targetWallet.getId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "LEDGER_ACCOUNT_NOT_FOUND",
                        "Ledger account not found for wallet " + targetWallet.getWalletReference()
                ));

        String ledgerReference = "LTX-" + topUpReference;
        LedgerTransaction ledgerTransaction = ledgerTransactionRepository.save(
                new LedgerTransaction(
                        null,
                        ledgerReference,
                        LedgerTransactionType.ADJUSTMENT,
                        LedgerTransactionStatus.POSTED
                )
        );

        List<LedgerEntry> entries = List.of(
                new LedgerEntry(ledgerTransaction, systemHoldingAccount, EntryDirection.DEBIT, normalizedAmount, currency),
                new LedgerEntry(ledgerTransaction, targetAccount, EntryDirection.CREDIT, normalizedAmount, currency)
        );

        assertBalanced(entries);
        ledgerEntryRepository.saveAll(entries);
        return ledgerTransaction;
    }

    @Transactional(readOnly = true)
    public TransferJournalDto getJournalForTransfer(Transfer transfer) {
        LedgerTransaction ltx = ledgerTransactionRepository.findByTransferId(transfer.getId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "LEDGER_TRANSACTION_NOT_FOUND",
                        "No ledger journal found for transfer " + transfer.getReference()
                ));

        List<JournalLineDto> lines = ledgerEntryRepository.findAllWithAccountByTransactionId(ltx.getId()).stream()
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
                ltx.getReference(),
                ltx.getTransactionType(),
                ltx.getStatus(),
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
