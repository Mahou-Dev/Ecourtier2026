package com.ecourtier.domain.ia.service;

import com.ecourtier.domain.annonce.entity.ModeTransaction;
import com.ecourtier.domain.annonce.entity.TypeBien;
import com.ecourtier.domain.ia.dto.CriteresExtraitsDTO;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
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

    private static final Map<String, Integer> MOT_VERS_NOMBRE = Map.ofEntries(
            Map.entry("un", 1), Map.entry("une", 1),
            Map.entry("deux", 2),
            Map.entry("trois", 3),
            Map.entry("quatre", 4),
            Map.entry("cinq", 5),
            Map.entry("six", 6),
            Map.entry("sept", 7),
            Map.entry("huit", 8),
            Map.entry("neuf", 9),
            Map.entry("dix", 10)
    );

    private static final Map<String, Integer> ORDINAUX = Map.ofEntries(
            Map.entry("premier", 1), Map.entry("première", 1), Map.entry("premiere", 1),
            Map.entry("deuxième", 2), Map.entry("deuxieme", 2), Map.entry("second", 2), Map.entry("seconde", 2),
            Map.entry("troisième", 3), Map.entry("troisieme", 3),
            Map.entry("quatrième", 4), Map.entry("quatrieme", 4),
            Map.entry("cinquième", 5), Map.entry("cinquieme", 5),
            Map.entry("sixième", 6), Map.entry("sixieme", 6),
            Map.entry("septième", 7), Map.entry("septieme", 7),
            Map.entry("huitième", 8), Map.entry("huitieme", 8),
            Map.entry("neuvième", 9), Map.entry("neuvieme", 9),
            Map.entry("dixième", 10), Map.entry("dixieme", 10)
    );

    private static final Pattern BUDGET_RANGE_PATTERN = Pattern.compile("entre\\s+(\\d[\\d\\s]*)\\s+et\\s+(\\d[\\d\\s]*)", Pattern.CASE_INSENSITIVE);
    private static final Pattern BUDGET_SINGLE_PATTERN = Pattern.compile("(?:budget(?:\\s+de)?|max|environ)?\\s*(\\d[\\d\\s]{3,})\\s*(?:fcfa|cfa|f|k)?", Pattern.CASE_INSENSITIVE);
    private static final Pattern PIECES_PATTERN = Pattern.compile(
            "(?:(un|une|deux|trois|quatre|cinq|six|sept|huit|neuf|dix|\\d+)\\s*(?:pièces?|pieces?))|(?:f(\\d+))",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern SUPERFICIE_PATTERN = Pattern.compile(
            "(\\d+(?:[.,]\\d+)?)\\s*(?:m²|m2|mètres\\s+carrés|metres\\s+carres|mètres\\s+carre|metres\\s+carre)",
            Pattern.CASE_INSENSITIVE
    );

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

        // 4. Nombre de pieces (exclusion stricte de 'chambres' car non présent dans le schéma AnnonceEntity)
        Matcher piecesMatcher = PIECES_PATTERN.matcher(textLower);
        if (piecesMatcher.find()) {
            String p1 = piecesMatcher.group(1);
            String p2 = piecesMatcher.group(2);
            if (p1 != null) {
                String p1Lower = p1.toLowerCase();
                if (MOT_VERS_NOMBRE.containsKey(p1Lower)) {
                    nombrePieces = MOT_VERS_NOMBRE.get(p1Lower);
                } else {
                    try {
                        nombrePieces = Integer.parseInt(p1);
                    } catch (NumberFormatException ignored) {}
                }
            } else if (p2 != null) {
                try {
                    nombrePieces = Integer.parseInt(p2);
                } catch (NumberFormatException ignored) {}
            }
        }

        // 5. Superficie minimale
        Matcher supMatcher = SUPERFICIE_PATTERN.matcher(textLower);
        if (supMatcher.find()) {
            String valStr = supMatcher.group(1).replace(',', '.');
            try {
                superficieMin = Double.parseDouble(valStr);
            } catch (NumberFormatException ignored) {}
        }

        // 6. Quartiers & Villes
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

        // 7. Meublé & Animaux
        if (textLower.contains("non meublé") || textLower.contains("non meuble") || textLower.contains("vide")) {
            meuble = false;
        } else if (textLower.contains("meublé") || textLower.contains("meuble")) {
            meuble = true;
        }

        if (textLower.contains("pas d'animaux") || textLower.contains("pas d'animal")
                || textLower.contains("sans animaux") || textLower.contains("sans animal")
                || textLower.contains("animaux interdits") || textLower.contains("animaux non acceptés")
                || textLower.contains("animaux non acceptes") || textLower.contains("animaux refusés")
                || textLower.contains("animaux refuses")) {
            animauxAcceptes = false;
        } else if (textLower.contains("animaux") || textLower.contains("animal")
                || textLower.contains("chat") || textLower.contains("chien")) {
            animauxAcceptes = true;
        }

        // 8. Étage (ordinaux en lettres, chiffres et RDC)
        Integer etage = null;
        if (textLower.contains("rdc") || textLower.contains("rez-de-chaussée") || textLower.contains("rez de chaussee") || textLower.contains("rez-de-chaussee")) {
            etage = 0;
        } else {
            Pattern ordinalPattern = Pattern.compile(
                    "\\b(premier|première|premiere|deuxième|deuxieme|second|seconde|troisième|troisieme|quatrième|quatrieme|cinquième|cinquieme|sixième|sixieme|septième|septieme|huitième|huitieme|neuvième|neuvieme|dixième|dixieme)\\s+étages?",
                    Pattern.CASE_INSENSITIVE
            );
            Matcher ordMatcher = ordinalPattern.matcher(textLower);
            if (ordMatcher.find()) {
                etage = ORDINAUX.get(ordMatcher.group(1).toLowerCase());
            } else {
                Pattern etagePattern = Pattern.compile("(?:(\\d+)(?:er|ère|ere|ème|eme)?\\s*étages?)|(?:étages?\\s*(\\d+))", Pattern.CASE_INSENSITIVE);
                Matcher etageMatcher = etagePattern.matcher(textLower);
                if (etageMatcher.find()) {
                    String e1 = etageMatcher.group(1);
                    String e2 = etageMatcher.group(2);
                    if (e1 != null) etage = Integer.parseInt(e1);
                    else if (e2 != null) etage = Integer.parseInt(e2);
                }
            }
        }

        // 9. Confiance & Question de relance
        int foundCount = 0;
        if (budgetMax != null || budgetMin != null) foundCount++;
        if (type != null) foundCount++;
        if (modeTransaction != null) foundCount++;
        if (quartier != null) foundCount++;
        if (nombrePieces != null) foundCount++;
        if (etage != null) foundCount++;
        if (meuble != null) foundCount++;
        if (animauxAcceptes != null) foundCount++;
        if (superficieMin != null) foundCount++;

        double confiance = Math.min(1.0, 0.4 + (foundCount * 0.1));

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
