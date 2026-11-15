package com.ecourtier.domain.transaction.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

@Entity
@Table(name = "paiements_wave")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaiementWaveEntity {

    private static final ZoneId DAKAR_ZONE = ZoneId.of("Africa/Dakar");

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transaction_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private TransactionEntity transaction;

    @Enumerated(EnumType.STRING)
    @Column(name = "type_operation", nullable = false, length = 30)
    private TypeOperationWave typeOperation;

    @Enumerated(EnumType.STRING)
    @Column(name = "statut", nullable = false, length = 40)
    private StatutPaiementWave statut;

    @Column(name = "cle_idempotence", nullable = false, unique = true, length = 128)
    private String cleIdempotence;

    @Column(name = "reference_externe", length = 150)
    private String referenceExterne;

    @Column(name = "montant", nullable = false, precision = 15, scale = 2)
    private BigDecimal montant;

    @Column(name = "devise", nullable = false, length = 10)
    @Builder.Default
    private String devise = "XOF";

    @Column(name = "tentatives", nullable = false)
    @Builder.Default
    private Integer tentatives = 1;

    @Column(name = "message_erreur", length = 1000)
    private String messageErreur;

    @Column(name = "payload_webhook", columnDefinition = "TEXT")
    private String payloadWebhook;

    @Column(name = "date_creation", nullable = false, updatable = false)
    private LocalDateTime dateCreation;

    @Column(name = "date_modification", nullable = false)
    private LocalDateTime dateModification;

    @Column(name = "date_confirmation")
    private LocalDateTime dateConfirmation;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now(DAKAR_ZONE);
        if (dateCreation == null) {
            dateCreation = now;
        }
        if (dateModification == null) {
            dateModification = dateCreation;
        }
        if (devise == null || devise.isBlank()) {
            devise = "XOF";
        }
        if (tentatives == null) {
            tentatives = 1;
        }
    }
}
