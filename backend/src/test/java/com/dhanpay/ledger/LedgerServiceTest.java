package com.dhanpay.ledger;

import com.dhanpay.entity.Transfer;
import com.dhanpay.entity.TransferStatus;
import com.dhanpay.entity.User;
import com.dhanpay.entity.Role;
import com.dhanpay.entity.UserStatus;
import com.dhanpay.entity.Wallet;
import com.dhanpay.entity.WalletStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LedgerServiceTest {

    @Mock
    private LedgerAccountRepository ledgerAccountRepository;
    @Mock
    private LedgerTransactionRepository ledgerTransactionRepository;
    @Mock
    private LedgerEntryRepository ledgerEntryRepository;

    @InjectMocks
    private LedgerService ledgerService;

    @Test
    @SuppressWarnings("unchecked")
    void recordTransfer_postsBalancedDebitAndCreditEntries() {
        User senderUser = new User("Aarav", "aarav@example.com", "h", Role.ROLE_CUSTOMER, UserStatus.ACTIVE);
        User recipientUser = new User("Priya", "priya@example.com", "h", Role.ROLE_CUSTOMER, UserStatus.ACTIVE);
        Wallet senderWallet = new Wallet(senderUser, "WLT-IND-10000002", new BigDecimal("25000.0000"), "INR", WalletStatus.ACTIVE);
        Wallet recipientWallet = new Wallet(recipientUser, "WLT-IND-10000003", new BigDecimal("15000.0000"), "INR", WalletStatus.ACTIVE);
        ReflectionTestUtils.setField(senderWallet, "id", 2L);
        ReflectionTestUtils.setField(recipientWallet, "id", 3L);

        LedgerAccount senderAccount = new LedgerAccount(senderWallet, "LA-IND-10000002", LedgerAccountType.CUSTOMER_WALLET, "INR", LedgerAccountStatus.ACTIVE);
        LedgerAccount recipientAccount = new LedgerAccount(recipientWallet, "LA-IND-10000003", LedgerAccountType.CUSTOMER_WALLET, "INR", LedgerAccountStatus.ACTIVE);
        ReflectionTestUtils.setField(senderAccount, "id", 3L);
        ReflectionTestUtils.setField(recipientAccount, "id", 4L);

        Transfer transfer = new Transfer("TXN-1001", senderWallet, recipientWallet, new BigDecimal("500.0000"), "INR", TransferStatus.SUCCESS, "Test");
        ReflectionTestUtils.setField(transfer, "id", 10L);

        when(ledgerAccountRepository.findByWalletId(2L)).thenReturn(Optional.of(senderAccount));
        when(ledgerAccountRepository.findByWalletId(3L)).thenReturn(Optional.of(recipientAccount));
        when(ledgerTransactionRepository.save(any(LedgerTransaction.class))).thenAnswer(inv -> inv.getArgument(0));
        when(ledgerEntryRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        LedgerTransaction tx = ledgerService.recordTransfer(transfer, senderWallet, recipientWallet, new BigDecimal("500.0000"));

        assertThat(tx.getReference()).isEqualTo("LTX-TXN-1001");
        assertThat(tx.getTransactionType()).isEqualTo(LedgerTransactionType.P2P_TRANSFER);

        ArgumentCaptor<List<LedgerEntry>> captor = ArgumentCaptor.forClass(List.class);
        verify(ledgerEntryRepository).saveAll(captor.capture());
        List<LedgerEntry> posted = captor.getValue();
        assertThat(posted).hasSize(2);
        assertThat(posted.get(0).getDirection()).isEqualTo(EntryDirection.DEBIT);
        assertThat(posted.get(0).getLedgerAccount().getId()).isEqualTo(3L);
        assertThat(posted.get(0).getAmount()).isEqualByComparingTo("500.0000");
        assertThat(posted.get(1).getDirection()).isEqualTo(EntryDirection.CREDIT);
        assertThat(posted.get(1).getLedgerAccount().getId()).isEqualTo(4L);
        assertThat(posted.get(1).getAmount()).isEqualByComparingTo("500.0000");
    }

    @Test
    void recordTransfer_rejectsNonPositiveAmount() {
        User u1 = new User("A", "a@example.com", "h", Role.ROLE_CUSTOMER, UserStatus.ACTIVE);
        User u2 = new User("B", "b@example.com", "h", Role.ROLE_CUSTOMER, UserStatus.ACTIVE);
        Wallet w1 = new Wallet(u1, "W1", BigDecimal.TEN, "INR", WalletStatus.ACTIVE);
        Wallet w2 = new Wallet(u2, "W2", BigDecimal.TEN, "INR", WalletStatus.ACTIVE);
        ReflectionTestUtils.setField(w1, "id", 1L);
        ReflectionTestUtils.setField(w2, "id", 2L);
        Transfer t = new Transfer("TXN-0", w1, w2, BigDecimal.ONE, "INR", TransferStatus.INITIATED, null);

        assertThatThrownBy(() -> ledgerService.recordTransfer(t, w1, w2, BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void recordReversal_postsCompensatingDebitRecipientAndCreditSender() {
        User senderUser = new User("Aarav", "aarav@example.com", "h", Role.ROLE_CUSTOMER, UserStatus.ACTIVE);
        User recipientUser = new User("Priya", "priya@example.com", "h", Role.ROLE_CUSTOMER, UserStatus.ACTIVE);
        Wallet senderWallet = new Wallet(senderUser, "WLT-IND-10000002", new BigDecimal("24500.0000"), "INR", WalletStatus.ACTIVE);
        Wallet recipientWallet = new Wallet(recipientUser, "WLT-IND-10000003", new BigDecimal("15500.0000"), "INR", WalletStatus.ACTIVE);
        ReflectionTestUtils.setField(senderWallet, "id", 2L);
        ReflectionTestUtils.setField(recipientWallet, "id", 3L);

        LedgerAccount senderAccount = new LedgerAccount(senderWallet, "LA-IND-10000002", LedgerAccountType.CUSTOMER_WALLET, "INR", LedgerAccountStatus.ACTIVE);
        LedgerAccount recipientAccount = new LedgerAccount(recipientWallet, "LA-IND-10000003", LedgerAccountType.CUSTOMER_WALLET, "INR", LedgerAccountStatus.ACTIVE);
        ReflectionTestUtils.setField(senderAccount, "id", 3L);
        ReflectionTestUtils.setField(recipientAccount, "id", 4L);

        Transfer transfer = new Transfer("TXN-2001", senderWallet, recipientWallet, new BigDecimal("500.0000"), "INR", TransferStatus.REVERSED, "Refund");
        ReflectionTestUtils.setField(transfer, "id", 20L);

        when(ledgerAccountRepository.findByWalletId(3L)).thenReturn(Optional.of(recipientAccount));
        when(ledgerAccountRepository.findByWalletId(2L)).thenReturn(Optional.of(senderAccount));
        when(ledgerTransactionRepository.save(any(LedgerTransaction.class))).thenAnswer(inv -> inv.getArgument(0));
        when(ledgerEntryRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        LedgerTransaction reversalTx = ledgerService.recordReversal(transfer, senderWallet, recipientWallet, new BigDecimal("500.0000"));

        assertThat(reversalTx.getReference()).isEqualTo("LTX-REV-TXN-2001");
        assertThat(reversalTx.getTransactionType()).isEqualTo(LedgerTransactionType.REVERSAL);

        ArgumentCaptor<List<LedgerEntry>> captor = ArgumentCaptor.forClass(List.class);
        verify(ledgerEntryRepository).saveAll(captor.capture());
        List<LedgerEntry> posted = captor.getValue();
        assertThat(posted).hasSize(2);
        assertThat(posted.get(0).getDirection()).isEqualTo(EntryDirection.DEBIT);
        assertThat(posted.get(0).getLedgerAccount().getId()).isEqualTo(4L);
        assertThat(posted.get(1).getDirection()).isEqualTo(EntryDirection.CREDIT);
        assertThat(posted.get(1).getLedgerAccount().getId()).isEqualTo(3L);
    }
}
