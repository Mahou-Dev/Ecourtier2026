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
import com.ecourtier.domain.annonce.repository.AnnonceSpecification;
import com.ecourtier.domain.annonce.repository.DocumentVerificationRepository;
import com.ecourtier.domain.annonce.repository.PhotoAnnonceRepository;
import com.ecourtier.domain.utilisateur.entity.UtilisateurEntity;
import com.ecourtier.domain.utilisateur.repository.UtilisateurRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AnnonceService {

    private final AnnonceRepository annonceRepository;
    private final PhotoAnnonceRepository photoAnnonceRepository;
    private final DocumentVerificationRepository documentVerificationRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final FileStorageService fileStorageService;
    private final AnnonceMapper annonceMapper;

    @Transactional
    public AnnonceResponseDTO creerAnnonce(AnnonceCreateDTO dto, String userEmail) {
        UtilisateurEntity user = utilisateurRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Utilisateur non trouvé"));

        AnnonceEntity annonce = AnnonceEntity.builder()
                .titre(dto.getTitre())
                .description(dto.getDescription())
                .type(dto.getType())
                .modeTransaction(dto.getModeTransaction())
                .prix(dto.getPrix())
                .superficie(dto.getSuperficie())
                .nombrePieces(dto.getNombrePieces())
                .etage(dto.getEtage())
                .quartier(dto.getQuartier())
                .ville(dto.getVille())
                .latitude(dto.getLatitude())
                .longitude(dto.getLongitude())
                .meuble(dto.isMeuble())
                .animauxAcceptes(dto.isAnimauxAcceptes())
                .lienVisite360(dto.getLienVisite360())
                .statutVerification(StatutVerification.EN_ATTENTE)
                .statutPublication(StatutPublication.DISPONIBLE)
                .supprime(false)
                .utilisateur(user)
                .equipements(dto.getEquipements() != null ? dto.getEquipements() : new HashSet<>())
                .proximites(dto.getProximites() != null ? dto.getProximites() : new HashSet<>())
                .build();

        AnnonceEntity saved = annonceRepository.save(annonce);
        return annonceMapper.toResponseDTO(saved);
    }

    @Transactional(readOnly = true)
    public AnnonceResponseDTO obtenirAnnonceParId(UUID id) {
        AnnonceEntity annonce = annonceRepository.findByIdAndSupprimeFalse(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Annonce introuvable ou supprimée"));
        return annonceMapper.toResponseDTO(annonce);
    }

    @Transactional
    public AnnonceResponseDTO modifierAnnonce(UUID id, AnnonceUpdateDTO dto) {
        AnnonceEntity annonce = annonceRepository.findByIdAndSupprimeFalse(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Annonce introuvable ou supprimée"));

        if (dto.getTitre() != null) annonce.setTitre(dto.getTitre());
        if (dto.getDescription() != null) annonce.setDescription(dto.getDescription());
        if (dto.getType() != null) annonce.setType(dto.getType());
        if (dto.getModeTransaction() != null) annonce.setModeTransaction(dto.getModeTransaction());
        if (dto.getPrix() != null) annonce.setPrix(dto.getPrix());
        if (dto.getSuperficie() != null) annonce.setSuperficie(dto.getSuperficie());
        if (dto.getNombrePieces() != null) annonce.setNombrePieces(dto.getNombrePieces());
        if (dto.getEtage() != null) annonce.setEtage(dto.getEtage());
        if (dto.getQuartier() != null) annonce.setQuartier(dto.getQuartier());
        if (dto.getVille() != null) annonce.setVille(dto.getVille());
        if (dto.getLatitude() != null) annonce.setLatitude(dto.getLatitude());
        if (dto.getLongitude() != null) annonce.setLongitude(dto.getLongitude());
        if (dto.getMeuble() != null) annonce.setMeuble(dto.getMeuble());
        if (dto.getAnimauxAcceptes() != null) annonce.setAnimauxAcceptes(dto.getAnimauxAcceptes());
        if (dto.getLienVisite360() != null) annonce.setLienVisite360(dto.getLienVisite360());
        if (dto.getStatutPublication() != null) annonce.setStatutPublication(dto.getStatutPublication());
        if (dto.getEquipements() != null) annonce.setEquipements(dto.getEquipements());
        if (dto.getProximites() != null) annonce.setProximites(dto.getProximites());

        AnnonceEntity updated = annonceRepository.save(annonce);
        return annonceMapper.toResponseDTO(updated);
    }

    @Transactional
    public void supprimerAnnonceLogique(UUID id) {
        AnnonceEntity annonce = annonceRepository.findByIdAndSupprimeFalse(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Annonce introuvable ou déjà supprimée"));

        annonce.setSupprime(true);
        annonceRepository.save(annonce);
    }

    @Transactional(readOnly = true)
    public Page<AnnonceResponseDTO> rechercherAnnonces(
            Pageable pageable,
            BigDecimal budgetMin,
            BigDecimal budgetMax,
            TypeBien type,
            ModeTransaction modeTransaction,
            Double superficieMin,
            Integer nombrePieces,
            String quartier,
            String ville,
            Boolean meuble,
            Boolean animauxAcceptes,
            StatutVerification statutVerification,
            ProximiteTag proximite
    ) {
        return rechercherAnnonces(pageable, budgetMin, budgetMax, type, modeTransaction, superficieMin,
                nombrePieces, quartier, ville, meuble, animauxAcceptes, statutVerification, proximite, false);
    }

    @Transactional(readOnly = true)
    public Page<AnnonceResponseDTO> rechercherAnnonces(
            Pageable pageable,
            BigDecimal budgetMin,
            BigDecimal budgetMax,
            TypeBien type,
            ModeTransaction modeTransaction,
            Double superficieMin,
            Integer nombrePieces,
            String quartier,
            String ville,
            Boolean meuble,
            Boolean animauxAcceptes,
            StatutVerification statutVerification,
            ProximiteTag proximite,
            Boolean includeIndisponibles
    ) {
        return rechercherAnnonces(pageable, budgetMin, budgetMax, type, modeTransaction, superficieMin,
                nombrePieces, quartier, ville, meuble, animauxAcceptes, statutVerification, proximite, includeIndisponibles, null);
    }

    @Transactional(readOnly = true)
    public Page<AnnonceResponseDTO> rechercherAnnonces(
            Pageable pageable,
            BigDecimal budgetMin,
            BigDecimal budgetMax,
            TypeBien type,
            ModeTransaction modeTransaction,
            Double superficieMin,
            Integer nombrePieces,
            String quartier,
            String ville,
            Boolean meuble,
            Boolean animauxAcceptes,
            StatutVerification statutVerification,
            ProximiteTag proximite,
            Boolean includeIndisponibles,
            String auteurEmail
    ) {
        Specification<AnnonceEntity> spec = AnnonceSpecification.filterAnnonces(
                budgetMin, budgetMax, type, modeTransaction, superficieMin,
                nombrePieces, quartier, ville, meuble, animauxAcceptes,
                statutVerification, proximite, includeIndisponibles, auteurEmail
        );

        return annonceRepository.findAll(spec, pageable)
                .map(annonceMapper::toResponseDTO);
    }

    @Transactional
    public List<PhotoResponseDTO> ajouterPhotos(UUID id, List<MultipartFile> files) {
        AnnonceEntity annonce = annonceRepository.findByIdAndSupprimeFalse(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Annonce introuvable"));

        long existingCount = photoAnnonceRepository.countByAnnonceId(id);
        if (files == null || files.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Aucun fichier fourni");
        }

        if (existingCount + files.size() > 10) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Nombre maximal de 10 photos par annonce dépassé (actuelles: " + existingCount + ", nouvelles: " + files.size() + ")"
            );
        }

        List<PhotoResponseDTO> result = new ArrayList<>();
        int startOrdre = (int) existingCount;

        for (int i = 0; i < files.size(); i++) {
            MultipartFile file = files.get(i);
            String url = fileStorageService.storePhotoAnnonce(id, file);

            PhotoAnnonceEntity photo = PhotoAnnonceEntity.builder()
                    .annonce(annonce)
                    .url(url)
                    .ordre(startOrdre + i)
                    .build();

            PhotoAnnonceEntity saved = photoAnnonceRepository.save(photo);
            result.add(annonceMapper.toPhotoDTO(saved));
        }

        return result;
    }

    @Transactional
    public void supprimerPhoto(UUID id, UUID photoId) {
        PhotoAnnonceEntity photo = photoAnnonceRepository.findById(photoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Photo introuvable"));

        if (!photo.getAnnonce().getId().equals(id)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La photo n'appartient pas à cette annonce");
        }

        fileStorageService.deleteFile(photo.getUrl());
        photoAnnonceRepository.delete(photo);
    }

    @Transactional
    public List<PhotoResponseDTO> reordonnerPhotos(UUID id, OrdrePhotosDTO dto) {
        AnnonceEntity annonce = annonceRepository.findByIdAndSupprimeFalse(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Annonce introuvable"));

        List<UUID> photoIds = dto.getPhotoIdsEnOrdre();
        List<PhotoAnnonceEntity> photos = photoAnnonceRepository.findByAnnonceIdOrderByOrdreAsc(id);

        for (int i = 0; i < photoIds.size(); i++) {
            UUID photoId = photoIds.get(i);
            for (PhotoAnnonceEntity p : photos) {
                if (p.getId().equals(photoId)) {
                    p.setOrdre(i);
                    photoAnnonceRepository.save(p);
                    break;
                }
            }
        }

        return photoAnnonceRepository.findByAnnonceIdOrderByOrdreAsc(id)
                .stream()
                .map(annonceMapper::toPhotoDTO)
                .toList();
    }

    @Transactional
    public DocumentVerificationResponseDTO ajouterDocumentVerification(
            UUID id,
            TypeDocumentVerification typeDocument,
            MultipartFile file
    ) {
        AnnonceEntity annonce = annonceRepository.findByIdAndSupprimeFalse(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Annonce introuvable"));

        String url = fileStorageService.storeDocumentVerification(id, file);

        DocumentVerificationEntity doc = DocumentVerificationEntity.builder()
                .annonce(annonce)
                .typeDocument(typeDocument)
                .url(url)
                .statut(StatutDocument.EN_ATTENTE)
                .build();

        DocumentVerificationEntity saved = documentVerificationRepository.save(doc);
        return annonceMapper.toDocumentDTO(saved);
    }

    @Transactional(readOnly = true)
    public org.springframework.core.io.Resource obtenirFichierDocumentVerification(UUID annonceId, UUID documentId) {
        DocumentVerificationEntity doc = documentVerificationRepository.findById(documentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document introuvable"));

        if (!doc.getAnnonce().getId().equals(annonceId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le document n'appartient pas à cette annonce");
        }

        return fileStorageService.loadFileAsResource(doc.getUrl());
    }

    @Transactional(readOnly = true)
    public Page<AnnonceResponseDTO> listerAnnoncesEnAttenteVerification(Pageable pageable) {
        return annonceRepository.findByStatutVerificationAndSupprimeFalse(StatutVerification.EN_ATTENTE, pageable)
                .map(annonceMapper::toResponseDTO);
    }

    @Transactional
    public AnnonceResponseDTO deciderVerificationAnnonce(UUID id, VerificationDecisionDTO decision) {
        AnnonceEntity annonce = annonceRepository.findByIdAndSupprimeFalse(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Annonce introuvable"));

        if (decision.getStatutVerification() == StatutVerification.EN_ATTENTE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La décision doit être VERIFIEE ou REJETEE");
        }

        annonce.setStatutVerification(decision.getStatutVerification());

        // Update status of associated documents
        List<DocumentVerificationEntity> docs = documentVerificationRepository.findByAnnonceId(id);
        StatutDocument documentStatus = decision.getStatutVerification() == StatutVerification.VERIFIEE ?
                StatutDocument.VALIDE : StatutDocument.REJETE;

        for (DocumentVerificationEntity doc : docs) {
            doc.setStatut(documentStatus);
            if (decision.getStatutVerification() == StatutVerification.REJETEE) {
                doc.setMotifRejet(decision.getMotifRejet());
            }
            documentVerificationRepository.save(doc);
        }

        AnnonceEntity saved = annonceRepository.save(annonce);
        return annonceMapper.toResponseDTO(saved);
    }
}
