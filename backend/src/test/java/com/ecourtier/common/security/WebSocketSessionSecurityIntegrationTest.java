package com.ecourtier.common.security;

import com.ecourtier.domain.annonce.entity.AnnonceEntity;
import com.ecourtier.domain.annonce.entity.ModeTransaction;
import com.ecourtier.domain.annonce.entity.StatutPublication;
import com.ecourtier.domain.annonce.entity.StatutVerification;
import com.ecourtier.domain.annonce.entity.TypeBien;
import com.ecourtier.domain.annonce.repository.AnnonceRepository;
import com.ecourtier.domain.messagerie.dto.MessageResponseDTO;
import com.ecourtier.domain.messagerie.dto.MessageSendDTO;
import com.ecourtier.domain.messagerie.entity.ConversationChatEntity;
import com.ecourtier.domain.messagerie.repository.ConversationChatRepository;
import com.ecourtier.domain.messagerie.repository.MessageChatRepository;
import com.ecourtier.domain.utilisateur.dto.ChangeStatutRequestDTO;
import com.ecourtier.domain.utilisateur.dto.ChangerMotDePasseRequestDTO;
import com.ecourtier.domain.utilisateur.dto.ConnexionRequestDTO;
import com.ecourtier.domain.utilisateur.dto.DeconnexionRequestDTO;
import com.ecourtier.domain.utilisateur.dto.TokenResponseDTO;
import com.ecourtier.domain.utilisateur.entity.Role;
import com.ecourtier.domain.utilisateur.entity.UtilisateurEntity;
import com.ecourtier.domain.utilisateur.repository.RefreshTokenRepository;
import com.ecourtier.domain.utilisateur.repository.UtilisateurRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class WebSocketSessionSecurityIntegrationTest {

    private static final ZoneId DAKAR_ZONE = ZoneId.of("Africa/Dakar");

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private AnnonceRepository annonceRepository;

    @Autowired
    private ConversationChatRepository conversationChatRepository;

    @Autowired
    private MessageChatRepository messageChatRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private WebSocketSessionRegistry webSocketSessionRegistry;

    @Autowired
    private WebSocketAuthInterceptor webSocketAuthInterceptor;

    private UtilisateurEntity proprio;
    private UtilisateurEntity locataire;
    private UtilisateurEntity tiers;
    private UtilisateurEntity admin;
    private AnnonceEntity annonce;
    private ConversationChatEntity conversation;
    private final String password = "Password123!";

    @BeforeEach
    void setUp() {
        webSocketAuthInterceptor.setClock(Clock.system(DAKAR_ZONE));
        webSocketSessionRegistry.setClock(Clock.system(DAKAR_ZONE));

        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String phoneSeed = String.valueOf(Math.abs(suffix.hashCode() % 10000000));

        proprio = utilisateurRepository.save(UtilisateurEntity.builder()
                .nom("Proprio").prenom("WS").email("ws.proprio." + suffix + "@ecourtier.sn")
                .motDePasse(passwordEncoder.encode(password)).telephone("+221774" + phoneSeed)
                .role(Role.PROPRIETAIRE).actif(true).build());
        locataire = utilisateurRepository.save(UtilisateurEntity.builder()
                .nom("Locataire").prenom("WS").email("ws.loc." + suffix + "@ecourtier.sn")
                .motDePasse(passwordEncoder.encode(password)).telephone("+221775" + phoneSeed)
                .role(Role.LOCATAIRE).actif(true).build());
        tiers = utilisateurRepository.save(UtilisateurEntity.builder()
                .nom("Tiers").prenom("WS").email("ws.tiers." + suffix + "@ecourtier.sn")
                .motDePasse(passwordEncoder.encode(password)).telephone("+221776" + phoneSeed)
                .role(Role.LOCATAIRE).actif(true).build());
        admin = utilisateurRepository.save(UtilisateurEntity.builder()
                .nom("Admin").prenom("WS").email("ws.admin." + suffix + "@ecourtier.sn")
                .motDePasse(passwordEncoder.encode(password)).telephone("+221777" + phoneSeed)
                .role(Role.ADMIN).actif(true).build());

        annonce = annonceRepository.save(AnnonceEntity.builder()
                .titre("Appartement WS Security")
                .description("Test sécurité session STOMP")
                .type(TypeBien.APPARTEMENT)
                .modeTransaction(ModeTransaction.LOCATION)
                .prix(BigDecimal.valueOf(300000))
                .superficie(95.0)
                .nombrePieces(3)
                .quartier("Mermoz")
                .ville("Dakar")
                .latitude(14.70)
                .longitude(-17.47)
                .statutVerification(StatutVerification.VERIFIEE)
                .statutPublication(StatutPublication.DISPONIBLE)
                .supprime(false)
                .utilisateur(proprio)
                .build());

        conversation = conversationChatRepository.save(ConversationChatEntity.builder()
                .annonce(annonce)
                .locataire(locataire)
                .proprietaire(proprio)
                .build());
    }

    @AfterEach
    void tearDown() {
        webSocketAuthInterceptor.setClock(Clock.system(DAKAR_ZONE));
        webSocketSessionRegistry.setClock(Clock.system(DAKAR_ZONE));
        messageChatRepository.deleteAll(messageChatRepository.findAll().stream()
                .filter(m -> m.getConversation() != null && conversation.getId().equals(m.getConversation().getId()))
                .toList());
        conversationChatRepository.deleteById(conversation.getId());
        annonceRepository.deleteById(annonce.getId());
        for (UtilisateurEntity u : List.of(proprio, locataire, tiers, admin)) {
            refreshTokenRepository.deleteAll(refreshTokenRepository.findAll().stream()
                    .filter(rt -> rt.getUtilisateur() != null && u.getId().equals(rt.getUtilisateur().getId()))
                    .toList());
            utilisateurRepository.findById(u.getId()).ifPresent(utilisateurRepository::delete);
        }
    }

    private WebSocketStompClient createStompClient() {
        WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
        MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        converter.setObjectMapper(mapper);
        client.setMessageConverter(converter);
        return client;
    }

    private TokenResponseDTO loginRest(String email, String pwd) {
        ResponseEntity<TokenResponseDTO> res = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/v1/auth/connexion",
                new ConnexionRequestDTO(email, pwd),
                TokenResponseDTO.class
        );
        assertEquals(HttpStatus.OK, res.getStatusCode());
        return res.getBody();
    }

    private void awaitCondition(BooleanSupplierWithThrows check, long timeoutMs) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (check.getAsBoolean()) {
                return;
            }
            Thread.sleep(50);
        }
        assertTrue(check.getAsBoolean(), "Condition asynchrone non satisfaite dans le délai imparti");
    }

    @FunctionalInterface
    private interface BooleanSupplierWithThrows {
        boolean getAsBoolean() throws Exception;
    }

    private StompSession connectSession(String accessToken, CountDownLatch errorOrDisconnectLatch) throws Exception {
        WebSocketStompClient client = createStompClient();
        StompHeaders headers = new StompHeaders();
        headers.add("Authorization", "Bearer " + accessToken);
        return client.connectAsync(
                "ws://localhost:" + port + "/ws",
                new WebSocketHttpHeaders(),
                headers,
                new StompSessionHandlerAdapter() {
                    @Override
                    public void handleTransportError(StompSession session, Throwable exception) {
                        if (errorOrDisconnectLatch != null) {
                            errorOrDisconnectLatch.countDown();
                        }
                    }

                    @Override
                    public void handleException(StompSession session, StompCommand command, StompHeaders headers, byte[] payload, Throwable exception) {
                        if (errorOrDisconnectLatch != null) {
                            errorOrDisconnectLatch.countDown();
                        }
                    }
                }
        ).get(5, TimeUnit.SECONDS);
    }

    private StompFrameHandler queueHandler(BlockingQueue<MessageResponseDTO> queue) {
        return new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return MessageResponseDTO.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                if (payload instanceof MessageResponseDTO msg) {
                    queue.add(msg);
                }
            }
        };
    }

    @Test
    @DisplayName("Phase 3 D + A1: Connexion STOMP valide, blocage passif à l'expiration JWT, fermeture après SEND expiré et nettoyage registre")
    void testValidSessionPassiveExpiryBlocksOutboundAndSweepAndRegistryCleanup() throws Exception {
        String tokenProprio = jwtService.generateAccessToken(proprio);
        String tokenLocataire = jwtService.generateAccessToken(locataire);
        String topic = "/topic/conversations/" + conversation.getId();
        String sendDest = "/app/conversations/" + conversation.getId() + "/envoyer";

        StompSession sessionProprio = connectSession(tokenProprio, null);
        CountDownLatch locTransportClosed = new CountDownLatch(1);
        StompSession sessionLoc = connectSession(tokenLocataire, locTransportClosed);

        BlockingQueue<MessageResponseDTO> locMessages = new LinkedBlockingQueue<>();
        sessionLoc.subscribe(topic, queueHandler(locMessages));
        Thread.sleep(150);

        // 1. Flux nominal avec JWT valide
        sessionProprio.send(sendDest, new MessageSendDTO("Bonjour valide"));
        MessageResponseDTO received = locMessages.poll(4, TimeUnit.SECONDS);
        assertNotNull(received);
        assertEquals("Bonjour valide", received.getContenu());
        assertEquals(1, webSocketSessionRegistry.getActiveSessionCount(locataire.getEmail()));

        // 2. Avancer les horloges WS de +2h -> SEND et SUBSCRIBE avec JWT expiré sont rejetés et ferment la session
        Clock futureClock = Clock.fixed(Instant.now().plusSeconds(7200), DAKAR_ZONE);
        webSocketAuthInterceptor.setClock(futureClock);
        webSocketSessionRegistry.setClock(futureClock);

        sessionProprio.send(sendDest, new MessageSendDTO("Message envoyé avec JWT expiré"));
        sessionLoc.subscribe("/topic/conversations/" + conversation.getId(), queueHandler(locMessages));
        assertNull(locMessages.poll(600, TimeUnit.MILLISECONDS),
                "Un SEND ou SUBSCRIBE après expiration JWT ne doit jamais être traité ni diffusé");
        awaitCondition(() -> webSocketSessionRegistry.getActiveSessionCount(proprio.getEmail()) == 0, 3000);
        awaitCondition(() -> webSocketSessionRegistry.getActiveSessionCount(locataire.getEmail()) == 0, 3000);

        // 3. Reconnecter A et B sous horloge normale puis tester le sweep périodique sur session passive expirée
        webSocketAuthInterceptor.setClock(Clock.system(DAKAR_ZONE));
        webSocketSessionRegistry.setClock(Clock.system(DAKAR_ZONE));

        StompSession sessionProprio2 = connectSession(tokenProprio, null);
        StompSession sessionLocPassive = connectSession(tokenLocataire, new CountDownLatch(1));
        BlockingQueue<MessageResponseDTO> passiveQueue = new LinkedBlockingQueue<>();
        sessionLocPassive.subscribe(topic, queueHandler(passiveQueue));
        Thread.sleep(150);
        assertEquals(1, webSocketSessionRegistry.getActiveSessionCount(locataire.getEmail()));

        // Client normal A se déconnecte proprement -> nettoyage du registre pour proprio
        sessionProprio2.disconnect();
        awaitCondition(() -> webSocketSessionRegistry.getActiveSessionCount(proprio.getEmail()) == 0, 3000);

        // Avancer l'horloge WS de 2 heures et déclencher le sweep (simulant le @Scheduled)
        webSocketAuthInterceptor.setClock(futureClock);
        webSocketSessionRegistry.setClock(futureClock);
        int swept = webSocketSessionRegistry.sweepExpiredOrRevokedSessions();
        assertTrue(swept >= 1, "Le sweep doit fermer la session passive expirée");
        awaitCondition(() -> webSocketSessionRegistry.getActiveSessionCount(locataire.getEmail()) == 0, 3000);
        awaitCondition(() -> webSocketSessionRegistry.getRegisteredSessionCount() == 0, 3000);
    }

    @Test
    @DisplayName("Phase 3 D: Intercepteur sortant bloque le push vers une session expirée (exp) ou révoquée avant même le sweep périodique")
    void testOutboundInterceptorBlocksExpiredSubscriberAndClosesSession() throws Exception {
        String tokenProprio = jwtService.generateAccessToken(proprio);
        String tokenLoc = jwtService.generateAccessToken(locataire);
        String topic = "/topic/conversations/" + conversation.getId();
        String sendDest = "/app/conversations/" + conversation.getId() + "/envoyer";

        // 1. Connecter A (proprio) et B (locataire passif)
        StompSession sessionProprio = connectSession(tokenProprio, null);
        StompSession sessionLoc = connectSession(tokenLoc, null);
        BlockingQueue<MessageResponseDTO> locMessages = new LinkedBlockingQueue<>();
        sessionLoc.subscribe(topic, queueHandler(locMessages));
        Thread.sleep(150);

        // 2. Flux valide préalable : B reçoit bien le message
        sessionProprio.send(sendDest, new MessageSendDTO("Avant expiration"));
        MessageResponseDTO okMsg = locMessages.poll(4, TimeUnit.SECONDS);
        assertNotNull(okMsg);
        assertEquals("Avant expiration", okMsg.getContenu());

        // 3. Simuler le franchissement de la date d'expiration (exp < now) uniquement sur la session STOMP ouverte de B
        List<String> bSessionIds = webSocketSessionRegistry.getActiveSessionIds(locataire.getEmail());
        assertEquals(1, bSessionIds.size());
        webSocketSessionRegistry.bindUser(
                bSessionIds.get(0),
                locataire.getEmail(),
                Instant.now().minusSeconds(60).toEpochMilli(),
                locataire.getTokenVersion()
        );

        // A (toujours valide) envoie un message : outboundChannelInterceptor bloque l'envoi vers B et ferme sa session !
        sessionProprio.send(sendDest, new MessageSendDTO("Confidentiel post-expiration JWT"));
        assertNull(locMessages.poll(800, TimeUnit.MILLISECONDS),
                "Le client B dont le JWT a expiré ne doit plus recevoir de messages sur /topic/conversations/{id}");
        awaitCondition(() -> webSocketSessionRegistry.getActiveSessionCount(locataire.getEmail()) == 0, 3000);

        sessionProprio.disconnect();
        awaitCondition(() -> webSocketSessionRegistry.getRegisteredSessionCount() == 0, 3000);
    }

    @Test
    @DisplayName("Phase 3 A1 + D: Déconnexion, changement de mot de passe et désactivation ferment immédiatement les sessions STOMP et bloquent reconnexion")
    void testLogoutPasswordChangeAndDeactivationCloseStompSessionsAndBlockReconnect() throws Exception {
        String topic = "/topic/conversations/" + conversation.getId();

        // 1. Déconnexion -> fermeture STOMP + reconnexion avec ancien JWT refusée
        TokenResponseDTO locTokens = loginRest(locataire.getEmail(), password);
        StompSession sLogout = connectSession(locTokens.getAccessToken(), null);
        assertEquals(1, webSocketSessionRegistry.getActiveSessionCount(locataire.getEmail()));

        HttpHeaders locAuthHeaders = new HttpHeaders();
        locAuthHeaders.setBearerAuth(locTokens.getAccessToken());
        locAuthHeaders.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<Void> decRes = restTemplate.exchange(
                "http://localhost:" + port + "/api/v1/auth/deconnexion",
                HttpMethod.POST,
                new HttpEntity<>(new DeconnexionRequestDTO(locTokens.getRefreshToken()), locAuthHeaders),
                Void.class
        );
        assertEquals(HttpStatus.NO_CONTENT, decRes.getStatusCode());
        awaitCondition(() -> webSocketSessionRegistry.getActiveSessionCount(locataire.getEmail()) == 0, 3000);
        assertThrows(Exception.class, () -> connectSession(locTokens.getAccessToken(), null));

        // 2. Changement de mot de passe -> fermeture STOMP + reconnexion refusée
        TokenResponseDTO locTokens2 = loginRest(locataire.getEmail(), password);
        StompSession sPwd = connectSession(locTokens2.getAccessToken(), null);
        assertEquals(1, webSocketSessionRegistry.getActiveSessionCount(locataire.getEmail()));

        HttpHeaders pwdHeaders = new HttpHeaders();
        pwdHeaders.setBearerAuth(locTokens2.getAccessToken());
        pwdHeaders.setContentType(MediaType.APPLICATION_JSON);
        String newPwd = "NewPassword456!";
        ResponseEntity<String> pwdRes = restTemplate.exchange(
                "http://localhost:" + port + "/api/v1/utilisateurs/moi/mot-de-passe",
                HttpMethod.PUT,
                new HttpEntity<>(new ChangerMotDePasseRequestDTO(password, newPwd), pwdHeaders),
                String.class
        );
        assertEquals(HttpStatus.OK, pwdRes.getStatusCode());
        awaitCondition(() -> webSocketSessionRegistry.getActiveSessionCount(locataire.getEmail()) == 0, 3000);
        assertThrows(Exception.class, () -> connectSession(locTokens2.getAccessToken(), null));

        // 3. Désactivation du compte par ADMIN -> fermeture STOMP + reconnexion refusée
        TokenResponseDTO locTokens3 = loginRest(locataire.getEmail(), newPwd);
        TokenResponseDTO adminTokens = loginRest(admin.getEmail(), password);
        StompSession sDeactivate = connectSession(locTokens3.getAccessToken(), null);
        assertEquals(1, webSocketSessionRegistry.getActiveSessionCount(locataire.getEmail()));

        HttpHeaders adminHeaders = new HttpHeaders();
        adminHeaders.setBearerAuth(adminTokens.getAccessToken());
        adminHeaders.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> deactRes = restTemplate.exchange(
                "http://localhost:" + port + "/api/v1/admin/utilisateurs/" + locataire.getId() + "/statut",
                HttpMethod.PUT,
                new HttpEntity<>(new ChangeStatutRequestDTO(false), adminHeaders),
                String.class
        );
        assertEquals(HttpStatus.OK, deactRes.getStatusCode());
        awaitCondition(() -> webSocketSessionRegistry.getActiveSessionCount(locataire.getEmail()) == 0, 3000);
        assertThrows(Exception.class, () -> connectSession(locTokens3.getAccessToken(), null));
        assertFalse(sLogout.isConnected() && sPwd.isConnected() && sDeactivate.isConnected());

        // 4. Tiers non-participant ne peut pas souscrire au topic ni envoyer dans la conversation
        TokenResponseDTO tiersTokens = loginRest(tiers.getEmail(), password);
        TokenResponseDTO proprioTokens = loginRest(proprio.getEmail(), password);
        StompSession sTiers = connectSession(tiersTokens.getAccessToken(), null);
        BlockingQueue<MessageResponseDTO> tiersQueue = new LinkedBlockingQueue<>();
        sTiers.subscribe(topic, queueHandler(tiersQueue));
        awaitCondition(() -> webSocketSessionRegistry.getActiveSessionCount(tiers.getEmail()) == 0, 3000);

        StompSession sProprio = connectSession(proprioTokens.getAccessToken(), null);
        Thread.sleep(150);
        sProprio.send("/app/conversations/" + conversation.getId() + "/envoyer", new MessageSendDTO("Message privé"));
        assertNull(tiersQueue.poll(600, TimeUnit.MILLISECONDS),
                "Un tiers non-participant ne doit recevoir aucun message sur /topic/conversations/{id}");

        if (sTiers.isConnected()) {
            sTiers.disconnect();
        }
        sProprio.disconnect();
        awaitCondition(() -> webSocketSessionRegistry.getRegisteredSessionCount() == 0, 3000);
    }

    @Test
    @DisplayName("CA3-001 & CA3-002: Le rejeu d'un Refresh Token ferme immédiatement la session STOMP compromise sans couper un autre appareil, et un ancien Refresh Token ne peut pas déconnecter une session STOMP active")
    void testRefreshTokenReplayClosesCompromisedWebSocketSessionAndPreservesOtherDeviceAndRejectsDoSLogout() throws Exception {
        // 1. Connexion sur Appareil A (locataire) et rotation R1_A -> R2_A
        TokenResponseDTO devA1 = loginRest(locataire.getEmail(), password);
        ResponseEntity<TokenResponseDTO> rotRes = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/v1/auth/rafraichir",
                new com.ecourtier.domain.utilisateur.dto.RefreshTokenRequestDTO(devA1.getRefreshToken()),
                TokenResponseDTO.class
        );
        assertEquals(HttpStatus.OK, rotRes.getStatusCode());
        TokenResponseDTO devA2 = rotRes.getBody();
        assertNotNull(devA2);

        // 2. Connexion indépendante sur Appareil B (locataire)
        TokenResponseDTO devB = loginRest(locataire.getEmail(), password);

        // Ouvrir une session STOMP sur Appareil A et une session STOMP sur Appareil B
        StompSession wsDevA = connectSession(devA2.getAccessToken(), null);
        StompSession wsDevB = connectSession(devB.getAccessToken(), null);
        assertEquals(2, webSocketSessionRegistry.getActiveSessionCount(locataire.getEmail()));

        // 3. CA3-001 : Rejeu du Refresh Token consommé R1_A -> 401 + fermeture immédiate de wsDevA uniquement
        ResponseEntity<String> replayRes = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/v1/auth/rafraichir",
                new com.ecourtier.domain.utilisateur.dto.RefreshTokenRequestDTO(devA1.getRefreshToken()),
                String.class
        );
        assertEquals(HttpStatus.UNAUTHORIZED, replayRes.getStatusCode());

        awaitCondition(() -> webSocketSessionRegistry.getActiveSessionCount(locataire.getEmail()) == 1, 3000);
        assertThrows(Exception.class, () -> connectSession(devA1.getAccessToken(), null));
        assertThrows(Exception.class, () -> connectSession(devA2.getAccessToken(), null));

        // 4. CA3-002 : Tentative de déconnexion forcée avec les anciens Refresh Tokens (R1_A consommé, R2_A révoqué) -> 401 sans couper wsDevB
        ResponseEntity<String> dosLogout1 = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/v1/auth/deconnexion",
                new DeconnexionRequestDTO(devA1.getRefreshToken()),
                String.class
        );
        assertEquals(HttpStatus.UNAUTHORIZED, dosLogout1.getStatusCode());

        ResponseEntity<String> dosLogout2 = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/v1/auth/deconnexion",
                new DeconnexionRequestDTO(devA2.getRefreshToken()),
                String.class
        );
        assertEquals(HttpStatus.UNAUTHORIZED, dosLogout2.getStatusCode());

        assertEquals(1, webSocketSessionRegistry.getActiveSessionCount(locataire.getEmail()),
                "La session WebSocket de l'appareil B ne doit pas être affectée par le rejeu sur A ni par une tentative de déconnexion avec un ancien token");

        if (wsDevA.isConnected()) {
            wsDevA.disconnect();
        }
        wsDevB.disconnect();
        awaitCondition(() -> webSocketSessionRegistry.getRegisteredSessionCount() == 0, 3000);
    }
}
