package com.ecourtier.domain.avis.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AvisSummaryDTO {
    private Double noteMoyenne;
    private Long nombreAvis;
    private List<AvisResponseDTO> avis;
}
