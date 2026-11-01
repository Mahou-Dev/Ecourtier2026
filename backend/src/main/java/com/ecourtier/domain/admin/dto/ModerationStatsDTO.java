package com.ecourtier.domain.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ModerationStatsDTO {
    private Map<String, Long> signalementsParStatut;
    private Map<String, Long> signalementsParMotif;
    private Double noteMoyenneGlobale;
}
