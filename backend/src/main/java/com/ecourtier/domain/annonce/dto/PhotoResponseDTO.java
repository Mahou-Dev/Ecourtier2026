package com.ecourtier.domain.annonce.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PhotoResponseDTO {
    private UUID id;
    private String url;
    private Integer ordre;
}
