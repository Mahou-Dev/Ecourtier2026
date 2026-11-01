package com.ecourtier.domain.admin.service;

import com.ecourtier.domain.admin.dto.ActiviteCommercialeStatsDTO;
import com.ecourtier.domain.admin.dto.AdminStatistiquesDTO;
import com.ecourtier.domain.admin.dto.AnnoncesStatsDTO;
import com.ecourtier.domain.admin.dto.AssistantIAStatsDTO;
import com.ecourtier.domain.admin.dto.ModerationStatsDTO;
import com.ecourtier.domain.admin.dto.RepartitionGeographiqueDTO;
import com.ecourtier.domain.admin.dto.SerieTemporelleDTO;
import com.ecourtier.domain.admin.dto.UtilisateursStatsDTO;
import com.ecourtier.domain.admin.repository.AdminStatistiquesRepository;
import com.ecourtier.domain.annonce.entity.StatutPublication;
import com.ecourtier.domain.annonce.entity.StatutVerification;
import com.ecourtier.domain.annonce.entity.TypeBien;
import com.ecourtier.domain.avis.entity.MotifSignalement;
import com.ecourtier.domain.avis.entity.StatutSignalement;
import com.ecourtier.domain.rendezvous.entity.StatutRendezVous;
import com.ecourtier.domain.utilisateur.entity.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AdminStatistiquesService {

    private final AdminStatistiquesRepository repository;

    @Cacheable(value = "adminStats", key = "#periode != null ? #periode : '30j'")
    @Transactional(readOnly = true)
    public AdminStatistiquesDTO obtenirStatistiques(String periode) {
        String normalizedPeriode = (periode == null || periode.isBlank()) ? "30j" : periode.trim().toLowerCase();
        LocalDateTime dateDebut = calculerDateDebut(normalizedPeriode);

        UtilisateursStatsDTO utilisateurs = construireUtilisateursStats(dateDebut, normalizedPeriode);
        AnnoncesStatsDTO annonces = construireAnnoncesStats();
        ActiviteCommercialeStatsDTO activiteCommerciale = construireActiviteCommercialeStats(dateDebut);
        ModerationStatsDTO moderation = construireModerationStats();
        AssistantIAStatsDTO assistantIA = construireAssistantIAStats(dateDebut);

        return AdminStatistiquesDTO.builder()
                .periode(normalizedPeriode)
                .utilisateurs(utilisateurs)
                .annonces(annonces)
                .activiteCommerciale(activiteCommerciale)
                .moderation(moderation)
                .assistantIA(assistantIA)
                .build();
    }

    private LocalDateTime calculerDateDebut(String periode) {
        LocalDateTime now = LocalDateTime.now();
        return switch (periode) {
            case "7j" -> now.minusDays(7).withHour(0).withMinute(0).withSecond(0).withNano(0);
            case "90j" -> now.minusDays(90).withHour(0).withMinute(0).withSecond(0).withNano(0);
            case "tout" -> LocalDateTime.of(1970, 1, 1, 0, 0, 0);
            default -> now.minusDays(30).withHour(0).withMinute(0).withSecond(0).withNano(0);
        };
    }

    private UtilisateursStatsDTO construireUtilisateursStats(LocalDateTime dateDebut, String periode) {
        Map<String, Long> totalParRole = new LinkedHashMap<>();
        for (Role r : Role.values()) {
            totalParRole.put(r.name(), 0L);
        }
        for (Object[] row : repository.countUtilisateursByRole()) {
            if (row[0] != null) {
                totalParRole.put(((Role) row[0]).name(), (Long) row[1]);
            }
        }

        List<Object[]> rawDaily = repository.countDailyRegistrations(dateDebut);
        Map<String, Long> dailyMap = new LinkedHashMap<>();
        for (Object[] row : rawDaily) {
            if (row[0] != null) {
                String dateStr = row[0].toString();
                dailyMap.put(dateStr, (Long) row[1]);
            }
        }

        List<SerieTemporelleDTO> meps = new ArrayList<>();
        LocalDate start = dateDebut.toLocalDate();
        LocalDate end = LocalDate.now();

        if ("tout".equals(periode) && rawDaily.isEmpty()) {
            start = end;
        } else if ("tout".equals(periode) && !rawDaily.isEmpty()) {
            start = LocalDate.parse(rawDaily.get(0)[0].toString());
        }

        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            String dStr = d.format(DateTimeFormatter.ISO_LOCAL_DATE);
            long val = dailyMap.getOrDefault(dStr, 0L);
            meps.add(new SerieTemporelleDTO(dStr, val));
        }

        return UtilisateursStatsDTO.builder()
                .totalParRole(totalParRole)
                .nouvellesInscriptions(meps)
                .build();
    }

    private AnnoncesStatsDTO construireAnnoncesStats() {
        Map<String, Long> totalParPub = new LinkedHashMap<>();
        for (StatutPublication sp : StatutPublication.values()) {
            totalParPub.put(sp.name(), 0L);
        }
        for (Object[] row : repository.countAnnoncesByStatutPublication()) {
            if (row[0] != null) {
                totalParPub.put(((StatutPublication) row[0]).name(), (Long) row[1]);
            }
        }

        Map<String, Long> totalParVerif = new LinkedHashMap<>();
        for (StatutVerification sv : StatutVerification.values()) {
            totalParVerif.put(sv.name(), 0L);
        }
        for (Object[] row : repository.countAnnoncesByStatutVerification()) {
            if (row[0] != null) {
                totalParVerif.put(((StatutVerification) row[0]).name(), (Long) row[1]);
            }
        }

        List<Object[]> top5Raw = repository.findTopVilleQuartier(PageRequest.of(0, 5));
        List<RepartitionGeographiqueDTO> top5 = new ArrayList<>();
        for (Object[] row : top5Raw) {
            top5.add(new RepartitionGeographiqueDTO((String) row[0], (String) row[1], (Long) row[2]));
        }

        Map<String, Double> avgPrixByType = new LinkedHashMap<>();
        for (TypeBien tb : TypeBien.values()) {
            avgPrixByType.put(tb.name(), 0.0);
        }
        for (Object[] row : repository.findAveragePriceByTypeBien()) {
            if (row[0] != null && row[1] != null) {
                double val = ((Number) row[1]).doubleValue();
                avgPrixByType.put(((TypeBien) row[0]).name(), Math.round(val * 100.0) / 100.0);
            }
        }

        List<Object[]> verifTimes = repository.findVerificationDecisionTimes();
        Double tempsMoyenMinutes = 0.0;
        if (!verifTimes.isEmpty()) {
            long totalMinutes = 0;
            for (Object[] row : verifTimes) {
                LocalDateTime start = (LocalDateTime) row[0];
                LocalDateTime end = (LocalDateTime) row[1];
                if (start != null && end != null) {
                    totalMinutes += Math.abs(Duration.between(start, end).toMinutes());
                }
            }
            tempsMoyenMinutes = Math.round((totalMinutes / (double) verifTimes.size()) * 10.0) / 10.0;
        }

        return AnnoncesStatsDTO.builder()
                .totalParStatutPublication(totalParPub)
                .totalParStatutVerification(totalParVerif)
                .repartitionParVilleQuartier(top5)
                .prixMoyenParTypeBien(avgPrixByType)
                .tempsMoyenDecisionMinutes(tempsMoyenMinutes)
                .build();
    }

    private ActiviteCommercialeStatsDTO construireActiviteCommercialeStats(LocalDateTime dateDebut) {
        Map<String, Long> rdvMap = new LinkedHashMap<>();
        for (StatutRendezVous s : StatutRendezVous.values()) {
            rdvMap.put(s.name(), 0L);
        }
        for (Object[] row : repository.countRendezVousByStatut(dateDebut)) {
            if (row[0] != null) {
                rdvMap.put(((StatutRendezVous) row[0]).name(), (Long) row[1]);
            }
        }

        long countRdvEffectue = repository.countRendezVousEffectues(dateDebut);
        long countTxInitiated = repository.countTransactionsInitiated(dateDebut);
        long countBauxComplet = repository.countBauxSignesComplet(dateDebut);

        Double rdvToTxRate = countRdvEffectue == 0 ? 0.0 : Math.round((countTxInitiated / (double) countRdvEffectue) * 1000.0) / 10.0;
        Double txToBailRate = countTxInitiated == 0 ? 0.0 : Math.round((countBauxComplet / (double) countTxInitiated) * 1000.0) / 10.0;

        BigDecimal totalSequestreBloque = repository.sumMontantSequestreBloque();
        BigDecimal totalLibere = BigDecimal.valueOf(5000).multiply(BigDecimal.valueOf(countBauxComplet));

        return ActiviteCommercialeStatsDTO.builder()
                .nombreRdvParStatut(rdvMap)
                .tauxConversionRdvToTransaction(rdvToTxRate)
                .tauxConversionTransactionToBail(txToBailRate)
                .montantTotalSequestreBloque(totalSequestreBloque)
                .montantTotalLibere(totalLibere)
                .build();
    }

    private ModerationStatsDTO construireModerationStats() {
        Map<String, Long> sigStatutMap = new LinkedHashMap<>();
        for (StatutSignalement s : StatutSignalement.values()) {
            sigStatutMap.put(s.name(), 0L);
        }
        for (Object[] row : repository.countSignalementsByStatut()) {
            if (row[0] != null) {
                sigStatutMap.put(((StatutSignalement) row[0]).name(), (Long) row[1]);
            }
        }

        Map<String, Long> sigMotifMap = new LinkedHashMap<>();
        for (MotifSignalement m : MotifSignalement.values()) {
            sigMotifMap.put(m.name(), 0L);
        }
        for (Object[] row : repository.countSignalementsByMotif()) {
            if (row[0] != null) {
                sigMotifMap.put(((MotifSignalement) row[0]).name(), (Long) row[1]);
            }
        }

        Double noteMoyenne = repository.findNoteMoyenneGlobale();
        noteMoyenne = (noteMoyenne == null) ? 0.0 : Math.round(noteMoyenne * 10.0) / 10.0;

        return ModerationStatsDTO.builder()
                .signalementsParStatut(sigStatutMap)
                .signalementsParMotif(sigMotifMap)
                .noteMoyenneGlobale(noteMoyenne)
                .build();
    }

    private AssistantIAStatsDTO construireAssistantIAStats(LocalDateTime dateDebut) {
        long totalConvs = repository.countConversationsIA(dateDebut);
        long convsShortlist = repository.countConversationsIAWithShortlist(dateDebut);

        Double rate = totalConvs == 0 ? 0.0 : Math.round((convsShortlist / (double) totalConvs) * 1000.0) / 10.0;

        return AssistantIAStatsDTO.builder()
                .nombreConversations(totalConvs)
                .tauxConversationsShortlistNonVide(rate)
                .build();
    }
}
