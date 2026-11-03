package com.ecourtier;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@ActiveProfiles("test")
class EcourtierApplicationTest {

    @Test
    @DisplayName("Chargement du contexte Spring Boot et instanciation EcourtierApplication")
    void contextLoads() {
        EcourtierApplication app = new EcourtierApplication();
        assertNotNull(app);
    }

    @Test
    @DisplayName("Exécution de la méthode main d'EcourtierApplication")
    void testMainMethod() {
        try (var mocked = org.mockito.Mockito.mockStatic(org.springframework.boot.SpringApplication.class)) {
            mocked.when(() -> org.springframework.boot.SpringApplication.run(
                    org.mockito.ArgumentMatchers.eq(EcourtierApplication.class),
                    org.mockito.ArgumentMatchers.any(String[].class)))
                  .thenReturn(null);

            EcourtierApplication.main(new String[]{});

            mocked.verify(() -> org.springframework.boot.SpringApplication.run(
                    org.mockito.ArgumentMatchers.eq(EcourtierApplication.class),
                    org.mockito.ArgumentMatchers.any(String[].class)));
        }
    }
}
