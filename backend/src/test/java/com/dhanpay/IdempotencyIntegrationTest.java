package com.dhanpay;

import com.dhanpay.dto.auth.AuthResponse;
import com.dhanpay.dto.auth.RegisterRequest;
import com.dhanpay.dto.transfer.TransferRequestDto;
import com.dhanpay.dto.transfer.TransferResponseDto;
import com.dhanpay.entity.TransferStatus;
import com.dhanpay.entity.Wallet;
import com.dhanpay.exception.ApiErrorResponse;
import com.dhanpay.ledger.EntryDirection;
import com.dhanpay.ledger.LedgerAccount;
import com.dhanpay.ledger.LedgerAccountRepository;
import com.dhanpay.ledger.LedgerEntry;
import com.dhanpay.ledger.LedgerEntryRepository;
import com.dhanpay.ledger.LedgerTransaction;
import com.dhanpay.ledger.LedgerTransactionRepository;
import com.dhanpay.ledger.LedgerTransactionStatus;
import com.dhanpay.ledger.LedgerTransactionType;
import com.dhanpay.repository.WalletRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full-stack HTTP integration tests for database-backed idempotency guarantees.
 */
class IdempotencyIntegrationTest extends BaseIntegrationTest {

    private static final String SYSTEM_HOLDING_ACCOUNT = "GL-SYS-HOLDING-INR-0001";
    private static final String SEEDED_RECIPIENT_WALLET = "WLT-IND-10000003";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private LedgerAccountRepository ledgerAccountRepository;

    @Autowired
    private LedgerTransactionRepository ledgerTransactionRepository;

    @Autowired
    private LedgerEntryRepository ledgerEntryRepository;

    private record FundedTestUser(String token, String walletReference) {
    }

