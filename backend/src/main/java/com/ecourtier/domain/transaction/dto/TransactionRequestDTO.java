package com.ecourtier.domain.transaction.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
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

    @NotNull(message = "Le montant de la caution est obligatoire")
    @DecimalMin(value = "1000.00", message = "Le montant de la caution doit être au moins égal à 1000 FCFA")
    private BigDecimal montantCaution;
}
