package com.ecourtier.domain.avis.dto;

import com.ecourtier.domain.avis.entity.StatutSignalement;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TraiterSignalementDTO {

    @NotNull(message = "Le statut de traitement est obligatoire")
    private StatutSignalement statut;

    private boolean suspendreAnnonce;
}
