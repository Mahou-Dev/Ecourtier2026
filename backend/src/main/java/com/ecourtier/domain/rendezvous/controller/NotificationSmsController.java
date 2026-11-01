package com.ecourtier.domain.rendezvous.controller;

import com.ecourtier.domain.rendezvous.dto.NotificationSmsResponseDTO;
import com.ecourtier.domain.rendezvous.service.NotificationSmsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications-sms")
@RequiredArgsConstructor
@Tag(name = "Notifications SMS", description = "Consultation des notifications SMS simulées liées aux visites")
@SecurityRequirement(name = "bearerAuth")
public class NotificationSmsController {

    private final NotificationSmsService notificationSmsService;

    @GetMapping("/mes-sms")
    @PreAuthorize("isAuthenticated()")
    @Operation(
            summary = "Lister mes SMS simulés (paginé)",
            description = "Retourne la liste paginée des notifications SMS simulées (demande, confirmation, annulation, rappel) adressées à l'utilisateur connecté."
    )
    public ResponseEntity<Page<NotificationSmsResponseDTO>> listerMesSms(
            @PageableDefault(size = 20, sort = "dateCreation", direction = Sort.Direction.DESC) Pageable pageable,
            Authentication authentication
    ) {
        return ResponseEntity.ok(notificationSmsService.obtenirMesSms(authentication.getName(), pageable));
    }
}
