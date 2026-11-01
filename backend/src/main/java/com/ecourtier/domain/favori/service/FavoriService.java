package com.ecourtier.domain.favori.service;

import com.ecourtier.domain.annonce.dto.AnnonceResponseDTO;
import com.ecourtier.domain.annonce.entity.AnnonceEntity;
import com.ecourtier.domain.annonce.mapper.AnnonceMapper;
import com.ecourtier.domain.annonce.repository.AnnonceRepository;
import com.ecourtier.domain.favori.entity.FavoriEntity;
import com.ecourtier.domain.favori.repository.FavoriRepository;
import com.ecourtier.domain.utilisateur.entity.UtilisateurEntity;
import com.ecourtier.domain.utilisateur.repository.UtilisateurRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FavoriService {

    private final FavoriRepository favoriRepository;
    private final AnnonceRepository annonceRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final AnnonceMapper annonceMapper;

    @Transactional
    public void ajouterFavori(UUID annonceId, String userEmail) {
        UtilisateurEntity user = utilisateurRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Utilisateur non trouvé"));

        AnnonceEntity annonce = annonceRepository.findByIdAndSupprimeFalse(annonceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Annonce introuvable ou supprimée"));

        // Fast pre-check for duplicate
        if (favoriRepository.existsByUtilisateurIdAndAnnonceId(user.getId(), annonceId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cette annonce est déjà dans vos favoris");
        }

        FavoriEntity favori = FavoriEntity.builder()
                .utilisateur(user)
                .annonce(annonce)
                .build();

        try {
            favoriRepository.save(favori);
        } catch (DataIntegrityViolationException e) {
            // Race condition: another request inserted the same favori between our check and save
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cette annonce est déjà dans vos favoris");
        }
    }

    @Transactional
    public void supprimerFavori(UUID annonceId, String userEmail) {
        UtilisateurEntity user = utilisateurRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Utilisateur non trouvé"));

        FavoriEntity favori = favoriRepository.findByUtilisateurIdAndAnnonceId(user.getId(), annonceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ce favori n'existe pas"));

        favoriRepository.delete(favori);
    }

    @Transactional(readOnly = true)
    public Page<AnnonceResponseDTO> listerFavoris(String userEmail, Pageable pageable) {
        UtilisateurEntity user = utilisateurRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Utilisateur non trouvé"));

        Set<UUID> favorisIds = new HashSet<>(favoriRepository.findAnnonceIdsByUtilisateurId(user.getId()));

        return favoriRepository.findByUtilisateurIdOrderByDateAjoutDesc(user.getId(), pageable)
                .map(favori -> annonceMapper.toResponseDTO(favori.getAnnonce(), favorisIds));
    }

    @Transactional(readOnly = true)
    public Set<UUID> getAnnonceIdsFavorites(String userEmail) {
        if (userEmail == null) {
            return Set.of();
        }
        return utilisateurRepository.findByEmail(userEmail)
                .map(user -> new HashSet<>(favoriRepository.findAnnonceIdsByUtilisateurId(user.getId())))
                .orElse(new HashSet<>());
    }

    @Transactional(readOnly = true)
    public long compterFavoris(String userEmail) {
        UtilisateurEntity user = utilisateurRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Utilisateur non trouvé"));

        return favoriRepository.countByUtilisateurId(user.getId());
    }
}
