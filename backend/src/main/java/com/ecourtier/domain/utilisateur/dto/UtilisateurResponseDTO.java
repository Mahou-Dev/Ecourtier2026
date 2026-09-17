package com.ecourtier.domain.utilisateur.dto;

import com.ecourtier.domain.utilisateur.entity.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UtilisateurResponseDTO {
    private UUID id;
    private String nom;
    private String prenom;
    private String email;
    private String telephone;
    private Role role;
    private LocalDateTime dateCreation;
    private boolean actif;
    private String photoProfilUrl;
}
