package com.ecourtier.domain.avis.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReputationResponseDTO {
    private UUID utilisateurId;
    private Double noteMoyenne;
    private Long nombreAvis;
}
