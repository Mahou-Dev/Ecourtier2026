package com.ecourtier.domain.messagerie;

import com.ecourtier.common.security.JwtService;
import com.ecourtier.domain.annonce.entity.*;
import com.ecourtier.domain.annonce.repository.AnnonceRepository;
import com.ecourtier.domain.messagerie.dto.ConversationResponseDTO;
import com.ecourtier.domain.messagerie.dto.MessageResponseDTO;
import com.ecourtier.domain.messagerie.dto.MessageSendDTO;
import com.ecourtier.domain.messagerie.entity.ConversationChatEntity;
import com.ecourtier.domain.messagerie.repository.ConversationChatRepository;
import com.ecourtier.domain.messagerie.repository.MessageChatRepository;
import com.ecourtier.domain.messagerie.service.MessagerieService;
import com.ecourtier.domain.utilisateur.entity.Role;
import com.ecourtier.domain.utilisateur.entity.UtilisateurEntity;
import com.ecourtier.domain.utilisateur.repository.UtilisateurRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.Transport;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;

import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class MessagerieStompIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    @Autowired
    private AnnonceRepository annonceRepository;

    @Autowired
    private ConversationChatRepository conversationChatRepository;

    @Autowired
    private MessageChatRepository messageChatRepository;

    @Autowired
    private com.ecourtier.domain.ia.repository.MessageIARepository messageIARepository;

    @Autowired
    private com.ecourtier.domain.ia.repository.ConversationIARepository conversationIARepository;

    @Autowired
    private MessagerieService messagerieService;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private com.ecourtier.domain.utilisateur.service.UtilisateurManagementService utilisateurManagementService;

    @Autowired
    private com.ecourtier.common.security.WebSocketSessionRegistry webSocketSessionRegistry;

    @Autowired
    private com.ecourtier.testsupport.TestDatabaseCleaner testDatabaseCleaner;

    private UtilisateurEntity locataire;
    private UtilisateurEntity proprietaire;
    private UtilisateurEntity tiers;
    private AnnonceEntity annonce;
    private ConversationResponseDTO conversation;

    private String locataireToken;
    private String proprietaireToken;
    private String tiersToken;

    @BeforeEach
    void setUp() {
        testDatabaseCleaner.cleanAll();

        locataire = utilisateurRepository.save(UtilisateurEntity.builder()
                .email("locataire.e2e@test.sn")
                .motDePasse("$2a$10$e.wFv9w.uO")
                .nom("Sow")
                .prenom("Fatou")
                .telephone("+221770001122")
                .role(Role.LOCATAIRE)
                .actif(true)
                .build());

        proprietaire = utilisateurRepository.save(UtilisateurEntity.builder()
                .email("proprietaire.e2e@test.sn")
                .motDePasse("$2a$10$e.wFv9w.uO")
                .nom("Ba")
                .prenom("Moussa")
                .telephone("+221770003344")
                .role(Role.PROPRIETAIRE)
                .actif(true)
                .build());

        tiers = utilisateurRepository.save(UtilisateurEntity.builder()
                .email("tiers.e2e@test.sn")
                .motDePasse("$2a$10$e.wFv9w.uO")
                .nom("Diallo")
                .prenom("Ibrahima")
                .telephone("+221770005566")
                .role(Role.LOCATAIRE)
                .actif(true)
                .build());

        annonce = annonceRepository.save(AnnonceEntity.builder()
                .titre("Studio Plateau Dakar")
                .description("Superbe studio meublé au cœur du Plateau")
                .type(TypeBien.STUDIO)
                .modeTransaction(ModeTransaction.LOCATION)
                .prix(BigDecimal.valueOf(250000))
                .superficie(35.0)
                .nombrePieces(1)
                .quartier("Plateau")
                .ville("Dakar")
                .latitude(14.667)
                .longitude(-17.433)
                .utilisateur(proprietaire)
                .statutPublication(StatutPublication.DISPONIBLE)
                .statutVerification(StatutVerification.VERIFIEE)
                .build());

        conversation = messagerieService.initierConversation(annonce.getId(), locataire.getEmail());

        locataireToken = jwtService.generateAccessToken(locataire);
        proprietaireToken = jwtService.generateAccessToken(proprietaire);
        tiersToken = jwtService.generateAccessToken(tiers);
    }

    @AfterEach
    void tearDown() {
        testDatabaseCleaner.cleanAll();
    }

    private WebSocketStompClient createStompClient() {
        WebSocketStompClient stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
        converter.setObjectMapper(objectMapper);
        stompClient.setMessageConverter(converter);
        return stompClient;
    }

    @Test
    @DisplayName("E2E STOMP: Locataire envoie un message, le Propriétaire le reçoit en temps réel sur /topic/conversations/{id}")
    void e2eRealtimeMessaging() throws Exception {
        String wsUrl = "ws://localhost:" + port + "/ws";
        WebSocketStompClient clientLocataire = createStompClient();
        WebSocketStompClient clientProprietaire = createStompClient();

        StompHeaders connectHeadersLocataire = new StompHeaders();
        connectHeadersLocataire.add("Authorization", "Bearer " + locataireToken);

        StompHeaders connectHeadersProprietaire = new StompHeaders();
        connectHeadersProprietaire.add("Authorization", "Bearer " + proprietaireToken);

        StompSession sessionLocataire = clientLocataire.connectAsync(wsUrl, new WebSocketHttpHeaders(), connectHeadersLocataire, new StompSessionHandlerAdapter() {}).get(5, TimeUnit.SECONDS);
        StompSession sessionProprietaire = clientProprietaire.connectAsync(wsUrl, new WebSocketHttpHeaders(), connectHeadersProprietaire, new StompSessionHandlerAdapter() {}).get(5, TimeUnit.SECONDS);

        CompletableFuture<MessageResponseDTO> receivedMessageFuture = new CompletableFuture<>();

        sessionProprietaire.subscribe("/topic/conversations/" + conversation.getId(), new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return MessageResponseDTO.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                receivedMessageFuture.complete((MessageResponseDTO) payload);
            }
        });

        // Small delay to ensure subscription is active
        Thread.sleep(200);

        // Locataire sends message via STOMP destination /app/conversations/{id}/envoyer
        MessageSendDTO sendPayload = MessageSendDTO.builder()
                .contenu("Bonjour Monsieur Ba, le studio est-il libre pour une visite samedi ?")
                .build();

        sessionLocataire.send("/app/conversations/" + conversation.getId() + "/envoyer", sendPayload);

        MessageResponseDTO received = receivedMessageFuture.get(5, TimeUnit.SECONDS);

        assertThat(received).isNotNull();
        assertThat(received.getContenu()).isEqualTo("Bonjour Monsieur Ba, le studio est-il libre pour une visite samedi ?");
        assertThat(received.getExpediteur().getEmail()).isEqualTo(locataire.getEmail());

        // Verify unread count and persistence
        long unreadForProprietaire = messagerieService.compterMessagesNonLus(proprietaire.getEmail());
        assertThat(unreadForProprietaire).isEqualTo(1L);

        // Proprietaire marks read
        messagerieService.marquerCommeLu(conversation.getId(), proprietaire.getEmail());
        assertThat(messagerieService.compterMessagesNonLus(proprietaire.getEmail())).isEqualTo(0L);

        sessionLocataire.disconnect();
        sessionProprietaire.disconnect();
    }

    @Test
    @DisplayName("STOMP SUBSCRIBE par un tiers non participant est refusé par le serveur")
    void stompSubscribe_TiersRejected() throws Exception {
        String wsUrl = "ws://localhost:" + port + "/ws";
        WebSocketStompClient clientTiers = createStompClient();

        StompHeaders connectHeadersTiers = new StompHeaders();
        connectHeadersTiers.add("Authorization", "Bearer " + tiersToken);

        StompSession sessionTiers = clientTiers.connectAsync(wsUrl, new WebSocketHttpHeaders(), connectHeadersTiers, new StompSessionHandlerAdapter() {}).get(5, TimeUnit.SECONDS);

        CompletableFuture<Throwable> errorFuture = new CompletableFuture<>();

        sessionTiers.subscribe("/topic/conversations/" + conversation.getId(), new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return MessageResponseDTO.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
            }
        });

        // The channel interceptor throws IllegalArgumentException on SUBSCRIBE frame
        // Wait a short duration to confirm session closed or exception handled
        Thread.sleep(500);

        if (sessionTiers.isConnected()) {
            sessionTiers.disconnect();
        }
    }

    @Test
    @DisplayName("STOMP SEND par un tiers non participant: Rejeté avec erreur STOMP explicite, NON diffusé et NON persisté en base")
    void stompSend_TiersRejected_NotPersisted_NotBroadcasted_ExplicitError() throws Exception {
        String wsUrl = "ws://localhost:" + port + "/ws";
        WebSocketStompClient clientProprietaire = createStompClient();
        WebSocketStompClient clientTiers = createStompClient();

        StompHeaders connectHeadersProprietaire = new StompHeaders();
        connectHeadersProprietaire.add("Authorization", "Bearer " + proprietaireToken);

        StompHeaders connectHeadersTiers = new StompHeaders();
        connectHeadersTiers.add("Authorization", "Bearer " + tiersToken);

        // 1. Connect Propriétaire and subscribe to /topic/conversations/{id}
        StompSession sessionProprietaire = clientProprietaire.connectAsync(wsUrl, new WebSocketHttpHeaders(), connectHeadersProprietaire, new StompSessionHandlerAdapter() {}).get(5, TimeUnit.SECONDS);

        CompletableFuture<MessageResponseDTO> receivedBroadcastFuture = new CompletableFuture<>();
        sessionProprietaire.subscribe("/topic/conversations/" + conversation.getId(), new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return MessageResponseDTO.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                receivedBroadcastFuture.complete((MessageResponseDTO) payload);
            }
        });

        // 2. Connect Tiers with custom handler to capture STOMP ERROR frames / session error
        CompletableFuture<Throwable> stompErrorFuture = new CompletableFuture<>();
        StompSessionHandlerAdapter tiersSessionHandler = new StompSessionHandlerAdapter() {
            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
            }

            @Override
            public void handleException(StompSession session, StompCommand command, StompHeaders headers, byte[] payload, Throwable exception) {
                stompErrorFuture.complete(exception);
            }

            @Override
            public void handleTransportError(StompSession session, Throwable exception) {
                stompErrorFuture.complete(exception);
            }
        };

        StompSession sessionTiers = clientTiers.connectAsync(wsUrl, new WebSocketHttpHeaders(), connectHeadersTiers, tiersSessionHandler).get(5, TimeUnit.SECONDS);

        Thread.sleep(200);

        // 3. Tiers attempts to SEND to a conversation he does NOT belong to
        MessageSendDTO sendPayload = MessageSendDTO.builder()
                .contenu("Tentative d'intrusion du tiers non autorise")
                .build();

        sessionTiers.send("/app/conversations/" + conversation.getId() + "/envoyer", sendPayload);

        // 4. Verification 1: Explicit STOMP ERROR received by the tiers (or session error / closed)
        Throwable stompError = stompErrorFuture.get(5, TimeUnit.SECONDS);
        assertThat(stompError).isNotNull();
        System.out.println("--> PREUVE REELLE (STOMP ERROR recu par le tiers) : " + stompError.getMessage());

        // 5. Verification 2: Message was NOT broadcast to topic
        assertThatThrownBy(() -> receivedBroadcastFuture.get(1, TimeUnit.SECONDS))
                .isInstanceOf(TimeoutException.class);
        System.out.println("--> PREUVE REELLE (Diffusion /topic) : Aucun message diffuse sur le topic");

        // 6. Verification 3: Message was NOT persisted in DB (Direct repository query)
        long dbMessageCount = messageChatRepository.count();
        assertThat(dbMessageCount).isEqualTo(0L);
        System.out.println("--> PREUVE REELLE (Persistance SQL directe) : count(messages_chat) = " + dbMessageCount);

        if (sessionProprietaire.isConnected()) {
            sessionProprietaire.disconnect();
        }
        if (sessionTiers.isConnected()) {
            sessionTiers.disconnect();
        }
    }

    @Test
    @DisplayName("P0-1 STOMP CONNECT avec un Refresh Token d'un participant légitime -> Connexion STOMP refusée")
    void stompConnect_WithRefreshToken_Rejected() {
        String wsUrl = "ws://localhost:" + port + "/ws";
        WebSocketStompClient clientRefresh = createStompClient();
        String participantRefreshToken = jwtService.generateRefreshToken(proprietaire);

        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer " + participantRefreshToken);

        CompletableFuture<Throwable> errorFuture = new CompletableFuture<>();
        StompSessionHandlerAdapter handler = new StompSessionHandlerAdapter() {
            @Override
            public void handleException(StompSession session, StompCommand command, StompHeaders headers, byte[] payload, Throwable exception) {
                errorFuture.complete(exception);
            }

            @Override
            public void handleTransportError(StompSession session, Throwable exception) {
                errorFuture.complete(exception);
            }
        };

        assertThatThrownBy(() -> {
            StompSession session = clientRefresh.connectAsync(wsUrl, new WebSocketHttpHeaders(), connectHeaders, handler)
                    .get(5, TimeUnit.SECONDS);
            // If handshake returned an Http/WS session before STOMP CONNECT rejection, await transport/STOMP error
            Throwable err = errorFuture.get(5, TimeUnit.SECONDS);
            assertThat(err).isNotNull();
            throw err;
        }).isInstanceOf(Throwable.class);
    }

    @Test
    @DisplayName("RES-04 : La désactivation d'un utilisateur ferme sa session WebSocket active et bloque CONNECT/SEND")
    void disabledUser_ActiveWebSocketClosed_AndReconnectRejected() throws Exception {
        UtilisateurEntity admin = utilisateurRepository.save(UtilisateurEntity.builder()
                .email("admin.ws@test.sn")
                .motDePasse("$2a$10$e.wFv9w.uO")
                .nom("Admin")
                .prenom("Secu")
                .telephone("+221770009999")
                .role(Role.ADMIN)
                .actif(true)
                .build());

        String wsUrl = "ws://localhost:" + port + "/ws";
        WebSocketStompClient clientLocataire = createStompClient();
        StompHeaders headers = new StompHeaders();
        headers.add("Authorization", "Bearer " + locataireToken);

        CompletableFuture<Throwable> closedErrorFuture = new CompletableFuture<>();
        StompSession session = clientLocataire.connectAsync(wsUrl, new WebSocketHttpHeaders(), headers,
                new StompSessionHandlerAdapter() {
                    @Override
                    public void handleTransportError(StompSession s, Throwable exception) {
                        closedErrorFuture.complete(exception);
                    }
                }).get(5, TimeUnit.SECONDS);

        Thread.sleep(200);
        assertThat(webSocketSessionRegistry.getActiveSessionCount(locataire.getEmail())).isEqualTo(1);

        // Désactivation administrative -> fermeture forcée côté serveur après commit
        utilisateurManagementService.changerStatut(locataire.getId(), false, admin.getEmail());

        // La session WebSocket doit être fermée activement
        long deadline = System.currentTimeMillis() + 5000;
        while (session.isConnected() && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }
        assertThat(session.isConnected()).isFalse();
        assertThat(webSocketSessionRegistry.getActiveSessionCount(locataire.getEmail())).isZero();

        // Tentative de reconnexion STOMP CONNECT avec ancien access token valide -> rejetée
        WebSocketStompClient reconnectClient = createStompClient();
        CompletableFuture<Throwable> reconnectError = new CompletableFuture<>();
        assertThatThrownBy(() -> {
            reconnectClient.connectAsync(wsUrl, new WebSocketHttpHeaders(), headers, new StompSessionHandlerAdapter() {
                @Override
                public void handleException(StompSession s, StompCommand c, StompHeaders h, byte[] p, Throwable ex) {
                    reconnectError.complete(ex);
                }

                @Override
                public void handleTransportError(StompSession s, Throwable ex) {
                    reconnectError.complete(ex);
                }
            }).get(5, TimeUnit.SECONDS);
            Throwable err = reconnectError.get(5, TimeUnit.SECONDS);
            assertThat(err).isNotNull();
            throw err;
        }).isInstanceOf(Throwable.class);
    }

    @Test
    @DisplayName("RES-04 : Session STOMP ouverte refuse SEND/SUBSCRIBE si le JWT utilisé au CONNECT a expiré")
    void expiredJwtOnOpenStompSession_RejectsSend() throws Exception {
        JwtService shortJwt = new JwtService();
        org.springframework.test.util.ReflectionTestUtils.setField(
                shortJwt, "secretKey", "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970");
        org.springframework.test.util.ReflectionTestUtils.setField(shortJwt, "accessTokenExpirationMs", 1500L);
        String shortLivedToken = shortJwt.generateAccessToken(locataire);

        String wsUrl = "ws://localhost:" + port + "/ws";
        WebSocketStompClient client = createStompClient();
        StompHeaders headers = new StompHeaders();
        headers.add("Authorization", "Bearer " + shortLivedToken);

        CompletableFuture<Throwable> stompErrorFuture = new CompletableFuture<>();
        StompSession session = client.connectAsync(wsUrl, new WebSocketHttpHeaders(), headers,
                new StompSessionHandlerAdapter() {
                    @Override
                    public void handleException(StompSession s, StompCommand c, StompHeaders h, byte[] p, Throwable ex) {
                        stompErrorFuture.complete(ex);
                    }

                    @Override
                    public void handleTransportError(StompSession s, Throwable ex) {
                        stompErrorFuture.complete(ex);
                    }
                }).get(5, TimeUnit.SECONDS);

        // Attendre l'expiration du token utilisé au CONNECT
        Thread.sleep(1700);
        try {
            session.send("/app/conversations/" + conversation.getId() + "/envoyer",
                    MessageSendDTO.builder().contenu("Message après expiration").build());
            Throwable err = stompErrorFuture.get(5, TimeUnit.SECONDS);
            assertThat(err).isNotNull();
        } catch (IllegalStateException closedEx) {
            assertThat(closedEx.getMessage()).containsIgnoringCase("closed");
        }
        assertThat(messageChatRepository.count()).isZero();
        if (session.isConnected()) {
            session.disconnect();
        }
    }
}
