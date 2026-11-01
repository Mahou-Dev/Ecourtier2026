package com.ecourtier.domain.rendezvous.dto;

import com.ecourtier.domain.rendezvous.entity.TypeNotificationSms;
import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "Notification SMS simulée enregistrée pour un utilisateur")
public class NotificationSmsResponseDTO {

    private UUID id;
    private UUID utilisateurId;
    private String telephone;
    private TypeNotificationSms type;
    private String message;
    private String statut;
    private UUID rdvId;
    private String annonceTitre;
    private LocalDateTime dateCreation;
}
