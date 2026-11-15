package com.ecourtier.common.security;

import com.ecourtier.domain.utilisateur.entity.UtilisateurEntity;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

@Service
public class JwtService {

    @Value("${ecourtier.jwt.secret:}")
    private String secretKey;

    @Value("${ecourtier.jwt.access-token-expiration-ms:3600000}")
    private long accessTokenExpirationMs;

    @Value("${ecourtier.jwt.refresh-token-expiration-ms:604800000}")
    private long refreshTokenExpirationMs;

    @Value("${ecourtier.jwt.issuer:ecourtier-api}")
    private String issuer = "ecourtier-api";

    @Value("${ecourtier.jwt.audience:ecourtier-client}")
    private String audience = "ecourtier-client";

    private static final ZoneId DAKAR_ZONE = ZoneId.of("Africa/Dakar");

    @Autowired(required = false)
    private Clock clock = Clock.system(DAKAR_ZONE);

    void setClock(Clock clock) {
        this.clock = clock != null ? clock : Clock.system(DAKAR_ZONE);
    }

    private Date nowDate() {
        return Date.from((clock != null ? clock : Clock.system(DAKAR_ZONE)).instant());
    }

    @PostConstruct
    public void validateSecretKey() {
        resolveSigningKeyBytes(this.secretKey);
    }

    public String generateAccessToken(UtilisateurEntity user) {
        return generateAccessToken(user, null);
    }

    public String generateSessionAccessToken(UtilisateurEntity user, UUID familyId) {
        return generateAccessToken(user, familyId);
    }

    public String generateAccessToken(UtilisateurEntity user, UUID familyId) {
        Map<String, Object> extraClaims = new HashMap<>();
        extraClaims.put("userId", user.getId().toString());
        extraClaims.put("role", user.getRole().name());
        extraClaims.put("type", "ACCESS");
        extraClaims.put("tokenVersion", user.getTokenVersion() != null ? user.getTokenVersion() : 0L);
        if (familyId != null) {
            extraClaims.put("familyId", familyId.toString());
        }
        return buildToken(extraClaims, user.getEmail(), accessTokenExpirationMs, UUID.randomUUID());
    }

    public String generateRefreshToken(UtilisateurEntity user) {
        return generateRefreshToken(user, UUID.randomUUID());
    }

    /**
     * Génère un refresh token portant un identifiant unique (jti) imposé,
     * destiné à être persisté (sous forme de hash SHA-256) dans la table refresh_tokens.
     */
    public String generateRefreshToken(UtilisateurEntity user, UUID jti) {
        Map<String, Object> extraClaims = new HashMap<>();
        extraClaims.put("userId", user.getId().toString());
        extraClaims.put("role", user.getRole().name());
        extraClaims.put("type", "REFRESH");
        return buildToken(extraClaims, user.getEmail(), refreshTokenExpirationMs, jti);
    }

