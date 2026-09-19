package com.ecourtier.common.service;

import com.ecourtier.domain.annonce.entity.AnnonceEntity;
import com.ecourtier.domain.annonce.entity.Equipement;
import com.ecourtier.domain.annonce.entity.ModeTransaction;
import com.ecourtier.domain.annonce.entity.ProximiteTag;
import com.ecourtier.domain.annonce.entity.StatutPublication;
import com.ecourtier.domain.annonce.entity.StatutVerification;
import com.ecourtier.domain.annonce.entity.TypeBien;
import com.ecourtier.domain.annonce.repository.AnnonceRepository;
import com.ecourtier.domain.utilisateur.entity.Role;
import com.ecourtier.domain.utilisateur.entity.UtilisateurEntity;
import com.ecourtier.domain.utilisateur.repository.UtilisateurRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

@Service
@Slf4j
@RequiredArgsConstructor
public class DemoDataSeederService {

    private final UtilisateurRepository utilisateurRepository;
    private final AnnonceRepository annonceRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public boolean seedDemoData() {
        if (annonceRepository.count() > 0) {
            log.info("[DEMO SEED] La base contient déjà des annonces. Operation ignorée.");
            return false;
        }

        log.info("[DEMO SEED] Initialisation des données de démonstration...");

        // 1. Create Demo Accounts
        UtilisateurEntity admin = getOrCreateUser("Admin", "E-Courtier", "admin@ecourtier.sn", "AdminPass123!", "+221770000000", Role.ADMIN);
        UtilisateurEntity agent = getOrCreateUser("Agent", "Agréé", "agent@ecourtier.sn", "AgentPass123!", "+221770000001", Role.AGENT);
        UtilisateurEntity proprio = getOrCreateUser("Babacar", "Diouf", "proprio.demo@ecourtier.sn", "ProprioPass123!", "+221770000002", Role.PROPRIETAIRE);
        UtilisateurEntity locataire = getOrCreateUser("Moussa", "Diop", "locataire.demo@ecourtier.sn", "LocatairePass123!", "+221770000003", Role.LOCATAIRE);

        // 2. Create Realistic Listings
        List<AnnonceEntity> demoListings = List.of(
                // Dakar - Almadies
                AnnonceEntity.builder()
                        .titre("Villa de Luxe Almadies Sea View")
                        .description("Superbe villa d'architecte avec piscine à débordement, jardin paysager et vue mer sur les Almadies. Quartier sécurisé proche des ambassades.")
                        .type(TypeBien.VILLA)
                        .modeTransaction(ModeTransaction.LOCATION)
                        .prix(BigDecimal.valueOf(1500000))
                        .superficie(350.0)
                        .nombrePieces(6)
                        .etage(0)
                        .quartier("Almadies")
                        .ville("Dakar")
                        .latitude(14.745)
                        .longitude(-17.518)
                        .meuble(true)
                        .animauxAcceptes(true)
                        .lienVisite360("https://my.matterport.com/show/?m=sample360almadies")
                        .statutVerification(StatutVerification.VERIFIEE)
                        .statutPublication(StatutPublication.DISPONIBLE)
                        .utilisateur(proprio)
                        .equipements(Set.of(Equipement.PISCINE, Equipement.CLIMATISATION, Equipement.PARKING, Equipement.GROUPE_ELECTROGENE, Equipement.GARDIEN))
                        .proximites(Set.of(ProximiteTag.PLAGE, ProximiteTag.ECOLE, ProximiteTag.COMMERCE))
                        .build(),

                // Dakar - Mermoz
                AnnonceEntity.builder()
                        .titre("Appartement F4 Standing Mermoz Corniche")
                        .description("Appartement 4 pièces lumineux avec finitions haut de gamme, cuisine équipée et balcon panoramique sur la Corniche Ouest.")
                        .type(TypeBien.APPARTEMENT)
                        .modeTransaction(ModeTransaction.LOCATION)
                        .prix(BigDecimal.valueOf(450000))
                        .superficie(115.0)
                        .nombrePieces(4)
                        .etage(3)
                        .quartier("Mermoz")
                        .ville("Dakar")
                        .latitude(14.708)
                        .longitude(-17.472)
                        .meuble(true)
                        .animauxAcceptes(false)
                        .statutVerification(StatutVerification.VERIFIEE)
                        .statutPublication(StatutPublication.DISPONIBLE)
                        .utilisateur(proprio)
                        .equipements(Set.of(Equipement.CLIMATISATION, Equipement.PARKING, Equipement.RESERVE_EAU))
                        .proximites(Set.of(ProximiteTag.TRANSPORT, ProximiteTag.HOPITAL, ProximiteTag.ECOLE))
                        .build(),

                // Dakar - Sacré-Cœur
                AnnonceEntity.builder()
                        .titre("Appartement F3 Moderne Sacré-Cœur 3")
                        .description("Résidence calme et sécurisée à Sacré-Cœur 3. Appartement F3 rénové idéal pour famille ou jeunes cadres.")
                        .type(TypeBien.APPARTEMENT)
                        .modeTransaction(ModeTransaction.LOCATION)
                        .prix(BigDecimal.valueOf(280000))
                        .superficie(80.0)
                        .nombrePieces(3)
                        .etage(2)
                        .quartier("Sacré-Cœur")
                        .ville("Dakar")
                        .latitude(14.715)
                        .longitude(-17.465)
                        .meuble(false)
                        .animauxAcceptes(false)
                        .statutVerification(StatutVerification.EN_ATTENTE)
                        .statutPublication(StatutPublication.DISPONIBLE)
                        .utilisateur(proprio)
                        .equipements(Set.of(Equipement.CLIMATISATION, Equipement.RESERVE_EAU))
                        .proximites(Set.of(ProximiteTag.ECOLE, ProximiteTag.MARCHE, ProximiteTag.COMMERCE))
                        .build(),

                // Dakar - Médina
                AnnonceEntity.builder()
                        .titre("Studio Coquet Médina Proche UCAD")
                        .description("Studio indépendant idéal étudiant ou professionnel. À 5 minutes à pied de l'Université Cheikh Anta Diop et des transports BRT.")
                        .type(TypeBien.STUDIO)
                        .modeTransaction(ModeTransaction.LOCATION)
                        .prix(BigDecimal.valueOf(110000))
                        .superficie(30.0)
                        .nombrePieces(1)
                        .etage(1)
                        .quartier("Médina")
                        .ville("Dakar")
                        .latitude(14.685)
                        .longitude(-17.448)
                        .meuble(true)
                        .animauxAcceptes(false)
                        .statutVerification(StatutVerification.VERIFIEE)
                        .statutPublication(StatutPublication.LOUEE)
                        .utilisateur(proprio)
                        .equipements(Set.of(Equipement.WIFI, Equipement.RESERVE_EAU))
                        .proximites(Set.of(ProximiteTag.ECOLE, ProximiteTag.TRANSPORT, ProximiteTag.MARCHE))
                        .build(),

                // Ziguinchor - Nema
                AnnonceEntity.builder()
                        .titre("Belle Villa Familiale Ziguinchor Nema")
                        .description("Grande concession ombragée avec arbres fruitiers et terrasses ventilées dans le quartier paisible de Nema à Ziguinchor.")
                        .type(TypeBien.VILLA)
                        .modeTransaction(ModeTransaction.VENTE)
                        .prix(BigDecimal.valueOf(45000000))
                        .superficie(400.0)
                        .nombrePieces(5)
                        .etage(0)
                        .quartier("Nema")
                        .ville("Ziguinchor")
                        .latitude(12.568)
                        .longitude(-16.273)
                        .meuble(false)
                        .animauxAcceptes(true)
                        .statutVerification(StatutVerification.VERIFIEE)
                        .statutPublication(StatutPublication.DISPONIBLE)
                        .utilisateur(proprio)
                        .equipements(Set.of(Equipement.PARKING, Equipement.RESERVE_EAU))
                        .proximites(Set.of(ProximiteTag.ECOLE, ProximiteTag.HOPITAL))
                        .build(),

                // Ziguinchor - Boucotte
                AnnonceEntity.builder()
                        .titre("Appartement F3 Centre Boucotte")
                        .description("Logement fonctionnel situé au cœur de Boucotte à proximité immédiate du marché et des commerces.")
                        .type(TypeBien.APPARTEMENT)
                        .modeTransaction(ModeTransaction.LOCATION)
                        .prix(BigDecimal.valueOf(95000))
                        .superficie(70.0)
                        .nombrePieces(3)
                        .etage(1)
                        .quartier("Boucotte")
                        .ville("Ziguinchor")
                        .latitude(12.575)
                        .longitude(-16.268)
                        .meuble(false)
                        .animauxAcceptes(false)
                        .statutVerification(StatutVerification.EN_ATTENTE)
                        .statutPublication(StatutPublication.DISPONIBLE)
                        .utilisateur(proprio)
                        .equipements(Set.of(Equipement.RESERVE_EAU, Equipement.PARKING))
                        .proximites(Set.of(ProximiteTag.MARCHE, ProximiteTag.TRANSPORT))
                        .build(),

                // Saly - Station Touristique
                AnnonceEntity.builder()
                        .titre("Villa de Vacances Saly Niakh Niakhal")
                        .description("Résidence de vacances avec piscine privée à 200m de la plage de Saly. Idéal investissement locatif court séjour.")
                        .type(TypeBien.VILLA)
                        .modeTransaction(ModeTransaction.VENTE)
                        .prix(BigDecimal.valueOf(85000000))
                        .superficie(280.0)
                        .nombrePieces(4)
                        .etage(0)
                        .quartier("Niakh Niakhal")
                        .ville("Saly")
                        .latitude(14.442)
                        .longitude(-17.025)
                        .meuble(true)
                        .animauxAcceptes(true)
                        .lienVisite360("https://my.matterport.com/show/?m=samplesaly360")
                        .statutVerification(StatutVerification.VERIFIEE)
                        .statutPublication(StatutPublication.DISPONIBLE)
                        .utilisateur(proprio)
                        .equipements(Set.of(Equipement.PISCINE, Equipement.CLIMATISATION, Equipement.GROUPE_ELECTROGENE, Equipement.GARDIEN))
                        .proximites(Set.of(ProximiteTag.PLAGE, ProximiteTag.COMMERCE))
                        .build(),

                // Thiès - Dixième
                AnnonceEntity.builder()
                        .titre("Maison Individuelle Thiès Dixième")
                        .description("Charmante maison de ville bien entretenue dans un secteur calme de Thiès Dixième, proche des axes routiers vers Dakar.")
                        .type(TypeBien.VILLA)
                        .modeTransaction(ModeTransaction.LOCATION)
                        .prix(BigDecimal.valueOf(180000))
                        .superficie(160.0)
                        .nombrePieces(4)
                        .etage(0)
                        .quartier("Dixième")
                        .ville("Thiès")
                        .latitude(14.783)
                        .longitude(-16.926)
                        .meuble(false)
                        .animauxAcceptes(true)
                        .statutVerification(StatutVerification.VERIFIEE)
                        .statutPublication(StatutPublication.DISPONIBLE)
                        .utilisateur(proprio)
                        .equipements(Set.of(Equipement.PARKING, Equipement.RESERVE_EAU))
                        .proximites(Set.of(ProximiteTag.ECOLE, ProximiteTag.TRANSPORT))
                        .build()
        );

        annonceRepository.saveAll(demoListings);
        log.info("[DEMO SEED] Seed terminé avec succès : 4 comptes de démonstration et {} annonces créées.", demoListings.size());
        return true;
    }

    private UtilisateurEntity getOrCreateUser(String prenom, String nom, String email, String password, String phone, Role role) {
        return utilisateurRepository.findByEmail(email).orElseGet(() ->
                utilisateurRepository.save(UtilisateurEntity.builder()
                        .prenom(prenom)
                        .nom(nom)
                        .email(email)
                        .motDePasse(passwordEncoder.encode(password))
                        .telephone(phone)
                        .role(role)
                        .actif(true)
                        .build())
        );
    }
}
