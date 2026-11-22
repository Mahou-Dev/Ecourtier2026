package com.ecourtier.domain.ia.dto;

import com.ecourtier.domain.annonce.dto.AnnonceResponseDTO;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MessageIaResponseDTO {
    private UUID id;
    private String reponse;
    private List<AnnonceResponseDTO> annoncesSuggeres;
    private CriteresExtraitsDTO criteresExtraits;
    private String questionDeRelance;
    private LocalDateTime dateEnvoi;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private IaMetriquesDTO metriques;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class IaMetriquesDTO {
        private Long extractMs;
        private Long searchMs;
        private Long generationMs;
        private Long llmMs;
        private Long totalMs;
    }
}
