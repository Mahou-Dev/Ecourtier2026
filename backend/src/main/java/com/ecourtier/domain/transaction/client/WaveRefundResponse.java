package com.ecourtier.domain.transaction.client;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WaveRefundResponse {
    private String refundReference;
    private String originalWaveReference;
    private BigDecimal refundedAmount;
    private String status;
}
