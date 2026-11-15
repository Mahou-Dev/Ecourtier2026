package com.ecourtier.common.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OpenApiContractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("Contrat OpenAPI synchronisé entre /v3/api-docs et /openapi.json avec tous les endpoints et statuts Phase 2")
    void testOpenApiContractSynchronizedWithPhase2() throws Exception {
        MvcResult liveResult = mockMvc.perform(get("/v3/api-docs")
                        .header("Host", "localhost:8080"))
                .andExpect(status().isOk())
                .andReturn();

        String liveRaw = liveResult.getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode liveRoot = objectMapper.readTree(liveRaw);

        String exportPath = System.getProperty("ecourtier.export.openapi");
        if (exportPath != null && !exportPath.isBlank()) {
            Path target = Path.of(exportPath);
            Files.createDirectories(target.getParent());
            String pretty = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(liveRoot);
            Files.writeString(target, pretty + System.lineSeparator(), StandardCharsets.UTF_8);
            return;
        }

        // Vérifier que les chemins Phase 2 et Phase 3 sont bien exposés par Springdoc live
        JsonNode livePaths = liveRoot.path("paths");
        for (String requiredPath : new String[]{
                "/api/v1/transactions/{id}/annuler",
                "/api/v1/transactions/{id}/remboursement",
                "/api/v1/transactions/{id}/rembourser",
                "/api/v1/transactions/{id}/bail/pdf/generer",
                "/api/v1/webhooks/wave",
                "/api/v1/admin/transactions/reconcilier-wave",
                "/api/v1/auth/deconnexion",
                "/api/v1/auth/rafraichir",
                "/api/v1/annonces/{id}"
        }) {
            assertTrue(livePaths.has(requiredPath), "Chemin OpenAPI manquant dans /v3/api-docs : " + requiredPath);
        }

        // Vérifier les schémas AnnonceResponseDTO / AnnonceUpdateDTO (.version requis), TransactionResponseDTO, StatutSequestre, StatutDocumentBail
        JsonNode schemas = liveRoot.path("components").path("schemas");
        assertTrue(schemas.path("AnnonceResponseDTO").path("properties").has("version"),
                "AnnonceResponseDTO doit exposer le champ version");
        assertTrue(schemas.path("AnnonceUpdateDTO").path("properties").has("version"),
                "AnnonceUpdateDTO doit exposer le champ version");
        Set<String> updateRequired = new TreeSet<>();
        schemas.path("AnnonceUpdateDTO").path("required").forEach(n -> updateRequired.add(n.asText()));
        assertTrue(updateRequired.contains("version"),
                "AnnonceUpdateDTO doit déclarer version comme champ obligatoire (required)");

        JsonNode txProps = schemas.path("TransactionResponseDTO").path("properties");
        assertTrue(txProps.has("statutSequestre"), "TransactionResponseDTO doit exposer statutSequestre");
        assertTrue(txProps.has("dossierActif"), "TransactionResponseDTO doit exposer dossierActif");
        assertTrue(txProps.has("dateExpiration"), "TransactionResponseDTO doit exposer dateExpiration");
        assertTrue(txProps.has("statutDocumentBail"), "TransactionResponseDTO doit exposer statutDocumentBail");
        assertTrue(txProps.has("pdfBailErreur"), "TransactionResponseDTO doit exposer pdfBailErreur");
        assertTrue(txProps.has("pdfBailTentatives"), "TransactionResponseDTO doit exposer pdfBailTentatives");
        assertTrue(txProps.has("pdfBailDerniereTentative"), "TransactionResponseDTO doit exposer pdfBailDerniereTentative");

        JsonNode statutSequestreEnum = txProps.path("statutSequestre").path("enum");
        assertFalse(statutSequestreEnum.isMissingNode());
        Set<String> enumValues = new TreeSet<>();
        statutSequestreEnum.forEach(n -> enumValues.add(n.asText()));
        assertEquals(Set.of("INITIALISE", "BLOQUE", "LIBERE", "REMBOURSE", "ANNULE", "EXPIRE"), enumValues);

        assertTrue(schemas.has("ApiErrorResponse"), "OpenAPI doit enregistrer le schéma standard ApiErrorResponse");
        assertEquals("#/components/schemas/ApiErrorResponse",
                livePaths.path("/api/v1/annonces/{id}").path("put").path("responses").path("409")
                        .path("content").path("application/json").path("schema").path("$ref").asText());

        // Vérifier que les endpoints paginés exposent page/size/sort au lieu d'un objet pageable opaque
        Set<String> annoncesParamNames = new TreeSet<>();
        livePaths.path("/api/v1/annonces").path("get").path("parameters")
                .forEach(p -> annoncesParamNames.add(p.path("name").asText()));
        assertTrue(annoncesParamNames.contains("page"), "GET /api/v1/annonces doit documenter le paramètre query page");
        assertTrue(annoncesParamNames.contains("size"), "GET /api/v1/annonces doit documenter le paramètre query size");
        assertFalse(annoncesParamNames.contains("pageable"), "GET /api/v1/annonces ne doit plus exposer un objet pageable non-plat");

        // Vérifier que les endpoints publics surchargent la sécurité globale (security: [])
        assertTrue(livePaths.path("/api/v1/health").path("get").has("security")
                && livePaths.path("/api/v1/health").path("get").path("security").isEmpty(),
                "GET /api/v1/health doit déclarer security: []");
        assertTrue(livePaths.path("/api/v1/annonces").path("get").has("security")
                && livePaths.path("/api/v1/annonces").path("get").path("security").isEmpty(),
                "GET /api/v1/annonces doit déclarer security: []");

        // Vérifier les codes de statut Phase 2 et Phase 3 documentés
        assertTrue(livePaths.path("/api/v1/auth/rafraichir").path("post").path("responses").has("429"));
        assertTrue(livePaths.path("/api/v1/auth/rafraichir").path("post").path("responses").has("401"));
        assertTrue(livePaths.path("/api/v1/annonces/{id}").path("put").path("responses").has("409"));
        assertTrue(livePaths.path("/api/v1/annonces/{id}").path("delete").path("responses").has("409"));
        assertTrue(livePaths.path("/api/v1/transactions/{id}/bail/signer").path("post").path("responses").has("409"));
        assertTrue(livePaths.path("/api/v1/transactions/{id}/bail/pdf/generer").path("post").path("responses").has("503"));
        assertTrue(livePaths.path("/api/v1/webhooks/wave").path("post").path("responses").has("401"));
        assertTrue(livePaths.path("/api/v1/webhooks/wave").path("post").path("responses").has("409"));
        assertTrue(livePaths.path("/api/v1/admin/transactions/reconcilier-wave").path("post").path("responses").has("403"));

        // Comparer avec /openapi.json (resources/static/openapi.json)
        MvcResult staticResult = mockMvc.perform(get("/openapi.json"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode staticRoot = objectMapper.readTree(staticResult.getResponse().getContentAsString(StandardCharsets.UTF_8));

        Set<String> liveOperations = collectOperations(livePaths);
        Set<String> staticOperations = collectOperations(staticRoot.path("paths"));
        assertEquals(liveOperations, staticOperations,
                "openapi.json statique doit contenir exactement les mêmes opérations HTTP que /v3/api-docs");

        assertEquals(liveRoot.path("components").path("schemas").path("AnnonceResponseDTO"),
                staticRoot.path("components").path("schemas").path("AnnonceResponseDTO"));
        assertEquals(liveRoot.path("components").path("schemas").path("AnnonceUpdateDTO"),
                staticRoot.path("components").path("schemas").path("AnnonceUpdateDTO"));
        assertEquals(liveRoot.path("components").path("schemas").path("TransactionResponseDTO"),
                staticRoot.path("components").path("schemas").path("TransactionResponseDTO"));
    }

    private Set<String> collectOperations(JsonNode pathsNode) {
        Set<String> ops = new TreeSet<>();
        Iterator<String> pathsIt = pathsNode.fieldNames();
        while (pathsIt.hasNext()) {
            String path = pathsIt.next();
            JsonNode methods = pathsNode.path(path);
            Iterator<String> methodIt = methods.fieldNames();
            while (methodIt.hasNext()) {
                String method = methodIt.next();
                ops.add(method.toUpperCase() + " " + path);
            }
        }
        return ops;
    }
}
