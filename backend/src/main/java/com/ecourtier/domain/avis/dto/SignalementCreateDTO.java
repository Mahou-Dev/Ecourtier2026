package com.ecourtier.domain.avis.dto;

import com.ecourtier.domain.avis.entity.MotifSignalement;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SignalementCreateDTO {

    @NotNull(message = "Le motif du signalement est obligatoire")
    private MotifSignalement motif;

    private String description;
}