    private FundedTestUser registerAndFundUser(BigDecimal openingBalance) {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        RegisterRequest registerRequest = new RegisterRequest(
                "Idempotency User " + suffix,
                "idem+" + suffix + "@dhanpay.test",
                "StrongPass@9999"
        );

        ResponseEntity<AuthResponse> registerResponse = restTemplate.postForEntity(
                "/api/auth/register",
                registerRequest,
                AuthResponse.class
        );
        assertThat(registerResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        AuthResponse auth = registerResponse.getBody();
        assertThat(auth).isNotNull();

        String walletRef = auth.user().walletReference();
        Wallet wallet = walletRepository.findByWalletReference(walletRef).orElseThrow();
        wallet.credit(openingBalance);
        walletRepository.save(wallet);

        LedgerAccount userLedgerAccount = ledgerAccountRepository.findByWalletId(wallet.getId()).orElseThrow();
        LedgerAccount systemHolding = ledgerAccountRepository.findByAccountNumber(SYSTEM_HOLDING_ACCOUNT).orElseThrow();

        LedgerTransaction openingTx = ledgerTransactionRepository.save(
                new LedgerTransaction(
                        null,
                        "LTX-IDEM-OPEN-" + suffix.toUpperCase(),
                        LedgerTransactionType.SYSTEM_OPENING,
                        LedgerTransactionStatus.POSTED
                )
        );
        ledgerEntryRepository.saveAll(List.of(
                new LedgerEntry(openingTx, systemHolding, EntryDirection.DEBIT, openingBalance, "INR"),
                new LedgerEntry(openingTx, userLedgerAccount, EntryDirection.CREDIT, openingBalance, "INR")
        ));

        return new FundedTestUser(auth.accessToken(), walletRef);
    }

    @Test
    @DisplayName("First transfer returns 201 Created and deducts funds once")
    void firstTransfer_returns201CreatedAndDeductsFunds() {
        FundedTestUser sender = registerAndFundUser(new BigDecimal("1000.0000"));
        String idempotencyKey = "IDEM-CREATE-" + UUID.randomUUID();

        TransferRequestDto request = new TransferRequestDto(
                SEEDED_RECIPIENT_WALLET,
                new BigDecimal("250.0000"),
                "First idempotent transfer"
        );

        ResponseEntity<TransferResponseDto> response = postTransfer(sender.token(), idempotencyKey, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        TransferResponseDto body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.transferReference()).isNotBlank();
        assertThat(body.status()).isEqualTo(TransferStatus.SUCCESS);
        assertThat(body.amount()).isEqualByComparingTo("250.0000");
        assertThat(body.senderRemainingBalance()).isEqualByComparingTo("750.0000");

        Wallet updatedWallet = walletRepository.findByWalletReference(sender.walletReference()).orElseThrow();
        assertThat(updatedWallet.getBalance()).isEqualByComparingTo("750.0000");
    }

    @Test
    @DisplayName("Immediate identical replay with same Idempotency-Key returns identical response without double debit")
    void identicalReplay_returnsIdenticalResponseWithoutDoubleDebit() {
        FundedTestUser sender = registerAndFundUser(new BigDecimal("1000.0000"));
        String idempotencyKey = "IDEM-REPLAY-" + UUID.randomUUID();

        TransferRequestDto request = new TransferRequestDto(
                SEEDED_RECIPIENT_WALLET,
                new BigDecimal("250.0000"),
                "Replayable transfer"
        );

        ResponseEntity<TransferResponseDto> firstResponse = postTransfer(sender.token(), idempotencyKey, request);
        assertThat(firstResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        TransferResponseDto firstBody = firstResponse.getBody();
        assertThat(firstBody).isNotNull();

        ResponseEntity<TransferResponseDto> replayResponse = postTransfer(sender.token(), idempotencyKey, request);
        assertThat(replayResponse.getStatusCode().is2xxSuccessful()).isTrue();
        TransferResponseDto replayBody = replayResponse.getBody();
        assertThat(replayBody).isNotNull();

        assertThat(replayBody.transferReference()).isEqualTo(firstBody.transferReference());
        assertThat(replayBody.amount()).isEqualByComparingTo(firstBody.amount());
        assertThat(replayBody.status()).isEqualTo(firstBody.status());
        assertThat(replayBody.senderRemainingBalance()).isEqualByComparingTo(firstBody.senderRemainingBalance());

        Wallet updatedWallet = walletRepository.findByWalletReference(sender.walletReference()).orElseThrow();
        assertThat(updatedWallet.getBalance())
                .as("Wallet must only be debited once across idempotent replays")
                .isEqualByComparingTo("750.0000");
    }

    @Test
    @DisplayName("Replay with same Idempotency-Key but altered amount returns 409 Conflict")
    void alteredAmountReplay_returns409Conflict() {
        FundedTestUser sender = registerAndFundUser(new BigDecimal("1000.0000"));
        String idempotencyKey = "IDEM-MISMATCH-" + UUID.randomUUID();

        TransferRequestDto originalRequest = new TransferRequestDto(
                SEEDED_RECIPIENT_WALLET,
                new BigDecimal("250.0000"),
                "Original payload"
        );
        ResponseEntity<TransferResponseDto> firstResponse = postTransfer(sender.token(), idempotencyKey, originalRequest);
        assertThat(firstResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        TransferRequestDto alteredRequest = new TransferRequestDto(
                SEEDED_RECIPIENT_WALLET,
                new BigDecimal("350.0000"),
                "Original payload"
        );
        ResponseEntity<ApiErrorResponse> conflictResponse = restTemplate.exchange(
                "/api/transfers",
                HttpMethod.POST,
                new HttpEntity<>(alteredRequest, buildHeaders(sender.token(), idempotencyKey)),
                ApiErrorResponse.class
        );

        assertThat(conflictResponse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        ApiErrorResponse errorBody = conflictResponse.getBody();
        assertThat(errorBody).isNotNull();
        assertThat(errorBody.code()).isEqualTo("IDEMPOTENCY_MISMATCH");

        Wallet updatedWallet = walletRepository.findByWalletReference(sender.walletReference()).orElseThrow();
        assertThat(updatedWallet.getBalance()).isEqualByComparingTo("750.0000");
    }

    private ResponseEntity<TransferResponseDto> postTransfer(
            String token,
            String idempotencyKey,
            TransferRequestDto request) {
        return restTemplate.exchange(
                "/api/transfers",
                HttpMethod.POST,
                new HttpEntity<>(request, buildHeaders(token, idempotencyKey)),
                TransferResponseDto.class
        );
    }

    private HttpHeaders buildHeaders(String token, String idempotencyKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        headers.set("Idempotency-Key", idempotencyKey);
        return headers;
    }
}
