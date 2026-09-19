package com.ecourtier.common.config;

import com.ecourtier.domain.utilisateur.entity.Role;
import com.ecourtier.domain.utilisateur.entity.UtilisateurEntity;
import com.ecourtier.domain.utilisateur.repository.UtilisateurRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class AdminBootstrapRunner implements CommandLineRunner {

    private final UtilisateurRepository utilisateurRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${ecourtier.admin.bootstrap.email:admin@ecourtier.sn}")
    private String bootstrapEmail;

    @Value("${ecourtier.admin.bootstrap.password:}")
    private String bootstrapPassword;

    @Override
    public void run(String... args) {
        boolean hasAdmin = utilisateurRepository.existsByRole(Role.ADMIN);

        if (!hasAdmin) {
            if (bootstrapPassword != null && !bootstrapPassword.trim().isEmpty()) {
                log.info("[ADMIN BOOTSTRAP] Aucun administrateur détecté en base. Création du compte ADMIN initial ({})", bootstrapEmail);
                
                UtilisateurEntity admin = UtilisateurEntity.builder()
                        .nom("Administrateur")
                        .prenom("Système")
                        .email(bootstrapEmail.trim().toLowerCase())
                        .motDePasse(passwordEncoder.encode(bootstrapPassword.trim()))
                        .telephone("+221700000000")
                        .role(Role.ADMIN)
                        .actif(true)
                        .build();

                utilisateurRepository.save(admin);
                log.info("[ADMIN BOOTSTRAP] Compte ADMIN initial créé avec succès.");
            } else {
                log.warn("[ADMIN BOOTSTRAP] Aucun compte ADMIN présent en base de données et la variable d'environnement ADMIN_BOOTSTRAP_PASSWORD n'est pas définie. Aucun compte administrateur n'a été créé. Pour bootstrapper le premier administrateur, lancez l'application en définissant la variable ADMIN_BOOTSTRAP_PASSWORD (et optionnellement ADMIN_BOOTSTRAP_EMAIL).");
            }
        } else {
            log.debug("[ADMIN BOOTSTRAP] Un ou plusieurs comptes ADMIN existent déjà en base. Aucune action de bootstrap requise.");
        }
    }
}
