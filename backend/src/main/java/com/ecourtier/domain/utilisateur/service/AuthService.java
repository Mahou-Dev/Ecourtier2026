package com.ecourtier.domain.utilisateur.service;

import com.ecourtier.common.security.JwtService;
import com.ecourtier.domain.utilisateur.dto.ConnexionRequestDTO;
import com.ecourtier.domain.utilisateur.dto.InscriptionRequestDTO;
import com.ecourtier.domain.utilisateur.dto.RefreshTokenRequestDTO;
import com.ecourtier.domain.utilisateur.dto.TokenResponseDTO;
import com.ecourtier.domain.utilisateur.entity.Role;
import com.ecourtier.domain.utilisateur.entity.UtilisateurEntity;
import com.ecourtier.domain.utilisateur.mapper.UtilisateurMapper;
import com.ecourtier.domain.utilisateur.repository.UtilisateurRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UtilisateurRepository utilisateurRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final UtilisateurMapper utilisateurMapper;
    private final AuthenticationManager authenticationManager;

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

        String accessToken = jwtService.generateAccessToken(savedUser);
        String refreshToken = jwtService.generateRefreshToken(savedUser);

        return TokenResponseDTO.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtService.getAccessTokenExpirationMs() / 1000)
                .utilisateur(utilisateurMapper.toResponseDTO(savedUser))
                .build();
    }

    @Transactional(readOnly = true)
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

        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = jwtService.generateRefreshToken(user);

        return TokenResponseDTO.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtService.getAccessTokenExpirationMs() / 1000)
                .utilisateur(utilisateurMapper.toResponseDTO(user))
                .build();
    }

    @Transactional(readOnly = true)
    public TokenResponseDTO rafraichir(RefreshTokenRequestDTO request) {
        String token = request.getRefreshToken();
        if (!jwtService.isRefreshToken(token)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token invalide ou expiré");
        }

        String email = jwtService.extractEmail(token);
        UtilisateurEntity user = utilisateurRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Utilisateur non trouvé"));

        if (!user.isActif()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Le compte utilisateur est désactivé");
        }

        String newAccessToken = jwtService.generateAccessToken(user);
        String newRefreshToken = jwtService.generateRefreshToken(user);

        return TokenResponseDTO.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtService.getAccessTokenExpirationMs() / 1000)
                .utilisateur(utilisateurMapper.toResponseDTO(user))
                .build();
    }
}
