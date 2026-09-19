package com.ecourtier.common.controller;

import com.ecourtier.common.service.DemoDataSeederService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class AdminSeedControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DemoDataSeederService demoDataSeederService;

    @Test
    @DisplayName("POST /api/v1/admin/seed-demo - Accès refusé sans rôle ADMIN (403)")
    @WithMockUser(roles = "LOCATAIRE")
    void seedDemoData_ForbiddenForNonAdmin() throws Exception {
        mockMvc.perform(post("/api/v1/admin/seed-demo"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/v1/admin/seed-demo - Succès avec rôle ADMIN (200 OK - Base vide)")
    @WithMockUser(roles = "ADMIN")
    void seedDemoData_SuccessWhenEmpty() throws Exception {
        given(demoDataSeederService.seedDemoData()).willReturn(true);

        mockMvc.perform(post("/api/v1/admin/seed-demo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.seeded").value(true))
                .andExpect(jsonPath("$.message").value("Données de démonstration initialisées avec succès."));
    }

    @Test
    @DisplayName("POST /api/v1/admin/seed-demo - Idempotence (200 OK - Base déjà peuplée)")
    @WithMockUser(roles = "ADMIN")
    void seedDemoData_IdempotentWhenNotEmpty() throws Exception {
        given(demoDataSeederService.seedDemoData()).willReturn(false);

        mockMvc.perform(post("/api/v1/admin/seed-demo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.seeded").value(false))
                .andExpect(jsonPath("$.message").value("La base de données contient déjà des annonces. Aucune donnée n'a été insérée."));
    }
}
