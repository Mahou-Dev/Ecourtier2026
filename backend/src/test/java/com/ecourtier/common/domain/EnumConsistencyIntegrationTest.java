package com.ecourtier.common.domain;

import com.ecourtier.domain.annonce.entity.AnnonceEntity;
import com.ecourtier.domain.annonce.entity.ModeTransaction;
import com.ecourtier.domain.annonce.entity.StatutPublication;
import com.ecourtier.domain.annonce.entity.StatutVerification;
import com.ecourtier.domain.annonce.entity.TypeBien;
import com.ecourtier.domain.annonce.repository.AnnonceRepository;
import com.ecourtier.domain.avis.entity.MotifSignalement;
import com.ecourtier.domain.avis.entity.SignalementEntity;
import com.ecourtier.domain.avis.entity.StatutSignalement;
import com.ecourtier.domain.avis.repository.SignalementRepository;
import com.ecourtier.domain.transaction.entity.StatutBail;
import com.ecourtier.domain.transaction.entity.StatutSequestre;
import com.ecourtier.domain.transaction.entity.TransactionEntity;
import com.ecourtier.domain.transaction.repository.TransactionRepository;
import com.ecourtier.domain.utilisateur.entity.Role;
import com.ecourtier.domain.utilisateur.entity.UtilisateurEntity;
import com.ecourtier.domain.utilisateur.repository.UtilisateurRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class EnumConsistencyIntegrationTest {

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    @Autowired
    private AnnonceRepository annonceRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private SignalementRepository signalementRepository;

    @Test
    @DisplayName("Vérifie l'insertion et la persistance en base de toutes les valeurs des Enums métiers (Contraintes CHECK)")
    void testAllEnumValuesPersistence() {
        assertDoesNotThrow(() -> {
            // 1. Roles
            for (Role role : Role.values()) {
                UtilisateurEntity user = utilisateurRepository.save(UtilisateurEntity.builder()
                        .prenom("User")
                        .nom(role.name())
                        .email("user." + role.name().toLowerCase() + "@enumtest.sn")
                        .motDePasse("Pass1234!")
                        .telephone("+22177000" + role.ordinal())
                        .role(role)
                        .actif(true)
                        .build());
                assertEquals(role, user.getRole());
            }

            UtilisateurEntity owner = utilisateurRepository.findAll().get(0);

            // 2. StatutPublication, StatutVerification, TypeBien, ModeTransaction
            for (StatutPublication pub : StatutPublication.values()) {
                for (TypeBien type : TypeBien.values()) {
                    AnnonceEntity annonce = annonceRepository.save(AnnonceEntity.builder()
                            .titre("Annonce Enum " + pub.name() + " " + type.name())
                            .description("Description enum test")
                            .type(type)
                            .modeTransaction(ModeTransaction.LOCATION)
                            .prix(BigDecimal.valueOf(100000))
                            .superficie(50.0)
                            .nombrePieces(2)
                            .quartier("Fann")
                            .ville("Dakar")
                            .latitude(14.70)
                            .longitude(-17.46)
                            .statutVerification(StatutVerification.VERIFIEE)
                            .statutPublication(pub)
                            .utilisateur(owner)
                            .build());

                    assertEquals(pub, annonce.getStatutPublication());
                    assertEquals(type, annonce.getType());

                    // 3. StatutSequestre & StatutBail
                    for (StatutSequestre seq : StatutSequestre.values()) {
                        TransactionEntity tx = transactionRepository.save(TransactionEntity.builder()
                                .annonce(annonce)
                                .locataire(owner)
                                .montantCaution(BigDecimal.valueOf(100000))
                                .statutSequestre(seq)
                                .statutBail(StatutBail.SIGNE_COMPLET)
                                .build());
                        assertEquals(seq, tx.getStatutSequestre());
                    }
                }
            }

            // 4. MotifSignalement & StatutSignalement
            AnnonceEntity sampleAnnonce = annonceRepository.findAll().get(0);
            for (MotifSignalement motif : MotifSignalement.values()) {
                for (StatutSignalement statSig : StatutSignalement.values()) {
                    SignalementEntity sig = signalementRepository.save(SignalementEntity.builder()
                            .annonce(sampleAnnonce)
                            .auteur(owner)
                            .motif(motif)
                            .description("Test enum motif")
                            .statut(statSig)
                            .build());
                    assertEquals(motif, sig.getMotif());
                    assertEquals(statSig, sig.getStatut());
                }
            }
        });
    }
}
