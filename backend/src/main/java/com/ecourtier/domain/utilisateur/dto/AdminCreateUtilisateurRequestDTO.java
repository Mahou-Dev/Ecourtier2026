package com.ecourtier.domain.utilisateur.dto;

import com.ecourtier.domain.utilisateur.entity.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminCreateUtilisateurRequestDTO {

    @NotBlank(message = "Le nom est obligatoire")
    private String nom;

    @NotBlank(message = "Le prénom est obligatoire")
    private String prenom;

    @NotBlank(message = "L'adresse email est obligatoire")
    @Email(message = "L'adresse email doit être valide")
    private String email;

    @NotBlank(message = "Le numéro de téléphone est obligatoire")
    private String telephone;

    @NotNull(message = "Le rôle est obligatoire (ADMIN, AGENT, PROPRIETAIRE ou LOCATAIRE)")
    private Role role;

    @Schema(description = "Mot de passe temporaire optionnel (au moins 8 caractères, 1 majuscule, 1 chiffre). Généré automatiquement s'il est omis.")
    private String motDePasseTemporaire;
}
