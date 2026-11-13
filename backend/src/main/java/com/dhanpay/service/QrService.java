package com.dhanpay.service;

import com.dhanpay.dto.qr.GenerateQrRequestDto;
import com.dhanpay.dto.qr.ParsedQrPayloadDto;
import com.dhanpay.dto.qr.QrCodeResponseDto;
import com.dhanpay.entity.Wallet;
import com.dhanpay.entity.WalletStatus;
import com.dhanpay.exception.BusinessException;
import com.dhanpay.exception.ResourceNotFoundException;
import com.dhanpay.qr.QrCodeGenerator;
import com.dhanpay.repository.WalletRepository;
import com.dhanpay.security.UserPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Generates and parses dynamic UPI / DhanPay QR codes for instant P2P settlement.
 */
@Service
public class QrService {

    private static final String BRAND_DARK = "#05334D";
    private static final String BRAND_LIGHT = "#FFFFFF";
    private static final Pattern WALLET_REF_PATTERN = Pattern.compile("WLT-[A-Z0-9-]+", Pattern.CASE_INSENSITIVE);

    private final WalletRepository walletRepository;

    public QrService(WalletRepository walletRepository) {
        this.walletRepository = walletRepository;
    }

    @Transactional(readOnly = true)
    public QrCodeResponseDto getMyStaticQr(UserPrincipal principal) {
        Wallet wallet = requireCallerWallet(principal);
        return buildQrResponse(wallet, null, null);
    }

    @Transactional(readOnly = true)
    public QrCodeResponseDto generateDynamicQr(UserPrincipal principal, GenerateQrRequestDto request) {
        Wallet wallet = requireCallerWallet(principal);
        BigDecimal amount = (request != null && request.amount() != null)
                ? request.amount().setScale(4, RoundingMode.HALF_EVEN)
                : null;
        String note = (request != null && request.note() != null && !request.note().isBlank())
                ? request.note().trim()
                : null;

        return buildQrResponse(wallet, amount, note);
    }

    @Transactional(readOnly = true)
    public ParsedQrPayloadDto parseAndValidateQr(UserPrincipal principal, String rawPayload) {
        if (rawPayload == null || rawPayload.isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_QR_PAYLOAD", "Scanned QR content cannot be empty");
        }

        Wallet callerWallet = requireCallerWallet(principal);
        String trimmed = rawPayload.trim();

        String targetWalletRef = null;
        BigDecimal amount = null;
        String description = null;
        String currency = "INR";

        if (trimmed.startsWith("upi://pay") || trimmed.startsWith("dhanpay://pay")) {
            Map<String, String> params = parseQueryParams(trimmed);
            String pa = params.get("pa");
            String walletParam = params.get("wallet");
            String vpa = (pa != null) ? pa : walletParam;

            if (vpa != null) {
                targetWalletRef = vpa.contains("@") ? vpa.substring(0, vpa.indexOf('@')) : vpa;
            }

            String am = params.get("am");
            String amountParam = params.get("amount");
            String rawAm = (am != null) ? am : amountParam;
            if (rawAm != null && !rawAm.isBlank()) {
                try {
                    amount = new BigDecimal(rawAm).setScale(4, RoundingMode.HALF_EVEN);
                } catch (NumberFormatException ignored) {
                }
            }

            String tn = params.get("tn");
            String noteParam = params.get("note");
            description = (tn != null) ? tn : noteParam;
            if (params.containsKey("cu")) {
                currency = params.get("cu");
            }
        } else {
            Matcher matcher = WALLET_REF_PATTERN.matcher(trimmed);
            if (matcher.find()) {
                targetWalletRef = matcher.group();
            } else {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "UNRECOGNIZED_QR_FORMAT", "Payload does not contain a valid UPI or DhanPay reference");
            }
        }

        if (targetWalletRef == null || targetWalletRef.isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "TARGET_WALLET_NOT_FOUND", "Could not extract wallet reference from QR payload");
        }

        String normalizedTarget = targetWalletRef.trim().toUpperCase();
        Wallet targetWallet = walletRepository.findByWalletReference(normalizedTarget)
                .orElseThrow(() -> new ResourceNotFoundException("WALLET_NOT_FOUND", "No DhanPay wallet matches reference: " + normalizedTarget));

        boolean isSelf = Objects.equals(callerWallet.getId(), targetWallet.getId());
        boolean isActive = targetWallet.getStatus() == WalletStatus.ACTIVE;

        return new ParsedQrPayloadDto(
                trimmed,
                targetWallet.getWalletReference(),
                targetWallet.getUser().getFullName(),
                amount,
                currency,
                description,
                isActive,
                isSelf
        );
    }

    private QrCodeResponseDto buildQrResponse(Wallet wallet, BigDecimal amount, String note) {
        String walletRef = wallet.getWalletReference();
        String name = wallet.getUser().getFullName();
        String encodedName = urlEncode(name);

        StringBuilder upiBuilder = new StringBuilder("upi://pay?")
                .append("pa=").append(walletRef).append("@dhanpay")
                .append("&pn=").append(encodedName)
                .append("&cu=INR");

        StringBuilder dhanpayBuilder = new StringBuilder("dhanpay://pay?")
                .append("wallet=").append(walletRef)
                .append("&name=").append(encodedName)
                .append("&cu=INR");

        if (amount != null && amount.compareTo(BigDecimal.ZERO) > 0) {
            String formattedAmount = amount.setScale(2, RoundingMode.HALF_EVEN).toPlainString();
            upiBuilder.append("&am=").append(formattedAmount);
            dhanpayBuilder.append("&amount=").append(formattedAmount);
        }

        if (note != null && !note.isBlank()) {
            String encodedNote = urlEncode(note);
            upiBuilder.append("&tn=").append(encodedNote);
            dhanpayBuilder.append("&note=").append(encodedNote);
        }

        String upiUri = upiBuilder.toString();
        String dhanpayUri = dhanpayBuilder.toString();

        boolean[][] qrMatrix = QrCodeGenerator.encode(upiUri);
        String svg = QrCodeGenerator.toSvg(qrMatrix, 4, BRAND_DARK, BRAND_LIGHT);

        return new QrCodeResponseDto(
                walletRef,
                name,
                upiUri,
                dhanpayUri,
                amount,
                wallet.getCurrency(),
                note,
                svg
        );
    }

    private Wallet requireCallerWallet(UserPrincipal principal) {
        return walletRepository.findByUserId(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("WALLET_NOT_FOUND", "Wallet not found for authenticated user"));
    }

    private static Map<String, String> parseQueryParams(String uriStr) {
        Map<String, String> params = new HashMap<>();
        try {
            int qIdx = uriStr.indexOf('?');
            if (qIdx >= 0 && qIdx < uriStr.length() - 1) {
                String query = uriStr.substring(qIdx + 1);
                String[] pairs = query.split("&");
                for (String pair : pairs) {
                    int eqIdx = pair.indexOf('=');
                    if (eqIdx > 0) {
                        String key = URLDecoder.decode(pair.substring(0, eqIdx), StandardCharsets.UTF_8).trim();
                        String val = URLDecoder.decode(pair.substring(eqIdx + 1), StandardCharsets.UTF_8).trim();
                        params.put(key, val);
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return params;
    }

    private static String urlEncode(String val) {
        return URLEncoder.encode(val, StandardCharsets.UTF_8);
    }
}
