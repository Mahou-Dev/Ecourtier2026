package com.ecourtier.domain.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RepartitionGeographiqueDTO {
    private String ville;
    private String quartier;
    private long total;
}
