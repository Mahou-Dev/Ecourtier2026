package com.ecourtier.domain.rendezvous.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RendezVousRequestDTO {

    @NotNull(message = "La date et l'heure du rendez-vous sont obligatoires")
    @Future(message = "La date du rendez-vous doit être dans le futur")
    private LocalDateTime dateHeure;
}
