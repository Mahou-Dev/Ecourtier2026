package com.ecourtier.domain.annonce.service;

import com.ecourtier.common.service.FileStorageService;
import com.ecourtier.domain.annonce.dto.AnnonceCreateDTO;
import com.ecourtier.domain.annonce.dto.AnnonceResponseDTO;
import com.ecourtier.domain.annonce.dto.AnnonceUpdateDTO;
import com.ecourtier.domain.annonce.dto.DocumentVerificationResponseDTO;
import com.ecourtier.domain.annonce.dto.OrdrePhotosDTO;
import com.ecourtier.domain.annonce.dto.PhotoResponseDTO;
import com.ecourtier.domain.annonce.dto.VerificationDecisionDTO;
import com.ecourtier.domain.annonce.entity.AnnonceEntity;
import com.ecourtier.domain.annonce.entity.DocumentVerificationEntity;
import com.ecourtier.domain.annonce.entity.ModeTransaction;
import com.ecourtier.domain.annonce.entity.PhotoAnnonceEntity;
import com.ecourtier.domain.annonce.entity.ProximiteTag;
import com.ecourtier.domain.annonce.entity.StatutDocument;
import com.ecourtier.domain.annonce.entity.StatutPublication;
import com.ecourtier.domain.annonce.entity.StatutVerification;
import com.ecourtier.domain.annonce.entity.TypeBien;
import com.ecourtier.domain.annonce.entity.TypeDocumentVerification;
import com.ecourtier.domain.annonce.mapper.AnnonceMapper;
import com.ecourtier.domain.annonce.repository.AnnonceRepository;
import com.ecourtier.domain.annonce.repository.DocumentVerificationRepository;
import com.ecourtier.domain.annonce.repository.PhotoAnnonceRepository;
import com.ecourtier.domain.utilisateur.entity.Role;
import com.ecourtier.domain.utilisateur.entity.UtilisateurEntity;
import com.ecourtier.domain.utilisateur.repository.UtilisateurRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class AnnonceServiceTest {

    @Mock
    private AnnonceRepository annonceRepository;

    @Mock
    private PhotoAnnonceRepository photoAnnonceRepository;

    @Mock
    private DocumentVerificationRepository documentVerificationRepository;

    @Mock
    private UtilisateurRepository utilisateurRepository;

    @Mock
    private FileStorageService fileStorageService;

    @Mock
    private AnnonceMapper annonceMapper;

    @InjectMocks
    private AnnonceService annonceService;

    private UtilisateurEntity proprio;
    private AnnonceEntity annonce;

    @BeforeEach
    void setUp() {
        proprio = UtilisateurEntity.builder()
                .id(UUID.randomUUID())
                .email("proprio@test.sn")
                .nom("Diop")
                .prenom("Babacar")
                .role(Role.PROPRIETAIRE)
                .build();

        annonce = AnnonceEntity.builder()
                .id(UUID.randomUUID())
                .titre("Villa de Luxe Almadies")
                .description("Superbe villa vue mer")
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
                .statutVerification(StatutVerification.EN_ATTENTE)
                .statutPublication(StatutPublication.DISPONIBLE)
                .supprime(false)
                .utilisateur(proprio)
                .build();
    }

    @Test
    @DisplayName("Création d'une annonce réussie")
    void testCreerAnnonceSuccess() {
        AnnonceCreateDTO dto = new AnnonceCreateDTO();
        dto.setTitre("Villa Almadies");
        dto.setType(TypeBien.VILLA);
        dto.setModeTransaction(ModeTransaction.LOCATION);
        dto.setPrix(BigDecimal.valueOf(1500000));
        dto.setSuperficie(350.0);
        dto.setNombrePieces(6);

        when(utilisateurRepository.findByEmail("proprio@test.sn")).thenReturn(Optional.of(proprio));
        when(annonceRepository.save(any(AnnonceEntity.class))).thenReturn(annonce);
        when(annonceMapper.toResponseDTO(any())).thenReturn(new AnnonceResponseDTO());

        AnnonceResponseDTO response = annonceService.creerAnnonce(dto, "proprio@test.sn");
        assertNotNull(response);
    }

    @Test
    @DisplayName("Obtenir par ID et Modifier une annonce")
    void testGetAndModifierAnnonce() {
        when(annonceRepository.findByIdAndSupprimeFalse(annonce.getId())).thenReturn(Optional.of(annonce));
        when(annonceMapper.toResponseDTO(annonce)).thenReturn(new AnnonceResponseDTO());

        AnnonceResponseDTO res1 = annonceService.obtenirAnnonceParId(annonce.getId());
        assertNotNull(res1);

        AnnonceUpdateDTO updateDto = new AnnonceUpdateDTO();
        updateDto.setTitre("Nouveau Titre Villa");
        updateDto.setPrix(BigDecimal.valueOf(1600000));

        when(annonceRepository.save(annonce)).thenReturn(annonce);
        AnnonceResponseDTO res2 = annonceService.modifierAnnonce(annonce.getId(), updateDto);
        assertNotNull(res2);
        assertEquals("Nouveau Titre Villa", annonce.getTitre());
    }

    @Test
    @DisplayName("Suppression logique (Soft-delete)")
    void testSupprimerAnnonceLogique() {
        when(annonceRepository.findByIdAndSupprimeFalse(annonce.getId())).thenReturn(Optional.of(annonce));

        annonceService.supprimerAnnonceLogique(annonce.getId());
        assertTrue(annonce.isSupprime());
        verify(annonceRepository).save(annonce);
    }

    @Test
    @DisplayName("Recherche avancée avec combinaisons de filtres et mes_annonces")
    void testRechercherAnnoncesFilters() {
        when(annonceRepository.findAll(any(Specification.class), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(annonce)));
        when(annonceMapper.toResponseDTO(annonce)).thenReturn(new AnnonceResponseDTO());

        Page<AnnonceResponseDTO> page1 = annonceService.rechercherAnnonces(
                PageRequest.of(0, 10),
                BigDecimal.valueOf(100000), BigDecimal.valueOf(2000000),
                TypeBien.VILLA, ModeTransaction.LOCATION,
                100.0, 4, "Almadies", "Dakar",
                true, true, StatutVerification.VERIFIEE, ProximiteTag.PLAGE
        );
        assertEquals(1, page1.getTotalElements());

        Page<AnnonceResponseDTO> page2 = annonceService.rechercherAnnonces(
                PageRequest.of(0, 10),
                null, null, null, null, null, null, null, null,
                null, null, null, null, true, "proprio@test.sn"
        );
        assertEquals(1, page2.getTotalElements());
    }

    @Test
    @DisplayName("Ajout et suppression de photos d'une annonce")
    void testPhotosManagement() {
        MockMultipartFile file = new MockMultipartFile("files", "test.jpg", "image/jpeg", new byte[]{(byte)0xFF, (byte)0xD8, (byte)0xFF, 0x00});
        when(annonceRepository.findByIdAndSupprimeFalse(annonce.getId())).thenReturn(Optional.of(annonce));
        when(photoAnnonceRepository.countByAnnonceId(annonce.getId())).thenReturn(0L);
        when(fileStorageService.storePhotoAnnonce(eq(annonce.getId()), any())).thenReturn("/uploads/annonces/test.jpg");

        PhotoAnnonceEntity photo = PhotoAnnonceEntity.builder().id(UUID.randomUUID()).annonce(annonce).url("/uploads/annonces/test.jpg").ordre(0).build();
        when(photoAnnonceRepository.save(any())).thenReturn(photo);
        when(annonceMapper.toPhotoDTO(any())).thenReturn(new PhotoResponseDTO());

        List<PhotoResponseDTO> photos = annonceService.ajouterPhotos(annonce.getId(), List.of(file));
        assertEquals(1, photos.size());

        // Test limite de 10 photos dépassée -> BAD_REQUEST 400
        when(photoAnnonceRepository.countByAnnonceId(annonce.getId())).thenReturn(10L);
        ResponseStatusException exLimit = assertThrows(ResponseStatusException.class,
                () -> annonceService.ajouterPhotos(annonce.getId(), List.of(file)));
        assertEquals(400, exLimit.getStatusCode().value());

        // Suppression photo non-appartenante -> BAD_REQUEST 400
        UUID photoId = UUID.randomUUID();
        AnnonceEntity otherAnnonce = AnnonceEntity.builder().id(UUID.randomUUID()).build();
        PhotoAnnonceEntity otherPhoto = PhotoAnnonceEntity.builder().id(photoId).annonce(otherAnnonce).build();

        when(photoAnnonceRepository.findById(photoId)).thenReturn(Optional.of(otherPhoto));
        ResponseStatusException exBelong = assertThrows(ResponseStatusException.class,
                () -> annonceService.supprimerPhoto(annonce.getId(), photoId));
        assertEquals(400, exBelong.getStatusCode().value());

        // Suppression photo valide
        otherPhoto.setAnnonce(annonce);
        otherPhoto.setUrl("/uploads/annonces/test.jpg");
        annonceService.supprimerPhoto(annonce.getId(), photoId);
        verify(fileStorageService).deleteFile("/uploads/annonces/test.jpg");
        verify(photoAnnonceRepository).delete(otherPhoto);
    }

    @Test
    @DisplayName("Réordonnancement des photos d'une annonce")
    void testReordonnerPhotos() {
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();

        PhotoAnnonceEntity photo1 = PhotoAnnonceEntity.builder().id(p1).annonce(annonce).ordre(0).build();
        PhotoAnnonceEntity photo2 = PhotoAnnonceEntity.builder().id(p2).annonce(annonce).ordre(1).build();

        when(annonceRepository.findByIdAndSupprimeFalse(annonce.getId())).thenReturn(Optional.of(annonce));
        when(photoAnnonceRepository.findByAnnonceIdOrderByOrdreAsc(annonce.getId())).thenReturn(List.of(photo1, photo2));

        OrdrePhotosDTO dto = new OrdrePhotosDTO();
        dto.setPhotoIdsEnOrdre(List.of(p2, p1));

        annonceService.reordonnerPhotos(annonce.getId(), dto);
        assertEquals(1, photo1.getOrdre());
        assertEquals(0, photo2.getOrdre());
    }

    @Test
    @DisplayName("Ajout et téléchargement de documents de vérification")
    void testDocumentsManagement() {
        MockMultipartFile file = new MockMultipartFile("file", "doc.pdf", "application/pdf", "test".getBytes());
        when(annonceRepository.findByIdAndSupprimeFalse(annonce.getId())).thenReturn(Optional.of(annonce));
        when(fileStorageService.storeDocumentVerification(eq(annonce.getId()), any())).thenReturn("/uploads/docs/doc.pdf");

        DocumentVerificationEntity doc = DocumentVerificationEntity.builder()
                .id(UUID.randomUUID())
                .annonce(annonce)
                .typeDocument(TypeDocumentVerification.TITRE_FONCIER)
                .url("/uploads/docs/doc.pdf")
                .statut(StatutDocument.EN_ATTENTE)
                .build();

        when(documentVerificationRepository.save(any())).thenReturn(doc);
        when(annonceMapper.toDocumentDTO(any())).thenReturn(new DocumentVerificationResponseDTO());

        DocumentVerificationResponseDTO resDoc = annonceService.ajouterDocumentVerification(annonce.getId(), TypeDocumentVerification.TITRE_FONCIER, file);
        assertNotNull(resDoc);

        // Obtenir fichier document
        Resource mockResource = new ByteArrayResource("test".getBytes());
        when(documentVerificationRepository.findById(doc.getId())).thenReturn(Optional.of(doc));
        when(fileStorageService.loadFileAsResource(doc.getUrl())).thenReturn(mockResource);

        Resource resFile = annonceService.obtenirFichierDocumentVerification(annonce.getId(), doc.getId());
        assertNotNull(resFile);
    }

    @Test
    @DisplayName("Décision de vérification admin (VERIFIEE / REJETEE)")
    void testDeciderVerificationAnnonce() {
        when(annonceRepository.findByIdAndSupprimeFalse(annonce.getId())).thenReturn(Optional.of(annonce));

        DocumentVerificationEntity doc = DocumentVerificationEntity.builder()
                .id(UUID.randomUUID())
                .annonce(annonce)
                .statut(StatutDocument.EN_ATTENTE)
                .build();

        when(documentVerificationRepository.findByAnnonceId(annonce.getId())).thenReturn(List.of(doc));
        when(annonceRepository.save(annonce)).thenReturn(annonce);
        when(annonceMapper.toResponseDTO(annonce)).thenReturn(new AnnonceResponseDTO());

        // 1. Décision VERIFIEE -> Document mis à VALIDE
        VerificationDecisionDTO decisionVerif = new VerificationDecisionDTO();
        decisionVerif.setStatutVerification(StatutVerification.VERIFIEE);

        annonceService.deciderVerificationAnnonce(annonce.getId(), decisionVerif);
        assertEquals(StatutVerification.VERIFIEE, annonce.getStatutVerification());
        assertEquals(StatutDocument.VALIDE, doc.getStatut());

        // 2. Décision REJETEE -> Document mis à REJETE avec motif
        VerificationDecisionDTO decisionRejet = new VerificationDecisionDTO();
        decisionRejet.setStatutVerification(StatutVerification.REJETEE);
        decisionRejet.setMotifRejet("Document illisible");

        annonceService.deciderVerificationAnnonce(annonce.getId(), decisionRejet);
        assertEquals(StatutVerification.REJETEE, annonce.getStatutVerification());
        assertEquals(StatutDocument.REJETE, doc.getStatut());
        assertEquals("Document illisible", doc.getMotifRejet());

        // 3. Décision EN_ATTENTE -> Refus 400
        VerificationDecisionDTO decisionAttente = new VerificationDecisionDTO();
        decisionAttente.setStatutVerification(StatutVerification.EN_ATTENTE);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> annonceService.deciderVerificationAnnonce(annonce.getId(), decisionAttente));
        assertEquals(400, ex.getStatusCode().value());
    }

    @Test
    @DisplayName("Lister les annonces en attente de vérification pour les admins")
    void testListerAnnoncesEnAttenteVerification() {
        when(annonceRepository.findByStatutVerificationAndSupprimeFalse(eq(StatutVerification.EN_ATTENTE), any()))
                .thenReturn(new PageImpl<>(List.of(annonce)));
        when(annonceMapper.toResponseDTO(annonce)).thenReturn(new AnnonceResponseDTO());

        Page<AnnonceResponseDTO> page = annonceService.listerAnnoncesEnAttenteVerification(PageRequest.of(0, 10));
        assertEquals(1, page.getTotalElements());
    }
}
