package com.ecourtier.common.security;

import com.ecourtier.domain.utilisateur.entity.UtilisateurEntity;
import com.ecourtier.domain.utilisateur.repository.RefreshTokenRepository;
import com.ecourtier.domain.utilisateur.repository.UtilisateurRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;
    private final UtilisateurRepository utilisateurRepository;

    @Autowired(required = false)
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired(required = false)
    private Clock clock = Clock.system(ZoneId.of("Africa/Dakar"));

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");
        final String jwt;
        final String userEmail;

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        jwt = authHeader.substring(7);
        boolean accountDeactivated = false;
        try {
            if (!jwtService.isAccessTokenValid(jwt)) {
                SecurityContextHolder.clearContext();
            } else {
                userEmail = jwtService.extractEmail(jwt);

                if (userEmail != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                    UserDetails userDetails = this.userDetailsService.loadUserByUsername(userEmail);

                    if (jwtService.isAccessTokenValid(jwt, userDetails.getUsername())) {
                        UtilisateurEntity entity = utilisateurRepository.findByEmail(userEmail).orElse(null);
                        if (entity == null || !entity.isActif()) {
                            accountDeactivated = true;
                            SecurityContextHolder.clearContext();
                        } else {
                            Long tokenVersionClaim = jwtService.extractTokenVersion(jwt);
                            long currentVersion = entity.getTokenVersion() != null ? entity.getTokenVersion() : 0L;
                            UUID familyId = jwtService.extractFamilyId(jwt);
                            LocalDateTime now = LocalDateTime.now(clock != null ? clock : Clock.system(ZoneId.of("Africa/Dakar")));
                            boolean familyRevoked = familyId != null
                                    && refreshTokenRepository != null
                                    && !refreshTokenRepository.isFamilyActive(familyId, entity.getId(), now);

                            if (tokenVersionClaim == null || tokenVersionClaim.longValue() != currentVersion || familyRevoked) {
                                SecurityContextHolder.clearContext();
                            } else {
                                if (familyId != null) {
                                    request.setAttribute("jwtFamilyId", familyId);
                                }
                                UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                                        userDetails,
                                        null,
                                        userDetails.getAuthorities()
                                );
                                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                                SecurityContextHolder.getContext().setAuthentication(authToken);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Token validation or parsing failed, clear context
            SecurityContextHolder.clearContext();
        }

        if (accountDeactivated) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setContentType("application/json;charset=UTF-8");
            String safePath = request.getRequestURI() != null ? request.getRequestURI().replace("\"", "") : "";
            String body = String.format(
                    "{\"timestamp\":\"%s\",\"status\":403,\"error\":\"Le compte utilisateur est désactivé\",\"path\":\"%s\"}",
                    LocalDateTime.now(ZoneId.of("Africa/Dakar")),
                    safePath
            );
            response.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));
            return;
        }

        filterChain.doFilter(request, response);
    }
}
