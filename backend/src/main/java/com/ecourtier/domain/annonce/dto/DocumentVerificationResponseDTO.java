package com.ecourtier.domain.annonce.dto;

import com.ecourtier.domain.annonce.entity.StatutDocument;
import com.ecourtier.domain.annonce.entity.TypeDocumentVerification;
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
public class DocumentVerificationResponseDTO {
    private UUID id;
    private TypeDocumentVerification typeDocument;
    private String url;
    private StatutDocument statut;
    private String motifRejet;
    private LocalDateTime dateCreation;
}
