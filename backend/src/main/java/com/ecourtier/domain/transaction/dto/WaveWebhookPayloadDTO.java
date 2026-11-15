package com.ecourtier.domain.transaction.dto;

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
public class WaveWebhookPayloadDTO {
    private String eventId;
    private String type;
    private String status;
    private UUID transactionId;
    private String waveReference;
    private BigDecimal amount;
    private String currency;
    private String failureReason;
}