    private String buildToken(Map<String, Object> extraClaims, String subject, long expirationMs, UUID jti) {
        Date now = nowDate();
        Date expiryDate = new Date(now.getTime() + expirationMs);

        return Jwts.builder()
                .claims(extraClaims)
                .id(jti.toString())
                .issuer(issuer != null ? issuer.trim() : null)
                .audience().add(audience != null ? audience.trim() : null).and()
                .subject(subject)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * Valide strictement un refresh token (signature, expiration, subject, type REFRESH, iss, aud)
     * et retourne son jti. Retourne null si le token est invalide ou s'il ne porte pas de jti (token hérité).
     */
    public UUID extractValidRefreshJti(String token) {
        Claims claims = parseValidClaims(token);
        if (claims == null || !TOKEN_TYPE_REFRESH.equals(claims.get("type", String.class))) {
            return null;
        }
        String jti = claims.getId();
        if (jti == null || jti.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(jti);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public String extractJti(String token) {
        return extractClaim(token, Claims::getId);
    }

    public LocalDateTime extractExpirationLocalDateTime(String token) {
        Date expiration = extractExpiration(token);
        return LocalDateTime.ofInstant(expiration.toInstant(), DAKAR_ZONE);
    }

    public static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponible", e);
        }
    }

    public long getRefreshTokenExpirationMs() {
        return refreshTokenExpirationMs;
    }

    public String extractEmail(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public String extractRole(String token) {
        return extractClaim(token, claims -> claims.get("role", String.class));
    }

    public UUID extractUserId(String token) {
        String userIdStr = extractClaim(token, claims -> claims.get("userId", String.class));
        return userIdStr != null ? UUID.fromString(userIdStr) : null;
    }

    public String extractTokenType(String token) {
        return extractClaim(token, claims -> claims.get("type", String.class));
    }

    public static final String TOKEN_TYPE_ACCESS = "ACCESS";
    public static final String TOKEN_TYPE_REFRESH = "REFRESH";

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    public boolean isAccessTokenValid(String token, String expectedEmail) {
        if (expectedEmail == null || expectedEmail.isBlank()) {
            return false;
        }
        Claims claims = parseValidClaims(token);
        return claims != null
                && TOKEN_TYPE_ACCESS.equals(claims.get("type", String.class))
                && expectedEmail.equals(claims.getSubject());
    }

    public boolean isAccessTokenValid(String token) {
        Claims claims = parseValidClaims(token);
        return claims != null
                && TOKEN_TYPE_ACCESS.equals(claims.get("type", String.class));
    }

    public boolean isTokenValid(String token, String expectedEmail) {
        return isAccessTokenValid(token, expectedEmail);
    }

    public boolean isTokenValid(String token) {
        return isAccessTokenValid(token);
    }

    public boolean isRefreshToken(String token) {
        return extractValidRefreshJti(token) != null;
    }

    public boolean isTokenExpired(String token) {
        return extractExpiration(token).before(nowDate());
    }

    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    public Long extractTokenVersion(String token) {
        Claims claims = parseValidClaims(token);
        if (claims == null) {
            return null;
        }
        Object raw = claims.get("tokenVersion");
        if (raw instanceof Number number) {
            return number.longValue();
        }
        if (raw instanceof String str) {
            try {
                return Long.parseLong(str.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    public UUID extractFamilyId(String token) {
        Claims claims = parseValidClaims(token);
        if (claims == null) {
            return null;
        }
        String raw = claims.get("familyId", String.class);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private Claims parseValidClaims(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            Claims claims = extractAllClaims(token);
            String subject = claims.getSubject();
            String jti = claims.getId();
            Date expiration = claims.getExpiration();
            if (subject == null || subject.isBlank()
                    || jti == null || jti.isBlank()
                    || expiration == null || expiration.before(nowDate())) {
                return null;
            }
            if (issuer != null && !issuer.isBlank() && !issuer.trim().equals(claims.getIssuer())) {
                return null;
            }
            if (audience != null && !audience.isBlank()
                    && (claims.getAudience() == null || !claims.getAudience().contains(audience.trim()))) {
                return null;
            }
            return claims;
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .clock(() -> nowDate())
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(resolveSigningKeyBytes(this.secretKey));
    }

    static byte[] resolveSigningKeyBytes(String rawSecret) {
        if (rawSecret == null || rawSecret.isBlank()) {
            throw new IllegalStateException(
                    "Démarrage refusé : la variable JWT_SECRET (ecourtier.jwt.secret) est obligatoire et ne peut pas être vide."
            );
        }
        String trimmed = rawSecret.trim();
        byte[] keyBytes;
        try {
            keyBytes = Decoders.BASE64.decode(trimmed);
        } catch (Exception ex) {
            keyBytes = trimmed.getBytes(StandardCharsets.UTF_8);
        }
        if (keyBytes.length < 32) {
            throw new IllegalStateException(
                    "Démarrage refusé : JWT_SECRET doit représenter au moins 256 bits (32 octets) pour HMAC-SHA256."
            );
        }
        return keyBytes;
    }

    public long getAccessTokenExpirationMs() {
        return accessTokenExpirationMs;
    }
}
