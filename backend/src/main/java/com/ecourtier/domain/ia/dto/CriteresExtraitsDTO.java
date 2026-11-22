package com.ecourtier.domain.ia.dto;

import com.ecourtier.domain.annonce.entity.Equipement;
import com.ecourtier.domain.annonce.entity.ModeTransaction;
import com.ecourtier.domain.annonce.entity.ProximiteTag;
import com.ecourtier.domain.annonce.entity.TypeBien;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CriteresExtraitsDTO {
    private BigDecimal budgetMin;
    private BigDecimal budgetMax;
    private TypeBien type;
    private ModeTransaction modeTransaction;
    private String quartier;
    private List<String> quartiers;
    private String ville;
    private Integer nombrePieces;
    private Integer nombreChambres;
    private Integer etage;
    private Boolean ascenseur;
    private Boolean meuble;
    private Boolean animauxAcceptes;
    private Double superficieMin;
    private Double superficieMax;
    private Set<Equipement> equipements;
    private Set<ProximiteTag> proximites;
    private Double confiance;
    private String questionDeRelance;

    /**
     * Identifiants ordonnés des annonces suggérées pour la restauration fidèle après rechargement (ANO-IA-03).
     */
    private List<UUID> annoncesSuggereesIds;

    /**
     * Justifications personnalisées par identifiant d'annonce expliquant la pertinence métier.
     */
    private Map<String, String> explicationsAnnonces;

    /**
     * Fournisseur IA ayant produit l'extraction / réponse (gemini ou fallback-local).
     */
    private String fournisseurIa;

    /**
     * Indique si le mode de secours déterministe local a été utilisé suite à l'indisponibilité de Gemini.
     */
    private Boolean modeSecours;

    /**
     * Code technique non sensible justifiant le passage en mode secours local
     * (CLE_ABSENTE, IA_DESACTIVEE, TIMEOUT, QUOTA, ERREUR_HTTP, REPONSE_INVALIDE, FOURNISSEUR_NON_AUTORISE).
     */
    private String motifSecours;

    /**
     * Préférences qualitatives ou souples non éliminatoires (ex: "quartier calme", "logement meublé de préférence").
     */
    private List<String> preferencesSouples;

    /**
     * Attentes complémentaires exprimées (ex: "cour", "cuisine", "grand terrain").
     */
    private List<String> attentesComplementaires;

    /**
     * Points restant à préciser par l'utilisateur avant ou pendant la recherche (ex: "budget maximum en FCFA").
     */
    private List<String> precisionsAClarifier;

    /**
     * Champs transitoires utilisés lors de la fusion multi-tours (non persistés comme critères de filtre).
     */
    @JsonIgnore
    private Set<String> criteresASupprimer;

    @JsonIgnore
    private Boolean horsSujet;

    @JsonIgnore
    private String intentionSpeciale;

    @JsonIgnore
    private Boolean demandeQuestionsComplementaires;

    @JsonIgnore
    private Boolean besoinIndecis;
}
