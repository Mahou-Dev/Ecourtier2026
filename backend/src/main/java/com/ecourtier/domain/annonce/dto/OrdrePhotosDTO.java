package com.ecourtier.domain.annonce.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrdrePhotosDTO {
    @NotEmpty(message = "La liste des IDs de photos ne peut pas être vide")
    private List<UUID> photoIdsEnOrdre;
}
