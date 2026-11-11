package com.dhanpay.idempotency;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class IdempotencyServiceTest {

    private final IdempotencyService service = new IdempotencyService(null);

    @Test
    void computeFingerprint_isDeterministicAndHex64() {
        String fp1 = service.computeFingerprint("WLT-IND-10000003", new BigDecimal("500.0000"), "Dinner");
        String fp2 = service.computeFingerprint("WLT-IND-10000003", new BigDecimal("500.00"), "Dinner");

        assertThat(fp1).isEqualTo(fp2);
        assertThat(fp1).hasSize(64);
    }

    @Test
    void computeFingerprint_differsWhenAmountOrRecipientChanges() {
        String fp1 = service.computeFingerprint("WLT-IND-10000003", new BigDecimal("500.0000"), "Dinner");
        String fp2 = service.computeFingerprint("WLT-IND-10000004", new BigDecimal("500.0000"), "Dinner");
        String fp3 = service.computeFingerprint("WLT-IND-10000003", new BigDecimal("501.0000"), "Dinner");

        assertThat(fp1).isNotEqualTo(fp2);
        assertThat(fp1).isNotEqualTo(fp3);
    }
}
