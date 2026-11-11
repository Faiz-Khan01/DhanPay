package com.dhanpay.ledger;

import com.dhanpay.entity.Wallet;
import com.dhanpay.repository.WalletRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Verifies integrity between materialized wallet balances and the immutable double-entry ledger.
 */
@Service
public class ReconciliationService {

    private static final int MONETARY_SCALE = 4;

    private final WalletRepository walletRepository;
    private final LedgerAccountRepository ledgerAccountRepository;
    private final LedgerEntryRepository ledgerEntryRepository;

    public ReconciliationService(
            WalletRepository walletRepository,
            LedgerAccountRepository ledgerAccountRepository,
            LedgerEntryRepository ledgerEntryRepository) {
        this.walletRepository = walletRepository;
        this.ledgerAccountRepository = ledgerAccountRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
    }

    /**
     * Audits every wallet against its ledger account (SUM(Credits) - SUM(Debits)) and global ledger balance.
     */
    @Transactional(readOnly = true)
    public ReconciliationReportDto reconcileAllWallets() {
        List<Wallet> wallets = walletRepository.findAll();
        List<DiscrepancyItem> discrepancies = new ArrayList<>();

        BigDecimal aggregateWalletFunds = zero();
        BigDecimal aggregateLedgerFunds = zero();

        for (Wallet wallet : wallets) {
            BigDecimal currentBalance = scale(wallet.getBalance());
            aggregateWalletFunds = aggregateWalletFunds.add(currentBalance);

            Optional<LedgerAccount> accountOpt = ledgerAccountRepository.findByWalletId(wallet.getId());
            if (accountOpt.isEmpty()) {
                BigDecimal missingLedgerBalance = zero();
                discrepancies.add(new DiscrepancyItem(
                        wallet.getWalletReference(),
                        currentBalance,
                        missingLedgerBalance,
                        currentBalance.subtract(missingLedgerBalance)
                ));
                continue;
            }

            LedgerAccount account = accountOpt.get();
            BigDecimal totalCredits = scale(
                    ledgerEntryRepository.sumAmountByAccountIdAndDirection(account.getId(), EntryDirection.CREDIT)
            );
            BigDecimal totalDebits = scale(
                    ledgerEntryRepository.sumAmountByAccountIdAndDirection(account.getId(), EntryDirection.DEBIT)
            );
            BigDecimal ledgerDerivedBalance = totalCredits.subtract(totalDebits);
            aggregateLedgerFunds = aggregateLedgerFunds.add(ledgerDerivedBalance);

            BigDecimal delta = currentBalance.subtract(ledgerDerivedBalance);
            if (delta.compareTo(BigDecimal.ZERO) != 0) {
                discrepancies.add(new DiscrepancyItem(
                        wallet.getWalletReference(),
                        currentBalance,
                        ledgerDerivedBalance,
                        delta
                ));
            }
        }

        BigDecimal globalDebits = scale(ledgerEntryRepository.sumAllByDirection(EntryDirection.DEBIT));
        BigDecimal globalCredits = scale(ledgerEntryRepository.sumAllByDirection(EntryDirection.CREDIT));
        boolean ledgerGloballyBalanced = globalDebits.compareTo(globalCredits) == 0;
        boolean systemBalanced = discrepancies.isEmpty() && ledgerGloballyBalanced;

        return new ReconciliationReportDto(
                Instant.now(),
                wallets.size(),
                systemBalanced,
                aggregateWalletFunds,
                aggregateLedgerFunds,
                List.copyOf(discrepancies)
        );
    }

    private static BigDecimal zero() {
        return BigDecimal.ZERO.setScale(MONETARY_SCALE, RoundingMode.UNNECESSARY);
    }

    private static BigDecimal scale(BigDecimal amount) {
        return amount == null
                ? zero()
                : amount.setScale(MONETARY_SCALE, RoundingMode.HALF_EVEN);
    }
}
