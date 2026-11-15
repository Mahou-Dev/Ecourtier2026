package com.ecourtier.domain.utilisateur.service;

import com.ecourtier.common.security.JwtService;
import com.ecourtier.common.security.WebSocketSessionRegistry;
import com.ecourtier.domain.utilisateur.dto.ConnexionRequestDTO;
import com.ecourtier.domain.utilisateur.dto.InscriptionRequestDTO;
import com.ecourtier.domain.utilisateur.dto.RefreshTokenRequestDTO;
import com.ecourtier.domain.utilisateur.dto.TokenResponseDTO;
import com.ecourtier.domain.utilisateur.entity.RefreshTokenEntity;
import com.ecourtier.domain.utilisateur.entity.Role;
import com.ecourtier.domain.utilisateur.entity.UtilisateurEntity;
import com.ecourtier.domain.utilisateur.mapper.UtilisateurMapper;
import com.ecourtier.domain.utilisateur.repository.RefreshTokenRepository;
import com.ecourtier.domain.utilisateur.repository.UtilisateurRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    public static final String REVOCATION_REUSE_DETECTED = "REUSE_DETECTED";
    public static final String REVOCATION_LOGOUT = "LOGOUT";
    public static final String REVOCATION_USER_DISABLED = "USER_DISABLED";
    public static final String REVOCATION_PASSWORD_CHANGED = "PASSWORD_CHANGED";
    public static final String REVOCATION_ROLE_CHANGED = "ROLE_CHANGED";

    private final UtilisateurRepository utilisateurRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final UtilisateurMapper utilisateurMapper;
    private final AuthenticationManager authenticationManager;
    private final RefreshTokenRepository refreshTokenRepository;

    @Autowired(required = false)
    private Clock clock = Clock.system(ZoneId.of("Africa/Dakar"));

    @Autowired(required = false)
    private WebSocketSessionRegistry webSocketSessionRegistry;

    private LocalDateTime now() {
        return LocalDateTime.now(clock != null ? clock : Clock.system(ZoneId.of("Africa/Dakar")));
    }

    /**
     * Émet un refresh token signé (jti unique) et persiste uniquement son empreinte SHA-256.
     */
    private String emettreRefreshToken(UtilisateurEntity user, UUID familyId, UUID parentJti) {
        return emettreRefreshToken(user, familyId, parentJti, UUID.randomUUID());
    }

    private String emettreRefreshToken(UtilisateurEntity user, UUID familyId, UUID parentJti, UUID jti) {
        String refreshToken = jwtService.generateRefreshToken(user, jti);
        RefreshTokenEntity entity = RefreshTokenEntity.builder()
                .utilisateur(user)
                .jti(jti)
                .tokenHash(JwtService.sha256Hex(refreshToken != null ? refreshToken : jti.toString()))
                .familyId(familyId)
                .parentJti(parentJti)
                .expiresAt(now().plus(Duration.ofMillis(jwtService.getRefreshTokenExpirationMs())))
                .createdAt(now())
                .build();
        refreshTokenRepository.save(entity);
        return refreshToken;
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    private String generateAccessTokenWithFamily(UtilisateurEntity user, UUID familyId) {
        String tokenWithFamily = jwtService.generateSessionAccessToken(user, familyId);
        return tokenWithFamily != null ? tokenWithFamily : jwtService.generateAccessToken(user);
    }

    @Transactional
    public TokenResponseDTO inscription(InscriptionRequestDTO request) {
        String emailClean = request.getEmail().toLowerCase().trim();

        // Pre-check for duplicate email in database
        if (utilisateurRepository.existsByEmail(emailClean)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Un compte existe déjà avec cette adresse email");
        }

        // Restrict public registration to PROPRIETAIRE or LOCATAIRE only
        if (request.getRole() == Role.ADMIN || request.getRole() == Role.AGENT) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Les comptes ADMIN et AGENT ne peuvent pas être créés via l'inscription publique"
            );
        }

        UtilisateurEntity user = UtilisateurEntity.builder()
                .nom(request.getNom())
                .prenom(request.getPrenom())
                .email(emailClean)
                .motDePasse(passwordEncoder.encode(request.getMotDePasse()))
                .telephone(request.getTelephone())
                .role(request.getRole())
                .actif(true)
                .build();

        UtilisateurEntity savedUser = utilisateurRepository.save(user);

        UUID familyId = UUID.randomUUID();
        String accessToken = generateAccessTokenWithFamily(savedUser, familyId);
        String refreshToken = emettreRefreshToken(savedUser, familyId, null);

        return TokenResponseDTO.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtService.getAccessTokenExpirationMs() / 1000)
                .utilisateur(utilisateurMapper.toResponseDTO(savedUser))
                .build();
    }

    @Transactional
    public TokenResponseDTO connexion(ConnexionRequestDTO request) {
        String emailClean = request.getEmail().toLowerCase().trim();

        // 1. Authenticate credentials (email + password) FIRST to prevent account/status enumeration
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(emailClean, request.getMotDePasse())
            );
        } catch (DisabledException e) {
            // Credentials were valid, but account is disabled
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Le compte utilisateur est désactivé");
        } catch (Exception e) {
            // Invalid email or password -> generic 401 Unauthorized
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Email ou mot de passe incorrect");
        }

        // 2. Credentials valid -> retrieve user entity
        UtilisateurEntity user = utilisateurRepository.findByEmail(emailClean)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Email ou mot de passe incorrect"));

        // 3. Double-check active status as defense-in-depth
        if (!user.isActif()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Le compte utilisateur est désactivé");
        }

        UUID familyId = UUID.randomUUID();
        String accessToken = generateAccessTokenWithFamily(user, familyId);
        String refreshToken = emettreRefreshToken(user, familyId, null);

        return TokenResponseDTO.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtService.getAccessTokenExpirationMs() / 1000)
                .utilisateur(utilisateurMapper.toResponseDTO(user))
                .build();
    }

    /**
     * Rotation stricte du refresh token :
     * - le token doit être signé, non expiré, de type REFRESH, avec iss/aud valides ET porter un jti ;
     * - le jti doit exister en base (les tokens hérités sans persistance sont refusés en 401) ;
     * - la ligne est verrouillée (PESSIMISTIC_WRITE) pour sérialiser les rafraîchissements concurrents ;
     * - toute réutilisation d'un token déjà consommé révoque l'intégralité de la famille (access + refresh + WS).
     * noRollbackFor garantit que la révocation de famille est bien validée malgré l'exception 401 renvoyée.
     */
    @Transactional(noRollbackFor = ResponseStatusException.class)
    public TokenResponseDTO rafraichir(RefreshTokenRequestDTO request) {
        String token = request.getRefreshToken();
        UUID jti = jwtService.extractValidRefreshJti(token);
        if (jti == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token invalide ou expiré");
        }

        RefreshTokenEntity stored = refreshTokenRepository.findByJtiForUpdate(jti)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token invalide ou expiré"));

        if (!constantTimeEquals(JwtService.sha256Hex(token), stored.getTokenHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token invalide ou expiré");
        }

        LocalDateTime now = now();
        if (stored.getExpiresAt() == null || !stored.getExpiresAt().isAfter(now)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token invalide ou expiré");
        }

        if (stored.getUsedAt() != null) {
            int revoked = refreshTokenRepository.revokeFamily(stored.getFamilyId(), now, REVOCATION_REUSE_DETECTED);
            disconnectFamilyWsAfterCommit(stored.getFamilyId());
            log.warn("[AUTH] Réutilisation d'un refresh token consommé détectée (famille {}), {} jeton(s) révoqué(s)",
                    stored.getFamilyId(), revoked);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token déjà utilisé ou révoqué : session invalidée");
        }

        if (stored.getRevokedAt() != null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token déjà utilisé ou révoqué : session invalidée");
        }

        UtilisateurEntity user = stored.getUtilisateur();
        String subject = jwtService.extractEmail(token);
        if (user == null || subject == null || !subject.equalsIgnoreCase(user.getEmail())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token invalide ou expiré");
        }

        if (!user.isActif()) {
            refreshTokenRepository.revokeFamily(stored.getFamilyId(), now, REVOCATION_USER_DISABLED);
            disconnectFamilyWsAfterCommit(stored.getFamilyId());
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Le compte utilisateur est désactivé");
        }

        UUID childJti = UUID.randomUUID();
        String newRefreshToken = emettreRefreshToken(user, stored.getFamilyId(), stored.getJti(), childJti);
        stored.setReplacedByJti(childJti);
        stored.setUsedAt(now);
        refreshTokenRepository.save(stored);

        String newAccessToken = generateAccessTokenWithFamily(user, stored.getFamilyId());

        return TokenResponseDTO.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtService.getAccessTokenExpirationMs() / 1000)
                .utilisateur(utilisateurMapper.toResponseDTO(user))
                .build();
    }

    /**
     * Déconnexion serveur : révoque uniquement la session ciblée si le refresh token fourni est
     * valide, actif, non expiré et non consommé, ou la session courante de l'access token authentifié.
     * Un refresh token expiré, déjà révoqué ou déjà consommé est rejeté en 401 sans effet de bord (anti-DoS).
     */
    @Transactional
    public void deconnecter(String refreshToken, String authenticatedEmail) {
        deconnecter(refreshToken, authenticatedEmail, null);
    }

    @Transactional
    public void deconnecter(String refreshToken, String authenticatedEmail, UUID authenticatedFamilyId) {
        LocalDateTime now = now();
        if (refreshToken != null && !refreshToken.isBlank()) {
            String trimmedToken = refreshToken.trim();
            UUID jti = jwtService.extractValidRefreshJti(trimmedToken);
            if (jti == null) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token invalide ou expiré");
            }

            RefreshTokenEntity stored = refreshTokenRepository.findByJtiForUpdate(jti)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token inconnu"));

            if (!constantTimeEquals(JwtService.sha256Hex(trimmedToken), stored.getTokenHash())) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token inconnu");
            }

            if (authenticatedEmail != null && stored.getUtilisateur() != null
                    && !authenticatedEmail.equalsIgnoreCase(stored.getUtilisateur().getEmail())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Ce refresh token n'appartient pas à l'utilisateur connecté");
            }

            if (stored.getExpiresAt() == null || !stored.getExpiresAt().isAfter(now)
                    || stored.getRevokedAt() != null || stored.getUsedAt() != null) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token expiré, déjà utilisé ou révoqué");
            }

            refreshTokenRepository.revokeFamily(stored.getFamilyId(), now, REVOCATION_LOGOUT);
            disconnectFamilyWsAfterCommit(stored.getFamilyId());
            if (stored.getUtilisateur() != null
                    && !refreshTokenRepository.existsActiveByUtilisateurId(stored.getUtilisateur().getId(), now)) {
                utilisateurRepository.incrementTokenVersion(stored.getUtilisateur().getId());
            }
            return;
        }

        if (authenticatedEmail == null || authenticatedEmail.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Aucune session à révoquer");
        }

        UtilisateurEntity user = utilisateurRepository.findByEmail(authenticatedEmail.toLowerCase().trim())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Utilisateur non trouvé"));

        if (authenticatedFamilyId != null) {
            refreshTokenRepository.revokeFamily(authenticatedFamilyId, now, REVOCATION_LOGOUT);
            disconnectFamilyWsAfterCommit(authenticatedFamilyId);
            if (!refreshTokenRepository.existsActiveByUtilisateurId(user.getId(), now)) {
                utilisateurRepository.incrementTokenVersion(user.getId());
            }
            return;
        }

        refreshTokenRepository.revokeAllActiveByUtilisateurId(user.getId(), now, REVOCATION_LOGOUT);
        revokeAccessTokensAndDisconnectWs(user.getId(), user.getEmail());
    }

    private void disconnectFamilyWsAfterCommit(UUID familyId) {
        if (webSocketSessionRegistry != null && familyId != null) {
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        webSocketSessionRegistry.disconnectFamily(familyId);
                    }
                });
            } else {
                webSocketSessionRegistry.disconnectFamily(familyId);
            }
        }
    }

    private void revokeAccessTokensAndDisconnectWs(UUID userId, String email) {
        if (userId != null) {
            utilisateurRepository.incrementTokenVersion(userId);
        }
        if (webSocketSessionRegistry != null && email != null && !email.isBlank()) {
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        webSocketSessionRegistry.disconnectUser(email);
                    }
                });
            } else {
                webSocketSessionRegistry.disconnectUser(email);
            }
        }
    }
}
