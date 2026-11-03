package com.ecourtier.domain.utilisateur.service;

import com.ecourtier.domain.utilisateur.dto.AdminCreateUtilisateurRequestDTO;
import com.ecourtier.domain.utilisateur.dto.AdminCreateUtilisateurResponseDTO;
import com.ecourtier.domain.utilisateur.dto.ChangerMotDePasseRequestDTO;
import com.ecourtier.domain.utilisateur.dto.UtilisateurResponseDTO;
import com.ecourtier.domain.utilisateur.entity.Role;
import com.ecourtier.domain.utilisateur.entity.UtilisateurEntity;
import com.ecourtier.domain.utilisateur.mapper.UtilisateurMapper;
import com.ecourtier.domain.utilisateur.repository.UtilisateurRepository;
import com.ecourtier.domain.utilisateur.repository.UtilisateurSpecification;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class UtilisateurManagementService {

    private static final Pattern PASSWORD_POLICY = Pattern.compile("^(?=.*[A-Z])(?=.*\\d).{8,}$");
    private static final String UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final String LOWER = "abcdefghijkmnopqrstuvwxyz";
    private static final String DIGITS = "23456789";
    private static final String SPECIAL = "!@#$%";
    private static final String ALL_CHARS = UPPER + LOWER + DIGITS;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UtilisateurRepository utilisateurRepository;
    private final UtilisateurMapper utilisateurMapper;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public Page<UtilisateurResponseDTO> listerUtilisateurs(
            Role role,
            Boolean actif,
            String recherche,
            Pageable pageable
    ) {
        Specification<UtilisateurEntity> spec = UtilisateurSpecification.filterUtilisateurs(role, actif, recherche);
        return utilisateurRepository.findAll(spec, pageable)
                .map(utilisateurMapper::toResponseDTO);
    }

    @Transactional
    public AdminCreateUtilisateurResponseDTO creerUtilisateurParAdmin(AdminCreateUtilisateurRequestDTO request) {
        String emailClean = request.getEmail().toLowerCase().trim();
        if (utilisateurRepository.existsByEmail(emailClean)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Un compte existe déjà avec cette adresse email");
        }

        String clearTempPassword;
        if (request.getMotDePasseTemporaire() == null || request.getMotDePasseTemporaire().trim().isEmpty()) {
            clearTempPassword = genererMotDePasseTemporaireConforme();
        } else {
            clearTempPassword = request.getMotDePasseTemporaire().trim();
            if (!PASSWORD_POLICY.matcher(clearTempPassword).matches()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Le mot de passe temporaire doit contenir au moins 8 caractères, une majuscule et un chiffre"
                );
            }
        }

        UtilisateurEntity entity = UtilisateurEntity.builder()
                .nom(request.getNom().trim())
                .prenom(request.getPrenom().trim())
                .email(emailClean)
                .telephone(request.getTelephone().trim())
                .role(request.getRole())
                .motDePasse(passwordEncoder.encode(clearTempPassword))
                .actif(true)
                .build();

        UtilisateurEntity saved = utilisateurRepository.save(entity);
        return AdminCreateUtilisateurResponseDTO.builder()
                .utilisateur(utilisateurMapper.toResponseDTO(saved))
                .motDePasseTemporaire(clearTempPassword)
                .build();
    }

    @Transactional
    public UtilisateurResponseDTO changerRole(UUID targetId, Role nouveauRole, String adminEmail) {
        UtilisateurEntity target = utilisateurRepository.findById(targetId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Utilisateur introuvable"));

        if (target.getEmail().equalsIgnoreCase(adminEmail) && nouveauRole != Role.ADMIN) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Un administrateur ne peut pas se retirer son propre rôle ADMIN"
            );
        }

        if (target.getRole() == Role.ADMIN && target.isActif() && nouveauRole != Role.ADMIN) {
            long activeAdmins = utilisateurRepository.countByRoleAndActifTrue(Role.ADMIN);
            if (activeAdmins <= 1) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Impossible de retirer le rôle ADMIN : il doit toujours rester au moins un administrateur actif"
                );
            }
        }

        target.setRole(nouveauRole);
        UtilisateurEntity saved = utilisateurRepository.save(target);
        return utilisateurMapper.toResponseDTO(saved);
    }

    @Transactional
    public UtilisateurResponseDTO changerStatut(UUID targetId, boolean actif, String adminEmail) {
        UtilisateurEntity target = utilisateurRepository.findById(targetId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Utilisateur introuvable"));

        if (!actif && target.getEmail().equalsIgnoreCase(adminEmail)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Un administrateur ne peut pas désactiver son propre compte"
            );
        }

        if (!actif && target.getRole() == Role.ADMIN && target.isActif()) {
            long activeAdmins = utilisateurRepository.countByRoleAndActifTrue(Role.ADMIN);
            if (activeAdmins <= 1) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Impossible de désactiver ce compte : il doit toujours rester au moins un administrateur actif"
                );
            }
        }

        target.setActif(actif);
        UtilisateurEntity saved = utilisateurRepository.save(target);
        return utilisateurMapper.toResponseDTO(saved);
    }

    @Transactional(readOnly = true)
    public UtilisateurResponseDTO obtenirProfilCourant(String userEmail) {
        UtilisateurEntity user = utilisateurRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Utilisateur introuvable"));
        return utilisateurMapper.toResponseDTO(user);
    }

    @Transactional
    public void changerMotDePasse(String userEmail, ChangerMotDePasseRequestDTO request) {
        UtilisateurEntity user = utilisateurRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Utilisateur introuvable"));

        if (!passwordEncoder.matches(request.getAncienMotDePasse(), user.getMotDePasse())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "L'ancien mot de passe est incorrect");
        }

        if (passwordEncoder.matches(request.getNouveauMotDePasse(), user.getMotDePasse())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Le nouveau mot de passe doit être différent de l'ancien mot de passe"
            );
        }

        user.setMotDePasse(passwordEncoder.encode(request.getNouveauMotDePasse()));
        utilisateurRepository.save(user);
    }

    private String genererMotDePasseTemporaireConforme() {
        StringBuilder sb = new StringBuilder(12);
        sb.append("Ec");
        sb.append(UPPER.charAt(SECURE_RANDOM.nextInt(UPPER.length())));
        sb.append(DIGITS.charAt(SECURE_RANDOM.nextInt(DIGITS.length())));
        sb.append(SPECIAL.charAt(SECURE_RANDOM.nextInt(SPECIAL.length())));
        for (int i = 0; i < 7; i++) {
            sb.append(ALL_CHARS.charAt(SECURE_RANDOM.nextInt(ALL_CHARS.length())));
        }
        return sb.toString();
    }
}
