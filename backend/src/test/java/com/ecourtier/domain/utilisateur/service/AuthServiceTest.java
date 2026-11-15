package com.ecourtier.domain.utilisateur.service;

import com.ecourtier.common.security.JwtService;
import com.ecourtier.domain.utilisateur.dto.ConnexionRequestDTO;
import com.ecourtier.domain.utilisateur.dto.InscriptionRequestDTO;
import com.ecourtier.domain.utilisateur.dto.RefreshTokenRequestDTO;
import com.ecourtier.domain.utilisateur.dto.TokenResponseDTO;
import com.ecourtier.domain.utilisateur.dto.UtilisateurResponseDTO;
import com.ecourtier.domain.utilisateur.entity.Role;
import com.ecourtier.domain.utilisateur.entity.UtilisateurEntity;
import com.ecourtier.domain.utilisateur.mapper.UtilisateurMapper;
import com.ecourtier.domain.utilisateur.repository.UtilisateurRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import com.ecourtier.domain.utilisateur.entity.RefreshTokenEntity;
import com.ecourtier.domain.utilisateur.repository.RefreshTokenRepository;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class AuthServiceTest {

    @Mock
    private UtilisateurRepository utilisateurRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private UtilisateurMapper utilisateurMapper;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @InjectMocks
    private AuthService authService;

    private UtilisateurEntity user;

    @BeforeEach
    void setUp() {
        user = UtilisateurEntity.builder()
                .id(UUID.randomUUID())
                .nom("Diop")
                .prenom("Moussa")
                .email("moussa.test@ecourtier.sn")
                .motDePasse("hashedPass123!")
                .telephone("+221770001122")
                .role(Role.LOCATAIRE)
                .actif(true)
                .build();
    }

    @Test
    @DisplayName("Inscription réussie pour un rôle LOCATAIRE")
    void testInscriptionSuccess() {
        InscriptionRequestDTO req = InscriptionRequestDTO.builder()
                .nom("Diop")
                .prenom("Moussa")
                .email("moussa.test@ecourtier.sn")
                .motDePasse("Pass1234!")
                .telephone("+221770001122")
                .role(Role.LOCATAIRE)
                .build();

        when(utilisateurRepository.existsByEmail("moussa.test@ecourtier.sn")).thenReturn(false);
        when(passwordEncoder.encode("Pass1234!")).thenReturn("hashedPass123!");
        when(utilisateurRepository.save(any(UtilisateurEntity.class))).thenReturn(user);
        when(jwtService.generateAccessToken(any())).thenReturn("mock-access-token");
        when(jwtService.generateRefreshToken(any(), any())).thenReturn("mock-refresh-token");
        when(jwtService.getAccessTokenExpirationMs()).thenReturn(3600000L);
        when(utilisateurMapper.toResponseDTO(any())).thenReturn(new UtilisateurResponseDTO());

        TokenResponseDTO response = authService.inscription(req);

        assertNotNull(response);
        assertEquals("mock-access-token", response.getAccessToken());
        assertEquals("mock-refresh-token", response.getRefreshToken());
        assertEquals("Bearer", response.getTokenType());
        assertEquals(3600, response.getExpiresIn());
    }

    @Test
    @DisplayName("Inscription refusée si l'email existe déjà (409 CONFLICT)")
    void testInscriptionDuplicateEmail() {
        InscriptionRequestDTO req = InscriptionRequestDTO.builder()
                .nom("Diop")
                .prenom("Moussa")
                .email("moussa.test@ecourtier.sn")
                .motDePasse("Pass1234!")
                .telephone("+221770001122")
                .role(Role.LOCATAIRE)
                .build();

        when(utilisateurRepository.existsByEmail("moussa.test@ecourtier.sn")).thenReturn(true);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> authService.inscription(req));
        assertEquals(409, ex.getStatusCode().value());
        assertEquals("Un compte existe déjà avec cette adresse email", ex.getReason());
    }

    @Test
    @DisplayName("Inscription publique refusée pour rôle ADMIN ou AGENT (400 BAD_REQUEST)")
    void testInscriptionRestrictedRole() {
        InscriptionRequestDTO reqAdmin = InscriptionRequestDTO.builder()
                .email("admin.fake@ecourtier.sn")
                .motDePasse("Pass1234!")
                .role(Role.ADMIN)
                .build();

        when(utilisateurRepository.existsByEmail("admin.fake@ecourtier.sn")).thenReturn(false);

        ResponseStatusException exAdmin = assertThrows(ResponseStatusException.class,
                () -> authService.inscription(reqAdmin));
        assertEquals(400, exAdmin.getStatusCode().value());

        InscriptionRequestDTO reqAgent = InscriptionRequestDTO.builder()
                .email("agent.fake@ecourtier.sn")
                .motDePasse("Pass1234!")
                .role(Role.AGENT)
                .build();

        when(utilisateurRepository.existsByEmail("agent.fake@ecourtier.sn")).thenReturn(false);

        ResponseStatusException exAgent = assertThrows(ResponseStatusException.class,
                () -> authService.inscription(reqAgent));
        assertEquals(400, exAgent.getStatusCode().value());
    }

    @Test
    @DisplayName("Connexion réussie")
    void testConnexionSuccess() {
        ConnexionRequestDTO req = new ConnexionRequestDTO("moussa.test@ecourtier.sn", "Pass1234!");

        when(utilisateurRepository.findByEmail("moussa.test@ecourtier.sn")).thenReturn(Optional.of(user));
        when(jwtService.generateAccessToken(user)).thenReturn("mock-access-token");
        when(jwtService.generateRefreshToken(eq(user), any())).thenReturn("mock-refresh-token");
        when(jwtService.getAccessTokenExpirationMs()).thenReturn(3600000L);
        when(utilisateurMapper.toResponseDTO(user)).thenReturn(new UtilisateurResponseDTO());

        TokenResponseDTO response = authService.connexion(req);

        assertNotNull(response);
        assertEquals("mock-access-token", response.getAccessToken());
        verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
    }

    @Test
    @DisplayName("Connexion refusée si mot de passe ou email incorrect (401 Anti-Énumération)")
    void testConnexionInvalidCredentials() {
        ConnexionRequestDTO req = new ConnexionRequestDTO("moussa.test@ecourtier.sn", "BadPass123!");

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> authService.connexion(req));
        assertEquals(401, ex.getStatusCode().value());
        assertEquals("Email ou mot de passe incorrect", ex.getReason());
    }

    @Test
    @DisplayName("Connexion refusée si compte désactivé (403 FORBIDDEN)")
    void testConnexionDisabledAccount() {
        ConnexionRequestDTO req = new ConnexionRequestDTO("moussa.test@ecourtier.sn", "Pass1234!");

        // 1. Cas DisabledException dans authenticationManager
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new DisabledException("User is disabled"));

        ResponseStatusException ex1 = assertThrows(ResponseStatusException.class,
                () -> authService.connexion(req));
        assertEquals(403, ex1.getStatusCode().value());
        assertEquals("Le compte utilisateur est désactivé", ex1.getReason());

        // 2. Cas actif = false sur l'entité
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(null);
        user.setActif(false);
        when(utilisateurRepository.findByEmail("moussa.test@ecourtier.sn")).thenReturn(Optional.of(user));

        ResponseStatusException ex2 = assertThrows(ResponseStatusException.class,
                () -> authService.connexion(req));
        assertEquals(403, ex2.getStatusCode().value());
    }

    @Test
    @DisplayName("Rafraîchissement : token invalide/hérité sans jti -> 401, jti inconnu -> 401")
    void testRafraichirTokenInvalideOuHerite() {
        RefreshTokenRequestDTO req = new RefreshTokenRequestDTO("legacy-refresh-token");

        when(jwtService.extractValidRefreshJti("legacy-refresh-token")).thenReturn(null);
        ResponseStatusException exInvalid = assertThrows(ResponseStatusException.class,
                () -> authService.rafraichir(req));
        assertEquals(401, exInvalid.getStatusCode().value());

        UUID jti = UUID.randomUUID();
        when(jwtService.extractValidRefreshJti("legacy-refresh-token")).thenReturn(jti);
        when(refreshTokenRepository.findByJtiForUpdate(jti)).thenReturn(Optional.empty());
        ResponseStatusException exUnknown = assertThrows(ResponseStatusException.class,
                () -> authService.rafraichir(req));
        assertEquals(401, exUnknown.getStatusCode().value());
        verify(refreshTokenRepository, never()).save(any());
    }

    private RefreshTokenEntity storedToken(String rawToken, UUID jti, UUID familyId) {
        return RefreshTokenEntity.builder()
                .id(UUID.randomUUID())
                .utilisateur(user)
                .jti(jti)
                .tokenHash(JwtService.sha256Hex(rawToken))
                .familyId(familyId)
                .expiresAt(LocalDateTime.now().plusDays(7))
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("Rafraîchissement : rotation nominale (ancien consommé, nouveau dans la même famille)")
    void testRafraichirRotationNominale() {
        String raw = "valid-refresh-token";
        UUID jti = UUID.randomUUID();
        UUID familyId = UUID.randomUUID();
        RefreshTokenEntity stored = storedToken(raw, jti, familyId);

        when(jwtService.extractValidRefreshJti(raw)).thenReturn(jti);
        when(refreshTokenRepository.findByJtiForUpdate(jti)).thenReturn(Optional.of(stored));
        when(jwtService.extractEmail(raw)).thenReturn(user.getEmail());
        when(jwtService.generateRefreshToken(eq(user), any())).thenReturn("new-refresh-token");
        when(jwtService.generateAccessToken(user)).thenReturn("new-access-token");
        when(utilisateurMapper.toResponseDTO(user)).thenReturn(new UtilisateurResponseDTO());

        TokenResponseDTO response = authService.rafraichir(new RefreshTokenRequestDTO(raw));

        assertEquals("new-access-token", response.getAccessToken());
        assertEquals("new-refresh-token", response.getRefreshToken());
        assertNotNull(stored.getUsedAt());
        assertNotNull(stored.getReplacedByJti());

        ArgumentCaptor<RefreshTokenEntity> captor = ArgumentCaptor.forClass(RefreshTokenEntity.class);
        verify(refreshTokenRepository, times(2)).save(captor.capture());
        RefreshTokenEntity child = captor.getAllValues().stream()
                .filter(e -> e != stored).findFirst().orElseThrow();
        assertEquals(familyId, child.getFamilyId());
        assertEquals(jti, child.getParentJti());
        assertEquals(stored.getReplacedByJti(), child.getJti());
        assertEquals(JwtService.sha256Hex("new-refresh-token"), child.getTokenHash());
    }

    @Test
    @DisplayName("Rafraîchissement : réutilisation d'un token consommé -> révocation de toute la famille + 401")
    void testRafraichirReutilisationRevoqueFamille() {
        String raw = "used-refresh-token";
        UUID jti = UUID.randomUUID();
        UUID familyId = UUID.randomUUID();
        RefreshTokenEntity stored = storedToken(raw, jti, familyId);
        stored.setUsedAt(LocalDateTime.now().minusMinutes(1));

        when(jwtService.extractValidRefreshJti(raw)).thenReturn(jti);
        when(refreshTokenRepository.findByJtiForUpdate(jti)).thenReturn(Optional.of(stored));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> authService.rafraichir(new RefreshTokenRequestDTO(raw)));
        assertEquals(401, ex.getStatusCode().value());
        verify(refreshTokenRepository).revokeFamily(eq(familyId), any(), eq(AuthService.REVOCATION_REUSE_DETECTED));
        verify(jwtService, never()).generateAccessToken(any());
    }

    @Test
    @DisplayName("Rafraîchissement : utilisateur désactivé -> 403 et famille révoquée")
    void testRafraichirUtilisateurDesactive() {
        String raw = "valid-refresh-token";
        UUID jti = UUID.randomUUID();
        UUID familyId = UUID.randomUUID();
        RefreshTokenEntity stored = storedToken(raw, jti, familyId);
        user.setActif(false);

        when(jwtService.extractValidRefreshJti(raw)).thenReturn(jti);
        when(refreshTokenRepository.findByJtiForUpdate(jti)).thenReturn(Optional.of(stored));
        when(jwtService.extractEmail(raw)).thenReturn(user.getEmail());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> authService.rafraichir(new RefreshTokenRequestDTO(raw)));
        assertEquals(403, ex.getStatusCode().value());
        verify(refreshTokenRepository).revokeFamily(eq(familyId), any(), eq(AuthService.REVOCATION_USER_DISABLED));
    }

    @Test
    @DisplayName("Rafraîchissement : hash ne correspondant pas au jti -> 401 sans rotation")
    void testRafraichirHashDifferent() {
        UUID jti = UUID.randomUUID();
        RefreshTokenEntity stored = storedToken("autre-token", jti, UUID.randomUUID());

        when(jwtService.extractValidRefreshJti("forged-token")).thenReturn(jti);
        when(refreshTokenRepository.findByJtiForUpdate(jti)).thenReturn(Optional.of(stored));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> authService.rafraichir(new RefreshTokenRequestDTO("forged-token")));
        assertEquals(401, ex.getStatusCode().value());
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    @DisplayName("CA3-002 : Rafraîchissement avec token déjà révoqué (non consommé) ou expiré -> 401 sans nouvelle révocation")
    void testRafraichirTokenDejaRevoqueOuExpireSansEffetDeBord() {
        String rawRevoked = "already-revoked-token";
        UUID jtiRevoked = UUID.randomUUID();
        UUID familyId = UUID.randomUUID();
        RefreshTokenEntity revokedEntity = storedToken(rawRevoked, jtiRevoked, familyId);
        revokedEntity.setRevokedAt(LocalDateTime.now().minusMinutes(5));

        when(jwtService.extractValidRefreshJti(rawRevoked)).thenReturn(jtiRevoked);
        when(refreshTokenRepository.findByJtiForUpdate(jtiRevoked)).thenReturn(Optional.of(revokedEntity));

        ResponseStatusException ex1 = assertThrows(ResponseStatusException.class,
                () -> authService.rafraichir(new RefreshTokenRequestDTO(rawRevoked)));
        assertEquals(401, ex1.getStatusCode().value());
        verify(refreshTokenRepository, never()).revokeFamily(any(), any(), any());

        String rawExpired = "expired-db-token";
        UUID jtiExpired = UUID.randomUUID();
        RefreshTokenEntity expiredEntity = storedToken(rawExpired, jtiExpired, familyId);
        expiredEntity.setExpiresAt(LocalDateTime.now().minusMinutes(2));

        when(jwtService.extractValidRefreshJti(rawExpired)).thenReturn(jtiExpired);
        when(refreshTokenRepository.findByJtiForUpdate(jtiExpired)).thenReturn(Optional.of(expiredEntity));

        ResponseStatusException ex2 = assertThrows(ResponseStatusException.class,
                () -> authService.rafraichir(new RefreshTokenRequestDTO(rawExpired)));
        assertEquals(401, ex2.getStatusCode().value());
        verify(refreshTokenRepository, never()).revokeFamily(any(), any(), any());
    }

    @Test
    @DisplayName("CA3-002 : Déconnexion avec un refresh token déjà consommé, révoqué ou expiré -> 401 sans déconnecter la victime")
    void testDeconnecterRejetteAncienRefreshTokenSansEffetDeBord() {
        String rawUsed = "used-token";
        UUID jtiUsed = UUID.randomUUID();
        UUID familyId = UUID.randomUUID();
        RefreshTokenEntity usedEntity = storedToken(rawUsed, jtiUsed, familyId);
        usedEntity.setUsedAt(LocalDateTime.now().minusMinutes(3));

        when(jwtService.extractValidRefreshJti(rawUsed)).thenReturn(jtiUsed);
        when(refreshTokenRepository.findByJtiForUpdate(jtiUsed)).thenReturn(Optional.of(usedEntity));

        ResponseStatusException exUsed = assertThrows(ResponseStatusException.class,
                () -> authService.deconnecter(rawUsed, user.getEmail()));
        assertEquals(401, exUsed.getStatusCode().value());
        verify(refreshTokenRepository, never()).revokeFamily(any(), any(), any());
        verify(utilisateurRepository, never()).incrementTokenVersion(any());
    }
}
