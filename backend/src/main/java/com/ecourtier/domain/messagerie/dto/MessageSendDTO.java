package com.ecourtier.domain.messagerie.dto;

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
public class MessageSendDTO {

    @NotBlank(message = "Le contenu du message ne peut pas être vide")
    @Size(max = 1000, message = "Le contenu du message ne peut pas dépasser 1000 caractères")
    private String contenu;
}
