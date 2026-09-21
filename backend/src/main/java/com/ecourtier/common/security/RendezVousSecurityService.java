package com.ecourtier.common.security;

import com.ecourtier.domain.annonce.entity.AnnonceEntity;
import com.ecourtier.domain.annonce.repository.AnnonceRepository;
import com.ecourtier.domain.rendezvous.entity.RendezVousEntity;
import com.ecourtier.domain.rendezvous.repository.RendezVousRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service("rendezVousSecurityService")
@RequiredArgsConstructor
public class RendezVousSecurityService {

    private final RendezVousRepository rendezVousRepository;
    private final AnnonceRepository annonceRepository;

    @Transactional(readOnly = true)
    public boolean isAnnonceOwnerOrAdmin(UUID annonceId, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }

        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (isAdmin) return true;

        String currentUserEmail = authentication.getName();
        AnnonceEntity annonce = annonceRepository.findByIdAndSupprimeFalse(annonceId).orElse(null);
        return annonce != null && annonce.getUtilisateur() != null
                && annonce.getUtilisateur().getEmail().equalsIgnoreCase(currentUserEmail);
    }

    @Transactional(readOnly = true)
    public boolean isOwnerOrAdmin(UUID rdvId, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }

        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (isAdmin) return true;

        String currentUserEmail = authentication.getName();
        RendezVousEntity rdv = rendezVousRepository.findById(rdvId).orElse(null);
        if (rdv == null || rdv.getAnnonce() == null || rdv.getAnnonce().getUtilisateur() == null) {
            return false;
        }

        return rdv.getAnnonce().getUtilisateur().getEmail().equalsIgnoreCase(currentUserEmail);
    }

    @Transactional(readOnly = true)
    public boolean isDemandeurOrOwnerOrAdmin(UUID rdvId, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }

        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (isAdmin) return true;

        String currentUserEmail = authentication.getName();
        RendezVousEntity rdv = rendezVousRepository.findById(rdvId).orElse(null);
        if (rdv == null) return false;

        boolean isDemandeur = rdv.getDemandeur() != null && rdv.getDemandeur().getEmail().equalsIgnoreCase(currentUserEmail);
        boolean isOwner = rdv.getAnnonce() != null && rdv.getAnnonce().getUtilisateur() != null
                && rdv.getAnnonce().getUtilisateur().getEmail().equalsIgnoreCase(currentUserEmail);

        return isDemandeur || isOwner;
    }
}
