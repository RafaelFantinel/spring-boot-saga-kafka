package com.refarch.inventoryservice.service;

import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InventoryReservationServiceTest {

    private final InventoryTransactionalOps transactionalOps = mock(InventoryTransactionalOps.class);
    private final InventoryReservationService service = new InventoryReservationService(transactionalOps);

    @Test
    void reserve_retriesOnOptimisticLockConflict_thenSucceeds() {
        // First two attempts lose the optimistic-lock race, third succeeds -
        // this is exactly what happens when two orders race to reserve the same SKU.
        when(transactionalOps.reserveOnce("SKU-1", 2))
                .thenThrow(new OptimisticLockingFailureException("conflict"))
                .thenThrow(new OptimisticLockingFailureException("conflict"))
                .thenReturn(InventoryReservationService.ReservationOutcome.RESERVED);

        InventoryReservationService.ReservationOutcome outcome = service.reserve("SKU-1", 2);

        assertThat(outcome).isEqualTo(InventoryReservationService.ReservationOutcome.RESERVED);
        verify(transactionalOps, times(3)).reserveOnce("SKU-1", 2);
    }

    @Test
    void reserve_insufficientStock_doesNotRetry_andReturnsOutcome() {
        when(transactionalOps.reserveOnce(anyString(), anyInt()))
                .thenReturn(InventoryReservationService.ReservationOutcome.INSUFFICIENT_STOCK);

        InventoryReservationService.ReservationOutcome outcome = service.reserve("SKU-LOW-STOCK", 999);

        assertThat(outcome).isEqualTo(InventoryReservationService.ReservationOutcome.INSUFFICIENT_STOCK);
        verify(transactionalOps, times(1)).reserveOnce("SKU-LOW-STOCK", 999);
    }

    @Test
    void reserve_exhaustsRetries_throwsAfterMaxAttempts() {
        when(transactionalOps.reserveOnce(anyString(), anyInt()))
                .thenThrow(new OptimisticLockingFailureException("always conflicts"));

        assertThatThrownBy(() -> service.reserve("SKU-1", 1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Exhausted retries");

        verify(transactionalOps, times(5)).reserveOnce("SKU-1", 1);
    }
}
