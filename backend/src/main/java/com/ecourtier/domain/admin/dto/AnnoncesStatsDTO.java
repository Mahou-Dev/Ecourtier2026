package com.ecourtier.domain.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnnoncesStatsDTO {
    private Map<String, Long> totalParStatutPublication;
    private Map<String, Long> totalParStatutVerification;
    private List<RepartitionGeographiqueDTO> repartitionParVilleQuartier;
    private Map<String, Double> prixMoyenParTypeBien;
    private Double tempsMoyenDecisionMinutes;
}
