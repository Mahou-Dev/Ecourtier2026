package com.ecourtier.common.security;

import com.ecourtier.domain.utilisateur.entity.Role;
import com.ecourtier.domain.utilisateur.entity.UtilisateurEntity;
import com.ecourtier.domain.utilisateur.repository.UtilisateurRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import javax.crypto.SecretKey;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class SecurityRbacRealServerIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${ecourtier.jwt.secret}")
    private String jwtSecret;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private UtilisateurEntity locataire;
    private UtilisateurEntity admin;

    @BeforeEach
    void setUp() {
        utilisateurRepository.deleteAll();

        locataire = utilisateurRepository.save(UtilisateurEntity.builder()
                .nom("Diallo")
                .prenom("Fatou")
                .email("locataire.rbac.real@ecourtier.sn")
                .motDePasse(passwordEncoder.encode("LocatairePass123!"))
                .telephone("+221 77 200 10 20")
                .role(Role.LOCATAIRE)
                .actif(true)
                .build());

        admin = utilisateurRepository.save(UtilisateurEntity.builder()
                .nom("Fall")
                .prenom("Ibrahima")
                .email("admin.rbac.real@ecourtier.sn")
                .motDePasse(passwordEncoder.encode("AdminPass123!"))
                .telephone("+221 77 300 10 20")
                .role(Role.ADMIN)
                .actif(true)
                .build());
    }

    @Test
    @DisplayName("Serveur réel : LOCATAIRE vers /api/v1/admin/statistiques renvoie 403 JSON UTF-8 sans caractères corrompus")
    void testLocataireVersAdminStatistiquesRenvoie403JsonUtf8() throws Exception {
        String locataireToken = jwtService.generateAccessToken(locataire);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/v1/admin/statistiques"))
                .header("Authorization", "Bearer " + locataireToken)
                .GET()
                .build();

        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        String body = new String(response.body(), StandardCharsets.UTF_8);

        assertEquals(403, response.statusCode(), "Un LOCATAIRE authentifié doit recevoir 403 Forbidden et non 401");
        String contentType = response.headers().firstValue("Content-Type").orElse("");
        assertTrue(contentType.toLowerCase().contains("application/json"), "Content-Type doit être JSON : " + contentType);
        assertTrue(contentType.toLowerCase().contains("utf-8"), "Content-Type doit spécifier UTF-8 : " + contentType);

        assertNoCorruptedCharacters(body);
        JsonNode json = objectMapper.readTree(body);
        assertEquals(403, json.get("status").asInt());
        String errorMsg = json.get("error").asText();
        assertTrue(errorMsg.contains("Accès refusé —"), "Les accents et le tiret cadratin doivent être intacts : " + errorMsg);
    }

    @Test
    @DisplayName("Serveur réel : requête sans jeton vers /api/v1/admin/statistiques renvoie 401 JSON UTF-8 sans caractères corrompus")
    void testSansJetonVersAdminStatistiquesRenvoie401JsonUtf8() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/v1/admin/statistiques"))
                .GET()
                .build();

        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        String body = new String(response.body(), StandardCharsets.UTF_8);

        assertEquals(401, response.statusCode(), "Une requête sans jeton doit recevoir 401 Unauthorized");
        String contentType = response.headers().firstValue("Content-Type").orElse("");
        assertTrue(contentType.toLowerCase().contains("application/json"), "Content-Type doit être JSON : " + contentType);
        assertTrue(contentType.toLowerCase().contains("utf-8"), "Content-Type doit spécifier UTF-8 : " + contentType);

        assertNoCorruptedCharacters(body);
        JsonNode json = objectMapper.readTree(body);
        assertEquals(401, json.get("status").asInt());
        String errorMsg = json.get("error").asText();
        assertTrue(errorMsg.contains("Non authentifié —"), "Les accents et le tiret cadratin doivent être intacts : " + errorMsg);
        assertTrue(errorMsg.contains("expiré"), "Le mot 'expiré' doit être encodé proprement en UTF-8 : " + errorMsg);
    }

    @Test
    @DisplayName("Serveur réel : jeton expiré vers /api/v1/admin/statistiques renvoie 401 JSON UTF-8 sans caractères corrompus")
    void testJetonExpireVersAdminStatistiquesRenvoie401JsonUtf8() throws Exception {
        SecretKey key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtSecret));
        Date pastIssuedAt = new Date(System.currentTimeMillis() - 120_000L);
        Date pastExpiration = new Date(System.currentTimeMillis() - 60_000L);

        String expiredToken = Jwts.builder()
                .claims(Map.of(
                        "userId", admin.getId().toString(),
                        "role", Role.ADMIN.name(),
                        "type", "ACCESS"
                ))
                .subject(admin.getEmail())
                .issuedAt(pastIssuedAt)
                .expiration(pastExpiration)
                .signWith(key)
                .compact();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/v1/admin/statistiques"))
                .header("Authorization", "Bearer " + expiredToken)
                .GET()
                .build();

        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        String body = new String(response.body(), StandardCharsets.UTF_8);

        assertEquals(401, response.statusCode(), "Un jeton expiré doit recevoir 401 Unauthorized");
        String contentType = response.headers().firstValue("Content-Type").orElse("");
        assertTrue(contentType.toLowerCase().contains("application/json"), "Content-Type doit être JSON : " + contentType);
        assertTrue(contentType.toLowerCase().contains("utf-8"), "Content-Type doit spécifier UTF-8 : " + contentType);

        assertNoCorruptedCharacters(body);
        JsonNode json = objectMapper.readTree(body);
        assertEquals(401, json.get("status").asInt());
        String errorMsg = json.get("error").asText();
        assertTrue(errorMsg.contains("Non authentifié —"), "Les accents et le tiret cadratin doivent être intacts : " + errorMsg);
    }

    private void assertNoCorruptedCharacters(String body) {
        assertFalse(body.contains("\uFFFD"), "Le corps ne doit contenir aucun caractère de remplacement Unicode U+FFFD : " + body);
        assertFalse(body.contains("Ã"), "Le corps ne doit contenir aucun artefact mojibake ISO-8859-1 -> UTF-8 : " + body);
        assertFalse(body.contains("? "), "Le tiret cadratin ne doit pas être dégradé en '?' : " + body);
    }
}
