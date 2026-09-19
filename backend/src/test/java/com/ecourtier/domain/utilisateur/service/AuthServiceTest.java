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

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
        when(jwtService.generateRefreshToken(any())).thenReturn("mock-refresh-token");
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
        when(jwtService.generateRefreshToken(user)).thenReturn("mock-refresh-token");
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
    @DisplayName("Rafraîchissement de jeton (Refresh Token) réussi et cas d'erreurs")
    void testRafraichirToken() {
        RefreshTokenRequestDTO req = new RefreshTokenRequestDTO("mock-refresh-token");

        // 1. Token non valide
        when(jwtService.isRefreshToken("mock-refresh-token")).thenReturn(false);
        ResponseStatusException exInvalid = assertThrows(ResponseStatusException.class,
                () -> authService.rafraichir(req));
        assertEquals(401, exInvalid.getStatusCode().value());

        // 2. Token valide mais utilisateur inactif
        when(jwtService.isRefreshToken("mock-refresh-token")).thenReturn(true);
        when(jwtService.extractEmail("mock-refresh-token")).thenReturn("moussa.test@ecourtier.sn");
        user.setActif(false);
        when(utilisateurRepository.findByEmail("moussa.test@ecourtier.sn")).thenReturn(Optional.of(user));

        ResponseStatusException exDisabled = assertThrows(ResponseStatusException.class,
                () -> authService.rafraichir(req));
        assertEquals(403, exDisabled.getStatusCode().value());

        // 3. Cas nominal réussi
        user.setActif(true);
        when(jwtService.generateAccessToken(user)).thenReturn("new-access-token");
        when(jwtService.generateRefreshToken(user)).thenReturn("new-refresh-token");
        when(jwtService.getAccessTokenExpirationMs()).thenReturn(3600000L);
        when(utilisateurMapper.toResponseDTO(user)).thenReturn(new UtilisateurResponseDTO());

        TokenResponseDTO response = authService.rafraichir(req);
        assertNotNull(response);
        assertEquals("new-access-token", response.getAccessToken());
    }
}
