package com.ecourtier.common.security;

import com.ecourtier.domain.transaction.entity.TransactionEntity;
import com.ecourtier.domain.transaction.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service("transactionSecurityService")
@RequiredArgsConstructor
public class TransactionSecurityService {

    private final TransactionRepository transactionRepository;

    @Transactional(readOnly = true)
    public boolean isStakeholderOrAdmin(UUID transactionId, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }

        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (isAdmin) return true;

        String currentUserEmail = authentication.getName();
        TransactionEntity tx = transactionRepository.findById(transactionId).orElse(null);
        if (tx == null) return false;

        boolean isLocataire = tx.getLocataire() != null && tx.getLocataire().getEmail().equalsIgnoreCase(currentUserEmail);
        boolean isProprietaire = tx.getProprietaire() != null && tx.getProprietaire().getEmail().equalsIgnoreCase(currentUserEmail);

        return isLocataire || isProprietaire;
    }

    @Transactional(readOnly = true)
    public boolean isLocataireOrAdmin(UUID transactionId, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }

        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (isAdmin) return true;

        String currentUserEmail = authentication.getName();
        TransactionEntity tx = transactionRepository.findById(transactionId).orElse(null);
        if (tx == null || tx.getLocataire() == null) return false;

        return tx.getLocataire().getEmail().equalsIgnoreCase(currentUserEmail);
    }
}
