package com.ecourtier.common.security;

import com.ecourtier.domain.annonce.entity.AnnonceEntity;
import com.ecourtier.domain.annonce.repository.AnnonceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service("annonceSecurityService")
@RequiredArgsConstructor
public class AnnonceSecurityService {

    private final AnnonceRepository annonceRepository;

    @Transactional(readOnly = true)
    public boolean isOwnerOrAdmin(UUID annonceId, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }

        // Admins are granted full management rights
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"));
        if (isAdmin) {
            return true;
        }

        String currentUserEmail = authentication.getName();

        AnnonceEntity annonce = annonceRepository.findByIdAndSupprimeFalse(annonceId)
                .orElse(null);

        if (annonce == null || annonce.getUtilisateur() == null) {
            return false;
        }

        // Verify author ownership
        return annonce.getUtilisateur().getEmail().equalsIgnoreCase(currentUserEmail);
    }
}
