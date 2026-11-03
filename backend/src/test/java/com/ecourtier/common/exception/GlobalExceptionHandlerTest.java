package com.ecourtier.common.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Method;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("handleAccessDeniedException : utilisateur authentifié -> 403 Forbidden")
    void testAccessDeniedExceptionAuthentifie() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "user@test.sn", "pass", java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER"))));

        ResponseEntity<Map<String, Object>> response = handler.handleAccessDeniedException(
                new AccessDeniedException("Access denied"));

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertTrue(response.getBody().get("error").toString().contains("Accès refusé"));

        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("handleAccessDeniedException : anonyme -> rethrow exception")
    void testAccessDeniedExceptionAnonyme() {
        SecurityContextHolder.clearContext();
        assertThrows(AccessDeniedException.class, () ->
                handler.handleAccessDeniedException(new AccessDeniedException("Access denied")));
    }

    @Test
    @DisplayName("handleValidationExceptions : 400 Bad Request avec détail des erreurs")
    void testValidationExceptions() throws NoSuchMethodException {
        Method method = this.getClass().getDeclaredMethod("testValidationExceptions");
        MethodParameter parameter = new MethodParameter(method, -1);
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "target");
        bindingResult.addError(new FieldError("target", "email", "L'email est invalide"));

        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(parameter, bindingResult);

        ResponseEntity<Map<String, Object>> response = handler.handleValidationExceptions(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody().containsKey("erreurs"));
    }

    @Test
    @DisplayName("handleResponseStatusException : code et message propagés")
    void testResponseStatusException() {
        ResponseStatusException ex = new ResponseStatusException(HttpStatus.NOT_FOUND, "Ressource introuvable");

        ResponseEntity<Map<String, Object>> response = handler.handleResponseStatusException(ex);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("Ressource introuvable", response.getBody().get("error"));
    }

    @Test
    @DisplayName("handleDataIntegrityViolation : 409 Conflict")
    void testDataIntegrityViolation() {
        DataIntegrityViolationException ex = new DataIntegrityViolationException("Duplicate key");

        ResponseEntity<Map<String, Object>> response = handler.handleDataIntegrityViolation(ex);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertTrue(response.getBody().get("error").toString().contains("Conflit"));
    }

    @Test
    @DisplayName("handleDisabledException : 403 Forbidden")
    void testDisabledException() {
        DisabledException ex = new DisabledException("Compte désactivé");

        ResponseEntity<Map<String, Object>> response = handler.handleDisabledException(ex);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertTrue(response.getBody().get("error").toString().contains("désactivé"));
    }

    @Test
    @DisplayName("handleIllegalArgument : 400 Bad Request")
    void testIllegalArgumentException() {
        IllegalArgumentException ex = new IllegalArgumentException("Paramètre invalide");

        ResponseEntity<Map<String, Object>> response = handler.handleIllegalArgument(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Paramètre invalide", response.getBody().get("error"));
    }

    @Test
    @DisplayName("handleTypeMismatch : 400 Bad Request avec nom du paramètre")
    void testTypeMismatchException() {
        MethodArgumentTypeMismatchException ex = new MethodArgumentTypeMismatchException(
                "VALEUR_FAUSSE", String.class, "monParam", null, null);

        ResponseEntity<Map<String, Object>> response = handler.handleTypeMismatch(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody().get("error").toString().contains("monParam"));
    }

    @Test
    @DisplayName("handleNotReadable : 400 Bad Request")
    void testNotReadableException() {
        org.springframework.http.converter.HttpMessageNotReadableException ex =
                new org.springframework.http.converter.HttpMessageNotReadableException("JSON parse error", (org.springframework.http.HttpInputMessage) null);

        ResponseEntity<Map<String, Object>> response = handler.handleNotReadable(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody().get("error").toString().contains("Format de requête"));
    }

    @Test
    @DisplayName("handleMaxUploadSizeExceeded : 400 Bad Request")
    void testMaxUploadSizeExceeded() {
        MaxUploadSizeExceededException ex = new MaxUploadSizeExceededException(15000000);

        ResponseEntity<Map<String, Object>> response = handler.handleMaxUploadSizeExceeded(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody().get("error").toString().contains("taille"));
    }
}
