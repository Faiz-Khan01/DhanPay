package com.dhanpay.qr;

import com.dhanpay.dto.qr.GenerateQrRequestDto;
import com.dhanpay.dto.qr.ParsedQrPayloadDto;
import com.dhanpay.dto.qr.QrCodeResponseDto;
import com.dhanpay.entity.Role;
import com.dhanpay.entity.User;
import com.dhanpay.entity.UserStatus;
import com.dhanpay.entity.Wallet;
import com.dhanpay.entity.WalletStatus;
import com.dhanpay.exception.BusinessException;
import com.dhanpay.repository.WalletRepository;
import com.dhanpay.security.UserPrincipal;
import com.dhanpay.service.QrService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QrServiceTest {

    @Mock
    private WalletRepository walletRepository;

    @InjectMocks
    private QrService qrService;

    private static User user(long id, String name, String email) {
        User u = new User(name, email, "h", Role.ROLE_CUSTOMER, UserStatus.ACTIVE);
        ReflectionTestUtils.setField(u, "id", id);
        return u;
    }

    private static Wallet wallet(long id, User owner, String ref) {
        Wallet w = new Wallet(owner, ref, new BigDecimal("10000.0000"), "INR", WalletStatus.ACTIVE);
        ReflectionTestUtils.setField(w, "id", id);
        return w;
    }

    @Test
    void getMyStaticQr_generatesValidUpiAndSvg() {
        User u = user(1L, "Aarav Sharma", "aarav@example.com");
        Wallet w = wallet(2L, u, "WLT-IND-10000002");
        when(walletRepository.findByUserId(1L)).thenReturn(Optional.of(w));

        QrCodeResponseDto dto = qrService.getMyStaticQr(UserPrincipal.from(u));

        assertThat(dto.walletReference()).isEqualTo("WLT-IND-10000002");
        assertThat(dto.upiUri()).contains("pa=WLT-IND-10000002@dhanpay");
        assertThat(dto.qrSvg()).startsWith("<svg");
        assertThat(dto.qrSvg()).contains("#05334D");
    }

    @Test
    void generateDynamicQr_embedsAmountAndNote() {
        User u = user(1L, "Aarav Sharma", "aarav@example.com");
        Wallet w = wallet(2L, u, "WLT-IND-10000002");
        when(walletRepository.findByUserId(1L)).thenReturn(Optional.of(w));

        QrCodeResponseDto dto = qrService.generateDynamicQr(
                UserPrincipal.from(u),
                new GenerateQrRequestDto(new BigDecimal("750.50"), "Dinner party")
        );

        assertThat(dto.upiUri()).contains("am=750.50");
        assertThat(dto.upiUri()).contains("tn=Dinner+party");
        assertThat(dto.amount()).isEqualByComparingTo("750.5000");
        assertThat(dto.qrSvg()).isNotEmpty();
    }

    @Test
    void parseAndValidateQr_parsesUpiUriAndIdentifiesTargetWallet() {
        User caller = user(1L, "Aarav Sharma", "aarav@example.com");
        Wallet callerWallet = wallet(2L, caller, "WLT-IND-10000002");

        User target = user(3L, "Priya Patel", "priya@example.com");
        Wallet targetWallet = wallet(4L, target, "WLT-IND-10000003");

        when(walletRepository.findByUserId(1L)).thenReturn(Optional.of(callerWallet));
        when(walletRepository.findByWalletReference("WLT-IND-10000003")).thenReturn(Optional.of(targetWallet));

        String qrUri = "upi://pay?pa=WLT-IND-10000003@dhanpay&pn=Priya%20Patel&am=500.00&tn=Freelance";
        ParsedQrPayloadDto parsed = qrService.parseAndValidateQr(UserPrincipal.from(caller), qrUri);

        assertThat(parsed.targetWalletReference()).isEqualTo("WLT-IND-10000003");
        assertThat(parsed.targetFullName()).isEqualTo("Priya Patel");
        assertThat(parsed.amount()).isEqualByComparingTo("500.0000");
        assertThat(parsed.description()).isEqualTo("Freelance");
        assertThat(parsed.targetActive()).isTrue();
        assertThat(parsed.self()).isFalse();
    }

    @Test
    void parseAndValidateQr_flagsSelfTransfer() {
        User caller = user(1L, "Aarav Sharma", "aarav@example.com");
        Wallet callerWallet = wallet(2L, caller, "WLT-IND-10000002");

        when(walletRepository.findByUserId(1L)).thenReturn(Optional.of(callerWallet));
        when(walletRepository.findByWalletReference("WLT-IND-10000002")).thenReturn(Optional.of(callerWallet));

        ParsedQrPayloadDto parsed = qrService.parseAndValidateQr(UserPrincipal.from(caller), "WLT-IND-10000002");

        assertThat(parsed.targetWalletReference()).isEqualTo("WLT-IND-10000002");
        assertThat(parsed.self()).isTrue();
    }

    @Test
    void parseAndValidateQr_rejectsEmptyPayload() {
        User caller = user(1L, "Aarav Sharma", "aarav@example.com");
        assertThatThrownBy(() -> qrService.parseAndValidateQr(UserPrincipal.from(caller), "   "))
                .isInstanceOf(BusinessException.class);
    }
}
