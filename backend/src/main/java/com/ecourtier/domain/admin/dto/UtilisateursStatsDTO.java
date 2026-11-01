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
public class UtilisateursStatsDTO {
    private Map<String, Long> totalParRole;
    private List<SerieTemporelleDTO> nouvellesInscriptions;
}
