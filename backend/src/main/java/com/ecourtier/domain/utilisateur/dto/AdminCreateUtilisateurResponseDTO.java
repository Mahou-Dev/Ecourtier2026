package com.ecourtier.domain.utilisateur.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminCreateUtilisateurResponseDTO {

    private UtilisateurResponseDTO utilisateur;

    @Schema(description = "Mot de passe temporaire en clair, retourné uniquement à la création du compte")
    private String motDePasseTemporaire;
}
