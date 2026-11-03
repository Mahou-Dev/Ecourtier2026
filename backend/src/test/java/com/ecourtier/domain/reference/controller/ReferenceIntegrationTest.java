package com.ecourtier.domain.reference.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReferenceIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("GET /api/v1/references : retourne tous les référentiels avec libellés français exacts")
    void testGetReferencesContenuEtLibelles() throws Exception {
        mockMvc.perform(get("/api/v1/references"))
                .andExpect(status().isOk())
                // Types de bien
                .andExpect(jsonPath("$.typesBien", hasSize(3)))
                .andExpect(jsonPath("$.typesBien[?(@.valeur=='APPARTEMENT')].libelle").value("Appartement"))
                .andExpect(jsonPath("$.typesBien[?(@.valeur=='VILLA')].libelle").value("Villa"))
                .andExpect(jsonPath("$.typesBien[?(@.valeur=='STUDIO')].libelle").value("Studio"))
                // Équipements
                .andExpect(jsonPath("$.equipements", hasSize(7)))
                .andExpect(jsonPath("$.equipements[?(@.valeur=='WIFI')].libelle").value("Wi-Fi"))
                .andExpect(jsonPath("$.equipements[?(@.valeur=='CLIMATISATION')].libelle").value("Climatisation"))
                .andExpect(jsonPath("$.equipements[?(@.valeur=='RESERVE_EAU')].libelle").value("Réserve d'eau / Cuve"))
                .andExpect(jsonPath("$.equipements[?(@.valeur=='GROUPE_ELECTROGENE')].libelle").value("Groupe électrogène"))
                // Proximités
                .andExpect(jsonPath("$.proximites", hasSize(7)))
                .andExpect(jsonPath("$.proximites[?(@.valeur=='ECOLE')].libelle").value("École"))
                .andExpect(jsonPath("$.proximites[?(@.valeur=='HOPITAL')].libelle").value("Hôpital / Clinique"))
                // Types documents avec libellé exact exigé par le cahier
                .andExpect(jsonPath("$.typesDocument", hasSize(3)))
                .andExpect(jsonPath("$.typesDocument[?(@.valeur=='PIECE_IDENTITE')].libelle")
                        .value("Photo du propriétaire avec sa pièce d'identité"))
                .andExpect(jsonPath("$.typesDocument[?(@.valeur=='TITRE_FONCIER')].libelle")
                        .value("Titre foncier / Bail"))
                .andExpect(jsonPath("$.typesDocument[?(@.valeur=='BAIL_PRECEDENT')].libelle")
                        .value("Bail précédent / Facture Senelec"))
                // Modes de transaction
                .andExpect(jsonPath("$.modesTransaction", hasSize(2)))
                .andExpect(jsonPath("$.modesTransaction[?(@.valeur=='LOCATION')].libelle").value("Location"))
                .andExpect(jsonPath("$.modesTransaction[?(@.valeur=='VENTE')].libelle").value("Vente"));
    }
}
