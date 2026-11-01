package com.ecourtier.domain.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ActiviteCommercialeStatsDTO {
    private Map<String, Long> nombreRdvParStatut;
    private Double tauxConversionRdvToTransaction;
    private Double tauxConversionTransactionToBail;
    private BigDecimal montantTotalSequestreBloque;
    private BigDecimal montantTotalLibere;
}
