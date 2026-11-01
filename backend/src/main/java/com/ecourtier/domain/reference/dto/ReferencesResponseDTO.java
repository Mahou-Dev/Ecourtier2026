package com.ecourtier.domain.reference.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReferencesResponseDTO {
    private List<ReferenceItemDTO> typesBien;
    private List<ReferenceItemDTO> equipements;
    private List<ReferenceItemDTO> proximites;
    private List<ReferenceItemDTO> modesTransaction;
    private List<ReferenceItemDTO> typesDocument;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReferenceItemDTO {
        private String valeur;
        private String libelle;
        private String description;
    }
}
