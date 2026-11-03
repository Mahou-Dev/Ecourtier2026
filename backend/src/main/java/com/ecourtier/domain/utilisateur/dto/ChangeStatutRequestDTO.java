package com.ecourtier.domain.utilisateur.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChangeStatutRequestDTO {

    @NotNull(message = "Le statut actif (true/false) est obligatoire")
    private Boolean actif;
}
