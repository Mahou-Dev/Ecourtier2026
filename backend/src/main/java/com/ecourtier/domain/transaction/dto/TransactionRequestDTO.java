package com.ecourtier.domain.transaction.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransactionRequestDTO {

    /**
     * Montant éventuellement transmis par le client.
     * Note (§4) : les montants sont calculés exclusivement côté serveur à partir de l'annonce.
     * Tout montant fourni dans la requête est ignoré pour garantir l'intégrité du séquestre.
     */
    private BigDecimal montantCaution;
}
