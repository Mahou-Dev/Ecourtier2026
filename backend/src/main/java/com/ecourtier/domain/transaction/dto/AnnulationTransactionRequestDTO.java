package com.ecourtier.domain.transaction.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnnulationTransactionRequestDTO {

    @Size(max = 500, message = "Le motif d'annulation ne peut pas dépasser 500 caractères")
    private String motif;
}
