package com.ecourtier.domain.ia.dto;

import com.ecourtier.domain.ia.entity.RoleMessageIA;
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
public class MessageItemDTO {
    private UUID id;
    private RoleMessageIA role;
    private String contenu;
    private CriteresExtraitsDTO criteresExtraits;
    private LocalDateTime dateEnvoi;
}
