package com.ecourtier.domain.transaction.client;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WaveCheckoutResponse {
    private UUID transactionId;
    private BigDecimal amount;
    private String waveReference;
    private String checkoutUrl;
    private String status;
}
