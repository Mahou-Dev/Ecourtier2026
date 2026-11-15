package com.ecourtier.domain.annonce.dto;

import com.ecourtier.domain.annonce.entity.StatutPublication;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnnonceStatutUpdateDTO {

    @NotNull(message = "Le statut de publication cible est obligatoire")
    private StatutPublication statutPublication;

    @Size(max = 500, message = "Le motif ne peut pas dépasser 500 caractères")
    private String motif;

    private Long version;

    public AnnonceStatutUpdateDTO(StatutPublication statutPublication, String motif) {
        this.statutPublication = statutPublication;
        this.motif = motif;
    }
}
