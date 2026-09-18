package com.ecourtier.domain.ia.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MessageUserRequestDTO {

    @NotBlank(message = "Le message ne peut pas être vide")
    @Size(max = 500, message = "Le message ne doit pas dépasser 500 caractères")
    private String message;
}
