package com.ecourtier.domain.transaction.dto;

import com.ecourtier.domain.transaction.entity.StatutSequestre;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PayTechWebhookResponseDTO {

    private UUID transactionId;
    private String typeEvent;
    private String refCommand;
    private String statutPaiement;
    private StatutSequestre statutSequestre;
    private boolean latePayment;
    private boolean idempotentReplay;
    private String message;
}
