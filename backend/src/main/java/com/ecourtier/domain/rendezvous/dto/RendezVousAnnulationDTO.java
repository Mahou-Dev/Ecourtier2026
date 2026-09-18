package com.ecourtier.domain.rendezvous.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RendezVousAnnulationDTO {

    @NotBlank(message = "Le motif d'annulation est obligatoire")
    private String motif;
}
