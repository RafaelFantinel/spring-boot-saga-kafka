package com.refarch.flakyexternalservice.web;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChargeControllerTest {

    @Test
    void zeroFailureRate_alwaysApproves() {
        ChargeController controller = new ChargeController(0, 3000);
        for (int i = 0; i < 20; i++) {
            ChargeResponse response = controller.charge(new ChargeRequest("order-" + i, new BigDecimal("10.00")));
            assertThat(response.status()).isEqualTo("APPROVED");
            assertThat(response.transactionId()).isNotBlank();
        }
    }

    @Test
    void hundredPercentFailureRate_alwaysFailsOrStalls() {
        // slow-response-delay-ms=0 so the "slow" branch doesn't actually slow the test down.
        ChargeController controller = new ChargeController(100, 0);
        int failures = 0;
        int slowApprovals = 0;
        for (int i = 0; i < 20; i++) {
            try {
                ChargeResponse response = controller.charge(new ChargeRequest("order-" + i, new BigDecimal("10.00")));
                assertThat(response.status()).isEqualTo("APPROVED_LATE");
                slowApprovals++;
            } catch (ResponseStatusException e) {
                assertThat(e.getStatusCode().value()).isEqualTo(500);
                failures++;
            }
        }
        // Both failure modes (immediate 500 and slow-then-late-200) should show up over 20 tries.
        assertThat(failures + slowApprovals).isEqualTo(20);
        assertThat(failures).isGreaterThan(0);
        assertThat(slowApprovals).isGreaterThan(0);
    }
}
