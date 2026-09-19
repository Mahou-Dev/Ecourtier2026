package com.ecourtier.domain.ia.service;

import com.ecourtier.domain.annonce.entity.ModeTransaction;
import com.ecourtier.domain.annonce.entity.TypeBien;
import com.ecourtier.domain.ia.dto.CriteresExtraitsDTO;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class RuleBasedCriteriaExtractor {

    private static final List<String> PROMPT_INJECTION_PATTERNS = List.of(
            "ignore les instructions",
            "ignore previous instructions",
            "forget all instructions",
            "system prompt",
            "act as an unrestricted",
            "disregard previous"
    );

    private static final Pattern BUDGET_RANGE_PATTERN = Pattern.compile("entre\\s+(\\d[\\d\\s]*)\\s+et\\s+(\\d[\\d\\s]*)", Pattern.CASE_INSENSITIVE);
    private static final Pattern BUDGET_SINGLE_PATTERN = Pattern.compile("(?:budget(?:\\s+de)?|max|environ)?\\s*(\\d[\\d\\s]{3,})\\s*(?:fcfa|cfa|f|k)?", Pattern.CASE_INSENSITIVE);
    private static final Pattern PIECES_PATTERN = Pattern.compile("(?:(\\d+)\\s*(?:pièces?|pieces?|chambres?))|(?:f(\\d+))", Pattern.CASE_INSENSITIVE);

    public CriteresExtraitsDTO extractCriteria(String userMessage) {
        sanitizeAndValidate(userMessage);

        String textLower = userMessage.toLowerCase().trim();

        BigDecimal budgetMin = null;
        BigDecimal budgetMax = null;
        TypeBien type = null;
        ModeTransaction modeTransaction = null;
        String quartier = null;
        String ville = null;
        Integer nombrePieces = null;
        Boolean meuble = null;
        Boolean animauxAcceptes = null;
        Double superficieMin = null;

        // 1. Budget extraction
        Matcher rangeMatcher = BUDGET_RANGE_PATTERN.matcher(textLower);
        if (rangeMatcher.find()) {
            budgetMin = parseAmount(rangeMatcher.group(1));
            budgetMax = parseAmount(rangeMatcher.group(2));
        } else {
            Matcher singleMatcher = BUDGET_SINGLE_PATTERN.matcher(textLower);
            if (singleMatcher.find()) {
                BigDecimal val = parseAmount(singleMatcher.group(1));
                if (val != null && val.compareTo(BigDecimal.valueOf(10000)) >= 0) {
                    budgetMax = val;
                }
            }
        }

        // 2. Type Bien
        if (textLower.contains("studio")) {
            type = TypeBien.STUDIO;
        } else if (textLower.contains("villa") || textLower.contains("maison")) {
            type = TypeBien.VILLA;
        } else if (textLower.contains("appartement") || textLower.contains("appat") || textLower.contains("f2") || textLower.contains("f3") || textLower.contains("f4")) {
            type = TypeBien.APPARTEMENT;
        }

        // 3. Mode Transaction
        if (textLower.contains("acheter") || textLower.contains("achat") || textLower.contains("vente") || textLower.contains("vendre")) {
            modeTransaction = ModeTransaction.VENTE;
        } else if (textLower.contains("louer") || textLower.contains("location") || textLower.contains("mois") || textLower.contains("mensuel")) {
            modeTransaction = ModeTransaction.LOCATION;
        }

        // 4. Nombre de pieces
        Matcher piecesMatcher = PIECES_PATTERN.matcher(textLower);
        if (piecesMatcher.find()) {
            String p1 = piecesMatcher.group(1);
            String p2 = piecesMatcher.group(2);
            if (p1 != null) {
                nombrePieces = Integer.parseInt(p1);
            } else if (p2 != null) {
                nombrePieces = Integer.parseInt(p2);
            }
        }

        // 5. Quartiers & Villes
        if (textLower.contains("nema")) {
            quartier = "Nema";
        } else if (textLower.contains("boucotte")) {
            quartier = "Boucotte";
        } else if (textLower.contains("diabir")) {
            quartier = "Diabir";
        } else if (textLower.contains("medina") || textLower.contains("médina")) {
            quartier = "Médina";
        } else if (textLower.contains("almadies")) {
            quartier = "Almadies";
        } else if (textLower.contains("sacre-coeur") || textLower.contains("sacré-cœur") || textLower.contains("sacre coeur")) {
            quartier = "Sacré-Cœur";
        }

        if (textLower.contains("dakar")) {
            ville = "Dakar";
        } else if (textLower.contains("ziguinchor")) {
            ville = "Ziguinchor";
        }

        // 6. Meublé & Animaux
        if (textLower.contains("non meublé") || textLower.contains("non meuble") || textLower.contains("vide")) {
            meuble = false;
        } else if (textLower.contains("meublé") || textLower.contains("meuble")) {
            meuble = true;
        }

        if (textLower.contains("animaux")) {
            animauxAcceptes = true;
        }

        Integer etage = null;
        if (textLower.contains("rdc") || textLower.contains("rez-de-chaussée") || textLower.contains("rez de chaussee")) {
            etage = 0;
        } else {
            Pattern etagePattern = Pattern.compile("(?:(\\d+)(?:er|ème|eme)?\\s*étage)|(?:étage\\s*(\\d+))", Pattern.CASE_INSENSITIVE);
            Matcher etageMatcher = etagePattern.matcher(textLower);
            if (etageMatcher.find()) {
                String e1 = etageMatcher.group(1);
                String e2 = etageMatcher.group(2);
                if (e1 != null) etage = Integer.parseInt(e1);
                else if (e2 != null) etage = Integer.parseInt(e2);
            }
        }

        // 7. Confiance & Question de relance
        int foundCount = 0;
        if (budgetMax != null || budgetMin != null) foundCount++;
        if (type != null) foundCount++;
        if (modeTransaction != null) foundCount++;
        if (quartier != null) foundCount++;
        if (nombrePieces != null) foundCount++;
        if (etage != null) foundCount++;
        if (meuble != null) foundCount++;

        double confiance = Math.min(1.0, 0.4 + (foundCount * 0.15));

        String questionDeRelance = null;
        if (budgetMax == null && budgetMin == null) {
            questionDeRelance = "Quel est votre budget approximatif et le type de bien souhaité (ex: appartement, villa, studio) ?";
        }

        return CriteresExtraitsDTO.builder()
                .budgetMin(budgetMin)
                .budgetMax(budgetMax)
                .type(type)
                .modeTransaction(modeTransaction)
                .quartier(quartier)
                .ville(ville)
                .nombrePieces(nombrePieces)
                .etage(etage)
                .meuble(meuble)
                .animauxAcceptes(animauxAcceptes)
                .superficieMin(superficieMin)
                .confiance(confiance)
                .questionDeRelance(questionDeRelance)
                .build();
    }

    public void sanitizeAndValidate(String userMessage) {
        if (userMessage == null || userMessage.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le message ne peut pas être vide");
        }

        if (userMessage.length() > 500) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le message ne doit pas dépasser 500 caractères");
        }

        String lower = userMessage.toLowerCase();
        for (String pattern : PROMPT_INJECTION_PATTERNS) {
            if (lower.contains(pattern)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Message non autorisé : tentative de manipulation ou injection détectée"
                );
            }
        }
    }

    private BigDecimal parseAmount(String raw) {
        if (raw == null) return null;
        String clean = raw.replaceAll("\\s+", "");
        try {
            return new BigDecimal(clean);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
