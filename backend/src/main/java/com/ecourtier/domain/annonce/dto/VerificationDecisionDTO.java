package com.ecourtier.domain.annonce.dto;

import com.ecourtier.domain.annonce.entity.StatutVerification;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VerificationDecisionDTO {
    @NotNull(message = "Le statut de vérification est obligatoire (VERIFIEE ou REJETEE)")
    private StatutVerification statutVerification;

    private String motifRejet;
}
