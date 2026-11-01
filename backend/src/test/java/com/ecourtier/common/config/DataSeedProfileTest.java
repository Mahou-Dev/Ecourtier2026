package com.ecourtier.common.config;

import com.ecourtier.domain.annonce.repository.AnnonceRepository;
import com.ecourtier.domain.utilisateur.repository.UtilisateurRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
    "ADMIN_BOOTSTRAP_PASSWORD=",
    "JWT_SECRET=404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970",
    "spring.datasource.url=jdbc:h2:mem:prodseedtest;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@ActiveProfiles("prod")
public class DataSeedProfileTest {

    @Autowired(required = false)
    private DataSeedRunner dataSeedRunner;

    @Autowired
    private Environment environment;

    @Autowired
    private AnnonceRepository annonceRepository;

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    @DisplayName("Vérifie que le DataSeedRunner NE S'EXÉCUTE PAS sous le profil PROD (Bean non créé et Guard actif)")
    void testDataSeedRunnerDisabledInProdProfile() {
        // 1. Confirmation : Le profil actif est bien "prod" et non "dev"
        assertTrue(Arrays.asList(environment.getActiveProfiles()).contains("prod"));
        assertFalse(Arrays.asList(environment.getActiveProfiles()).contains("dev"));

        // 2. Le bean DataSeedRunner ne doit PAS être instancié par Spring en profil prod
        assertNull(dataSeedRunner, "DataSeedRunner ne doit pas être présent dans le contexte Spring sous le profil prod (@Profile('dev'))");

        // 3. Test de la garde explicite en cas d'invocation manuelle accidentelle
        com.ecourtier.common.service.DemoDataSeederService demoDataSeederService = org.mockito.Mockito.mock(com.ecourtier.common.service.DemoDataSeederService.class);
        DataSeedRunner manualRunner = new DataSeedRunner(demoDataSeederService, environment);
        long countAnnoncesBefore = annonceRepository.count();
        long countUsersBefore = utilisateurRepository.count();

        manualRunner.seedDataOnStartup();

        org.mockito.Mockito.verifyNoInteractions(demoDataSeederService);
        assertEquals(countAnnoncesBefore, annonceRepository.count(), "Aucune annonce ne doit être insérée par le seed sous le profil prod");
        assertEquals(countUsersBefore, utilisateurRepository.count(), "Aucun utilisateur ne doit être inséré par le seed sous le profil prod");
    }
}
