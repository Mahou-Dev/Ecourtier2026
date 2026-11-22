package com.ecourtier.domain.ia.service;

import com.ecourtier.domain.annonce.entity.Equipement;
import com.ecourtier.domain.annonce.entity.ModeTransaction;
import com.ecourtier.domain.annonce.entity.ProximiteTag;
import com.ecourtier.domain.annonce.entity.TypeBien;
import com.ecourtier.domain.ia.dto.CriteresExtraitsDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracteur déterministe de critères immobiliers (NLU local & fallback de dernier recours)
 * et filtre de sécurité multicouche contre les injections de prompt (ANO-IA-04, ANO-IA-05).
 */
@Service
@Slf4j
public class RuleBasedCriteriaExtractor {

    private static final int MAX_MESSAGE_LENGTH = 1000;
    private static final int REGEX_FLAGS = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.DOTALL;

    private static final Pattern SOFT_MEUBLE_PATTERN = Pattern.compile(
            "(?:(?:je\\s+)?prefererais|prefere|si\\s+possible|de\\s+preference|idealement|eventuellement|ce\\s+serait\\s+(?:bien|un\\s+plus)|serait\\s+un\\s+plus).{0,40}\\bmeubles?\\b|\\bmeubles?\\b\\s*(?:de\\s+preference|si\\s+possible|serait\\s+un\\s+plus)",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern LEGAL_TRANSPARENCY_PATTERN = Pattern.compile(
            "(?:titres?\\s+fonciers?|titre\\s+de\\s+propriete|acte\\s+notarie|\\bnotaire\\b|authenticite|honnete(?:te)?|\\barnaques?\\b|escroquerie|fraude|faux\\s+(?:courtiers?|documents?|titres?|proprietaires?)|badge\\s+verifie.*(?:sur|juridique|garant|signifi)|(?:garanti|certifi|sur(?:e|s)?).{0,40}(?:annonces?|proprietaire|bailleur|titre|document|authentique|juridique)|(?:annonces?|proprietaires?|bailleurs?).{0,30}(?:fiables?|authentiques?|verifies?)|comment\\s+(?:verifiez|protegez|fonctionne\\s+la\\s+verification)|verification\\s+des\\s+(?:annonces?|proprietaires?|bailleurs?|baux|bail)|(?:payer|verser|regler).{0,25}avant\\s+(?:de\\s+)?(?:voir|visiter|les\\s+documents)|avant\\s+de\\s+signer\\s+(?:un|le)?\\s*bail)",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern TYPE_MENTION_PATTERN = Pattern.compile(
            "\\b(studios?|villas?|maisons?|duplex|appartements?|appart|[ft][2-6]|chambres?)\\b",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );

    private static final List<Pattern> PROMPT_INJECTION_PATTERNS = List.of(
            Pattern.compile("(ignore|oublie|disregard|forget|bypass|contourne|override)\\s+.*?(instruction|consigne|règle|regle|rule|previous|précédent|precedent|system|système|systeme|prompt)", REGEX_FLAGS),
            Pattern.compile("(system\\s*prompt|prompt\\s*syst[èe]me|instructions?\\s*syst[èe]me|consignes?\\s*internes?|developer\\s*mode|mode\\s*d[ée]veloppeur|jailbreak|system\\s*override)", REGEX_FLAGS),
            Pattern.compile("(act\\s+as|you\\s+are\\s+now|tu\\s+es\\s+maintenant|agis\\s+comme|nouveau\\s+r[ôo]le)\\s+.*?(unrestricted|admin|root|hacker|dan|sans\\s+restriction|sans\\s+filtre)", REGEX_FLAGS),
            Pattern.compile("(donne|affiche|r[ée]v[èe]le|montre|print|dump|reveal|show|expose|quelle\\s+est|quel\\s+est).*?(mot\\s+de\\s+passe|password|secret|token|api[_\\s-]*key|cl[ée]\\s*(api|secr[èe]te|groq|gemini|openai|claude)|jwt_secret|gemini_api_key|groq_api_key)", REGEX_FLAGS),
            Pattern.compile("(api[_\\s-]*key|cl[ée]\\s*api|groq_api_key|gemini_api_key|openai_api_key|anthropic_api_key)", REGEX_FLAGS),
            Pattern.compile("(drop\\s+table|union\\s+select|information_schema|pg_catalog|/etc/passwd|printenv)", REGEX_FLAGS)
    );

    private static final Pattern OFF_TOPIC_OBJECT_PATTERN = Pattern.compile(
            "\\b(voitures?|v[ée]hicules?|motos?|scooters?|camions?|t[ée]l[ée]phones?|iphones?|samsungs?|ordinateurs?|laptops?|billets?\\s+d'avion|recettes?\\s+de\\s+cuisine|matchs?\\s+de\\s+foot)\\b",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );

    private static final Pattern REAL_ESTATE_CONTEXT_PATTERN = Pattern.compile(
            "\\b(appartements?|appart|villas?|maisons?|duplex|studios?|chambres?|logements?|biens?|immobiliers?|louer|location|acheter|achat|vente|pi[èe]ces?|f[1-6]|t[1-6]|meubl[ée]s?|m2|m²|étage|etage)\\b",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );

    private static final Pattern BUDGET_RANGE_PATTERN = Pattern.compile(
            "\\bentre\\s+(\\d{1,3}(?:[\\s.,]?\\d{3})+|\\d{2,9})\\s*(k|mille|millions?)?\\s*(?:fcfa|cfa|f|francs?)?\\s+et\\s+(\\d{1,3}(?:[\\s.,]?\\d{3})+|\\d{2,9})\\s*(k|mille|millions?)?\\b",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );

    private static final Pattern BUDGET_EXPLICIT_PATTERN = Pattern.compile(
            "(?:budget|prix|loyer|max|maximum|moins\\s+de|jusqu'[àa]|pour|autour\\s+de|environ|vers|[àa])\\s*(?:est\\s+de|de|max|maximum|[àa])?\\s*(\\d{1,3}(?:[\\s.,]?\\d{3})+|\\d{2,9})\\s*(k|mille|millions?)?(?:\\s*(?:fcfa|cfa|f|francs?))?\\b",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );

    private static final Pattern BUDGET_WITH_CURRENCY_PATTERN = Pattern.compile(
            "\\b(\\d{1,3}(?:[\\s.,]?\\d{3})+|\\d{2,9})\\s*(k|mille|millions?)?\\s*(fcfa|cfa|francs?|f\\b)|\\b(\\d{2,4})\\s*(k)\\b",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );

    private static final Pattern BUDGET_FALLBACK_LARGE_NUMBER = Pattern.compile(
            "\\b(\\d{1,3}(?:[\\s.]\\d{3})+|[1-9]\\d{4,8})\\b"
    );

    private static final Pattern PIECES_PATTERN = Pattern.compile(
            "\\b(?:(\\d+)\\s*(?:pi[èe]ces?|p\\b)|[ft](\\d+)\\b|(une|un|deux|trois|quatre|cinq|six|sept|huit)\\s+pi[èe]ces?)\\b",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );

    private static final Pattern CHAMBRES_PATTERN = Pattern.compile(
            "\\b(?:(\\d+)|(une|un|deux|trois|quatre|cinq|six))\\s+chambres?\\b",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );

    private static final Pattern SUPERFICIE_MIN_PATTERN = Pattern.compile(
            "(?:(?:au\\s+moins|min|minimum|plus\\s+de|[àa]\\s+partir\\s+de)\\s*)?(\\d+(?:[.,]\\d+)?)\\s*(?:m2\\b|m²|m[èe]tres?\\s*carr[ée]s?\\b)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );

    private static final Pattern SUPERFICIE_MAX_PATTERN = Pattern.compile(
            "(?:maximum|max|moins\\s+de|jusqu'[àa])\\s*(\\d+(?:[.,]\\d+)?)\\s*(?:m2\\b|m²|m[èe]tres?\\s*carr[ée]s?\\b)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );

    private static final Pattern ETAGE_PATTERN = Pattern.compile(
            "\\b(?:(rdc|rez[-\\s]de[-\\s]chauss[ée]e)|(?:au\\s+)?(\\d+)\\s*(?:er|e|[èe]me|eme)?\\s*[ée]tage|[ée]tage\\s*(\\d+)|(premier|1er|deuxi[èe]me|second|2e|troisi[èe]me|3e|quatri[èe]me|4e|cinqui[èe]me|5e)\\s*[ée]tage)\\b",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );

    /**
     * Dictionnaire ordonné des quartiers du Sénégal (du plus spécifique au plus général).
     */
    private static final Map<String, String> QUARTIERS_CONNUS = new LinkedHashMap<>();
    static {
        // Dakar
        QUARTIERS_CONNUS.put("sacre-coeur", "Sacré-Cœur");
        QUARTIERS_CONNUS.put("sacre coeur", "Sacré-Cœur");
        QUARTIERS_CONNUS.put("liberte 6", "Liberté 6");
        QUARTIERS_CONNUS.put("liberte 5", "Liberté 5");
        QUARTIERS_CONNUS.put("liberte 4", "Liberté 4");
        QUARTIERS_CONNUS.put("liberte 3", "Liberté 3");
        QUARTIERS_CONNUS.put("liberte 2", "Liberté 2");
        QUARTIERS_CONNUS.put("liberte 1", "Liberté 1");
        QUARTIERS_CONNUS.put("sicap liberte", "Liberté");
        QUARTIERS_CONNUS.put("liberte", "Liberté");
        QUARTIERS_CONNUS.put("fann hock", "Fann Hock");
        QUARTIERS_CONNUS.put("fann residence", "Fann Résidence");
        QUARTIERS_CONNUS.put("fann", "Fann");
        QUARTIERS_CONNUS.put("point e", "Point E");
        QUARTIERS_CONNUS.put("parcelles assainies", "Parcelles Assainies");
        QUARTIERS_CONNUS.put("hann maristes", "Hann Maristes");
        QUARTIERS_CONNUS.put("maristes", "Hann Maristes");
        QUARTIERS_CONNUS.put("keur gorgui", "Cité Keur Gorgui");
        QUARTIERS_CONNUS.put("ouest foire", "Ouest Foire");
        QUARTIERS_CONNUS.put("nord foire", "Nord Foire");
        QUARTIERS_CONNUS.put("sud foire", "Sud Foire");
        QUARTIERS_CONNUS.put("patte d'oie", "Patte d'Oie");
        QUARTIERS_CONNUS.put("grand yoff", "Grand Yoff");
        QUARTIERS_CONNUS.put("almadies", "Almadies");
        QUARTIERS_CONNUS.put("mamelles", "Mamelles");
        QUARTIERS_CONNUS.put("mermoz", "Mermoz");
        QUARTIERS_CONNUS.put("plateau", "Plateau");
        QUARTIERS_CONNUS.put("ouakam", "Ouakam");
        QUARTIERS_CONNUS.put("ngor", "Ngor");
        QUARTIERS_CONNUS.put("yoff", "Yoff");
        QUARTIERS_CONNUS.put("medina", "Médina");
        QUARTIERS_CONNUS.put("dieuppeul", "Dieuppeul");
        QUARTIERS_CONNUS.put("hlm", "HLM");
        // Ziguinchor & régions
        QUARTIERS_CONNUS.put("nema", "Nema");
        QUARTIERS_CONNUS.put("boucotte", "Boucotte");
        QUARTIERS_CONNUS.put("diabir", "Diabir");
        QUARTIERS_CONNUS.put("santhiaba", "Santhiaba");
        QUARTIERS_CONNUS.put("escale", "Escale");
        QUARTIERS_CONNUS.put("kande", "Kandé");
        QUARTIERS_CONNUS.put("tilene", "Tilène");
        QUARTIERS_CONNUS.put("lyndiane", "Lyndiane");
        QUARTIERS_CONNUS.put("saly", "Saly");
        QUARTIERS_CONNUS.put("somone", "Somone");
        QUARTIERS_CONNUS.put("ngaparou", "Ngaparou");
    }

    private static final Set<String> QUARTIERS_DAKAR = Set.of(
            "Sacré-Cœur", "Liberté 6", "Liberté 5", "Liberté 4", "Liberté 3", "Liberté 2", "Liberté 1", "Liberté",
            "Fann Hock", "Fann Résidence", "Fann", "Point E", "Parcelles Assainies", "Hann Maristes",
            "Cité Keur Gorgui", "Ouest Foire", "Nord Foire", "Sud Foire", "Patte d'Oie", "Grand Yoff",
            "Almadies", "Mamelles", "Mermoz", "Plateau", "Ouakam", "Ngor", "Yoff", "Médina", "Dieuppeul", "HLM"
    );

    /**
     * Valide la longueur et l'absence de tentative de prompt injection (ANO-IA-05).
     */
    public String validateAndSanitize(String rawMessage) {
        if (rawMessage == null || rawMessage.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le message ne peut pas être vide");
        }
        if (rawMessage.length() > MAX_MESSAGE_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le message dépasse la limite de 1000 caractères");
        }
        String sanitized = rawMessage.trim();
        for (Pattern pattern : PROMPT_INJECTION_PATTERNS) {
            if (pattern.matcher(sanitized).find()) {
                log.warn("Tentative de Prompt Injection bloquée par le pare-feu IA");
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Requête invalide ou non autorisée par la politique de sécurité");
            }
        }
        return sanitized;
    }

    public CriteresExtraitsDTO extractCriteria(String rawMessage) {
        String sanitized = validateAndSanitize(rawMessage);
        String lower = sanitized.toLowerCase(Locale.ROOT);
        String normalized = normalizeAccents(lower);

        CriteresExtraitsDTO.CriteresExtraitsDTOBuilder builder = CriteresExtraitsDTO.builder();

        // 0. Détection de demande manifestement hors sujet immobilier (ex: voiture à 200 000 FCFA)
        if (isOffTopicRequest(lower)) {
            builder.horsSujet(true);
            builder.confiance(0.2);
            builder.questionDeRelance("Je suis Samba, l'assistant immobilier d'E-Courtier Sénégal. Je traite exclusivement les recherches de logements (chambres, studios, appartements, villas à louer ou à vendre au Sénégal). Quel type de logement recherchez-vous ?");
            builder.fournisseurIa("fallback-local");
            builder.modeSecours(true);
            return builder.build();
        }

        // 0b. Détection des intentions conversationnelles spéciales (explication, FAQ documents, transparence juridique, liste)
        String specialIntent = detectSpecialIntent(lower, normalized);
        if (specialIntent != null) {
            builder.intentionSpeciale(specialIntent);
            if ("FAQ_VERIFICATION_JURIDIQUE".equals(specialIntent)) {
                builder.fournisseurIa("fallback-local");
                builder.modeSecours(true);
                builder.confiance(0.9);
                return builder.build();
            }
        }

        boolean needClarificationSignal = normalized.contains("je ne sais pas")
                || normalized.contains("pas encore exactement")
                || normalized.contains("j'hesite")
                || normalized.contains("besoin d'aide");
        if (needClarificationSignal) {
            builder.besoinIndecis(true);
        }
        if (normalized.contains("poser les questions")
                || normalized.contains("questions necessaires")
                || normalized.contains("informations te manquent")
                || normalized.contains("informations manquantes")
                || normalized.contains("questions complementaires")) {
            builder.demandeQuestionsComplementaires(true);
        }

        // 0c. Détection des demandes de retrait d'un critère en multi-tours (ex: "sans critère de quartier")
        Set<String> removals = detectCriteriaToRemove(lower, normalized);
        if (!removals.isEmpty()) {
            builder.criteresASupprimer(removals);
        }

        // 1. Extraction du type de bien et contrôle du périmètre résidentiel
        boolean nonResidentielDetecte = isNonResidentiel(lower);
        TypeBien type = nonResidentielDetecte ? null : extractTypeBien(lower);
        builder.type(type);

        // 2. Mode de transaction (LOCATION vs VENTE)
        ModeTransaction modeTransaction = extractModeTransaction(lower);
        builder.modeTransaction(modeTransaction);

        // 3. Ville & Quartier(s)
        List<String> quartiers = removals.contains("quartier") ? List.of() : extractQuartiers(lower, normalized);
        String quartier = quartiers.isEmpty() ? null : quartiers.get(0);
        builder.quartier(quartier);
        if (!quartiers.isEmpty()) {
            builder.quartiers(quartiers);
        }

        String ville = extractVille(lower, normalized);
        if (ville == null && quartier != null && QUARTIERS_DAKAR.contains(quartier)) {
            ville = "Dakar";
        }
        builder.ville(ville);

        // 4. Budget (plage min/max, chiffres, abréviations "100k" et montants en toutes lettres)
        BigDecimal budgetMin = null;
        BigDecimal budgetMax = null;
        if (!removals.contains("budget")) {
            BigDecimal[] range = extractBudgetRange(lower);
            if (range != null) {
                budgetMin = range[0];
                budgetMax = range[1];
            } else {
                budgetMax = extractBudget(lower, normalized);
            }
        }
        builder.budgetMin(budgetMin);
        builder.budgetMax(budgetMax);

        // 5. Nombre de pièces vs Nombre de chambres (respect strict de la Règle D)
        Integer nombrePieces = extractNombrePieces(lower);
        Integer nombreChambres = extractNombreChambres(lower);
        builder.nombrePieces(nombrePieces);
        builder.nombreChambres(nombreChambres);

        // 6. Meublé (critère obligatoire vs préférence souple)
        boolean meubleSouple = !removals.contains("meuble") && isSoftMeublePreference(normalized);
        Boolean meuble = removals.contains("meuble") || meubleSouple ? null : extractMeuble(lower);
        builder.meuble(meuble);

        // 7. Superficie minimale et maximale (m²)
        Double superficieMax = removals.contains("superficie") ? null : extractSuperficieMax(lower);
        Double superficieMin = removals.contains("superficie") ? null : extractSuperficieMin(lower, superficieMax);
        builder.superficieMin(superficieMin);
        builder.superficieMax(superficieMax);

        // 8. Étage & Ascenseur
        Integer etage = removals.contains("etage") ? null : extractEtage(lower);
        Boolean ascenseur = extractAscenseur(lower);
        builder.etage(etage);
        builder.ascenseur(ascenseur);

        // 9. Animaux acceptés
        Boolean animauxAcceptes = extractAnimauxAcceptes(lower);
        builder.animauxAcceptes(animauxAcceptes);

        // 10. Équipements, Proximités & Préférences souples / Attentes complémentaires
        Set<Equipement> equipements = extractEquipements(lower, normalized);
        if (!equipements.isEmpty()) {
            builder.equipements(equipements);
        }
        Set<ProximiteTag> proximites = extractProximites(lower, normalized);
        if (!proximites.isEmpty()) {
            builder.proximites(proximites);
        }

        List<String> preferencesSouples = extractPreferencesSouples(lower, normalized, meubleSouple, budgetMin, budgetMax, superficieMin, superficieMax);
        if (!preferencesSouples.isEmpty()) {
            builder.preferencesSouples(preferencesSouples);
        }
        List<String> attentesComplementaires = extractAttentesComplementaires(lower, normalized);
        if (!attentesComplementaires.isEmpty()) {
            builder.attentesComplementaires(attentesComplementaires);
        }
        List<String> precisionsAClarifier = buildPrecisionsAClarifier(
                lower, normalized, type, modeTransaction, ville, quartier, quartiers, budgetMin, budgetMax,
                nombrePieces, nombreChambres, superficieMin, superficieMax, expectationsNeedSurface(normalized, superficieMin)
        );
        if (!precisionsAClarifier.isEmpty()) {
            builder.precisionsAClarifier(precisionsAClarifier);
        }

        // 11. Confiance et Question de relance
        int matchedCount = 0;
        if (type != null) matchedCount++;
        if (modeTransaction != null) matchedCount++;
        if (quartier != null || ville != null) matchedCount++;
        if (budgetMax != null || budgetMin != null) matchedCount++;
        if (nombrePieces != null || nombreChambres != null) matchedCount++;
        if (meuble != null || meubleSouple) matchedCount++;
        if (superficieMin != null || superficieMax != null) matchedCount++;
        if (etage != null || ascenseur != null) matchedCount++;
        if (animauxAcceptes != null) matchedCount++;
        if (!equipements.isEmpty() || !proximites.isEmpty()) matchedCount++;
        if (!attentesComplementaires.isEmpty() || !preferencesSouples.isEmpty()) matchedCount++;

        double confiance = Math.min(1.0, 0.35 + (matchedCount * 0.15));
        builder.confiance(confiance);

        String questionDeRelance = null;
        if (nonResidentielDetecte) {
            questionDeRelance = "E-Courtier propose exclusivement des logements d'habitation (chambres, studios, appartements, villas). Quel type de logement résidentiel recherchez-vous ?";
        } else if (specialIntent == null && removals.isEmpty()) {
            questionDeRelance = buildSmartClarificationQuestion(
                    type, modeTransaction, ville, quartier, quartiers, budgetMin, budgetMax,
                    nombrePieces, nombreChambres, superficieMin, needClarificationSignal,
                    normalized.contains("informations te manquent") || normalized.contains("questions necessaires"),
                    preferencesSouples, expectationsNeedSurface(normalized, superficieMin)
            );
        }
        builder.questionDeRelance(questionDeRelance);
        builder.fournisseurIa("fallback-local");
        builder.modeSecours(true);

        return builder.build();
    }

    private boolean isOffTopicRequest(String lower) {
        return OFF_TOPIC_OBJECT_PATTERN.matcher(lower).find()
                && !REAL_ESTATE_CONTEXT_PATTERN.matcher(lower).find();
    }

    private String detectSpecialIntent(String lower, String normalized) {
        boolean isActivePropertySearch = normalized.matches(".*\\b(cherche|recherch|trouver|louer|acheter)\\b.*\\b(appartement|villa|maison|studio|chambre|logement)\\b.*")
                && !normalized.contains("comment")
                && !normalized.contains("arnaque")
                && !normalized.contains("payer avant")
                && !normalized.contains("verifier");
        if (!isActivePropertySearch && LEGAL_TRANSPARENCY_PATTERN.matcher(normalized).find()) {
            return "FAQ_VERIFICATION_JURIDIQUE";
        }
        if (normalized.contains("pourquoi me proposes")
                || normalized.contains("pourquoi cette annonce")
                || normalized.contains("pourquoi ces annonces")
                || normalized.contains("explique ton choix")
                || normalized.contains("en quoi cette annonce")) {
            return "EXPLICATION_RECOMMANDATION";
        }
        if ((normalized.contains("quel") || normalized.contains("preparer") || normalized.contains("fournir") || normalized.contains("dossier"))
                && (normalized.contains("document") || normalized.contains("papier") || normalized.contains("justificatif") || normalized.contains("dossier de location"))) {
            return "FAQ_DOCUMENTS";
        }
        if ((normalized.contains("montre") || normalized.contains("voir") || normalized.contains("affiche") || normalized.contains("quelles sont"))
                && (normalized.contains("biens disponibles") || normalized.contains("annonces disponibles") || normalized.contains("logements disponibles"))) {
            return "LISTE_DISPONIBLE";
        }
        return null;
    }

    private Set<String> detectCriteriaToRemove(String lower, String normalized) {
        Set<String> removals = new HashSet<>();
        if (normalized.contains("sans critere de quartier")
                || normalized.contains("sans quartier")
                || normalized.contains("peu importe le quartier")
                || normalized.contains("n'importe quel quartier")
                || normalized.contains("tous les quartiers")
                || normalized.contains("supprime le quartier")
                || normalized.contains("retire le quartier")) {
            removals.add("quartier");
        }
        if (normalized.contains("sans critere de budget")
                || normalized.contains("peu importe le budget")
                || normalized.contains("sans limite de budget")) {
            removals.add("budget");
        }
        if (normalized.contains("sans critere d'etage")
                || normalized.contains("peu importe l'etage")) {
            removals.add("etage");
        }
        if (normalized.contains("sans critere meuble")
                || normalized.contains("peu importe meuble")) {
            removals.add("meuble");
        }
        if (normalized.contains("sans critere de surface")
                || normalized.contains("sans critere de superficie")
                || normalized.contains("peu importe la surface")) {
            removals.add("superficie");
        }
        return removals;
    }

    private boolean isNonResidentiel(String lower) {
        return lower.matches(".*\\b(terrains?|parcelle|bureaux?|boutiques?|magasins?|entrep[ôo]ts?|hangars?|fonds\\s+de\\s+commerce|local\\s+commercial|locaux\\s+commerciaux)\\b.*")
                && !lower.matches(".*\\b(chambres?|studios?|appartements?|villas?|maisons?)\\b.*");
    }

    private TypeBien extractTypeBien(String lower) {
        String normalized = normalizeAccents(lower);
        Matcher m = TYPE_MENTION_PATTERN.matcher(normalized);
        record TypeCandidate(TypeBien type, int start, int end, boolean rejected) {}
        List<TypeCandidate> candidates = new ArrayList<>();

        while (m.find()) {
            String token = m.group(1).toLowerCase(Locale.ROOT);
            int start = m.start();
            int end = m.end();
            TypeBien mapped = mapTokenToTypeBien(token, normalized, start, end);
            if (mapped == null) {
                continue;
            }
            boolean rejected = isTypeMentionNegatedOrSource(normalized, start, end);
            candidates.add(new TypeCandidate(mapped, start, end, rejected));
        }

        // Deuxième passe : détecter "je préfère X à Y" (ex: "je préfère un appartement à une villa")
        // où Y est immédiatement précédé de "a <art> " alors qu'un candidat positif X précède dans la même phrase.
        List<TypeCandidate> refined = new ArrayList<>(candidates.size());
        for (int i = 0; i < candidates.size(); i++) {
            TypeCandidate c = candidates.get(i);
            boolean rejected = c.rejected();
            if (!rejected && i > 0) {
                String betweenPrevAndCurr = normalized.substring(candidates.get(i - 1).end(), c.start()).trim();
                String clauseBeforePrev = normalized.substring(Math.max(0, candidates.get(i - 1).start() - 35), candidates.get(i - 1).start());
                if ((clauseBeforePrev.contains("prefere") || clauseBeforePrev.contains("prefererais"))
                        && !candidates.get(i - 1).rejected()
                        && betweenPrevAndCurr.matches("^a\\s+(?:un|une|le|la|l'|du|de\\s+la|des)?$")) {
                    rejected = true;
                }
            }
            refined.add(new TypeCandidate(c.type(), c.start(), c.end(), rejected));
        }

        List<TypeCandidate> accepted = refined.stream().filter(c -> !c.rejected()).toList();
        if (accepted.isEmpty()) {
            return null;
        }
        boolean hasRejection = refined.stream().anyMatch(TypeCandidate::rejected);
        return hasRejection ? accepted.get(accepted.size() - 1).type() : accepted.get(0).type();
    }

    private TypeBien mapTokenToTypeBien(String token, String normalized, int start, int end) {
        if (token.startsWith("studio")) {
            return TypeBien.STUDIO;
        }
        if (token.startsWith("villa") || token.startsWith("maison") || "duplex".equals(token)) {
            return TypeBien.VILLA;
        }
        if (token.startsWith("appart") || token.matches("[ft][2-6]")) {
            return TypeBien.APPARTEMENT;
        }
        if (token.startsWith("chambre")) {
            String lookback = normalized.substring(Math.max(0, start - 28), start);
            String lookahead = normalized.substring(end, Math.min(normalized.length(), end + 18)).trim();
            boolean precededByMultiCount = lookback.matches(".*\\b(?:\\d+|deux|trois|quatre|cinq|six|plusieurs)\\s+$")
                    || token.equals("chambres");
            boolean singleQualifier = lookahead.startsWith("seule") || lookahead.startsWith("etudiant") || lookahead.startsWith("d'etudiant");
            if (precededByMultiCount && !singleQualifier) {
                return null;
            }
            return TypeBien.CHAMBRE;
        }
        return null;
    }

    private boolean isTypeMentionNegatedOrSource(String normalized, int start, int end) {
        String lookback = normalized.substring(Math.max(0, start - 45), start).trim();
        String lookahead = normalized.substring(end, Math.min(normalized.length(), end + 28)).trim();

        // Marques d'exclusion directe ou comparative avant le mot
        if (lookback.matches(".*\\b(?:plutot\\s+qu['e](?:\\s*d['e])?|au\\s+lieu\\s+d['e]|a\\s+la\\s+place\\s+d['e]|au\\s+detriment\\s+d['e]|et\\s+non|non\\s+pas|pas(?:\\s+d[eu]?)?|plus\\s+d[eu]?|sans|evite[rz]?|exclu(?:s|re)?)\\s*(?:un|une|le|la|l'|du|de|des)?$")
                || lookback.matches(".*\\bnon\\s+(?:un|une|le|la|l'|du|de)?$")
                || lookback.endsWith("plutot qu'")
                || lookback.endsWith("plutot que")
                || lookback.endsWith("au lieu d'")
                || lookback.endsWith("a la place d'")) {
            return true;
        }
        // "remplace le studio par une villa" / "changer la maison en studio"
        return lookback.matches(".*\\b(?:remplace[rz]?|change[rz]?|quitte[rz]?|laisse[rz]?)\\s*(?:le|la|l'|un|une|mon|ma)?$")
                && lookahead.matches("^(?:par|en|pour)\\b.*");
    }

    private ModeTransaction extractModeTransaction(String lower) {
        String normalized = normalizeAccents(lower);
        Pattern txPattern = Pattern.compile("\\b(acheter|achat|vente|vendre|acquerir|louer|location|loyer|mensuel|bail)\\b");
        Matcher m = txPattern.matcher(normalized);
        ModeTransaction accepted = null;
        while (m.find()) {
            String tok = m.group(1);
            String lookback = normalized.substring(Math.max(0, m.start() - 30), m.start()).trim();
            if (lookback.matches(".*\\b(?:au\\s+lieu\\s+de?|plutot\\s+que(?:\\s+de)?|et\\s+non|non\\s+pas|pas(?:\\s+de)?)$")
                    || lookback.endsWith("au lieu d'")
                    || lookback.endsWith("plutot qu'")
                    || lookback.endsWith("plutot que")) {
                continue;
            }
            ModeTransaction candidate = (tok.startsWith("achet") || tok.startsWith("vent") || tok.startsWith("vend") || tok.startsWith("acquer"))
                    ? ModeTransaction.VENTE
                    : ModeTransaction.LOCATION;
            if (accepted == null) {
                accepted = candidate;
            } else if (accepted != candidate && !lookback.isEmpty()) {
                accepted = candidate;
            }
        }
        return accepted;
    }

    private List<String> extractQuartiers(String lower, String normalized) {
        record QuartierMatch(String canonical, int startIndex) {}
        List<QuartierMatch> matches = new ArrayList<>();
        String working = normalized;
        for (Map.Entry<String, String> entry : QUARTIERS_CONNUS.entrySet()) {
            String key = entry.getKey();
            Pattern p = Pattern.compile("\\b" + Pattern.quote(key) + "\\b", Pattern.CASE_INSENSITIVE);
            Matcher m = p.matcher(working);
            if (m.find()) {
                String canonical = entry.getValue();
                int idx = m.start();
                if (matches.stream().noneMatch(qm -> qm.canonical().equals(canonical))) {
                    matches.add(new QuartierMatch(canonical, idx));
                }
                // Remplacer par des espaces de même longueur pour préserver les index tout en évitant que "liberte 6" ne déclenche "liberte"
                working = working.substring(0, m.start()) + " ".repeat(m.end() - m.start()) + working.substring(m.end());
            }
        }
        matches.sort(java.util.Comparator.comparingInt(QuartierMatch::startIndex));
        return matches.stream().map(QuartierMatch::canonical).toList();
    }

    private String extractVille(String lower, String normalized) {
        if (normalized.contains("ziguinchor") || normalized.contains("zig")
                || normalized.contains("nema") || normalized.contains("boucotte")
                || normalized.contains("diabir") || normalized.contains("santhiaba")) {
            if (normalized.contains("ziguinchor") || normalized.contains("zig")) {
                return "Ziguinchor";
            }
        }
        if (normalized.contains("dakar")) {
            return "Dakar";
        }
        if (normalized.contains("thies")) {
            return "Thiès";
        }
        if (normalized.contains("saint-louis") || normalized.contains("saint louis")) {
            return "Saint-Louis";
        }
        if (normalized.contains("saly")) {
            return "Saly";
        }
        if (normalized.contains("mbour")) {
            return "Mbour";
        }
        if (normalized.contains("rufisque")) {
            return "Rufisque";
        }
        if (normalized.contains("pikine")) {
            return "Pikine";
        }
        if (normalized.contains("guediawaye")) {
            return "Guédiawaye";
        }
        if (normalized.contains("touba")) {
            return "Touba";
        }
        if (normalized.contains("kaolack")) {
            return "Kaolack";
        }
        return null;
    }

    private BigDecimal[] extractBudgetRange(String lower) {
        Matcher rangeMatcher = BUDGET_RANGE_PATTERN.matcher(lower);
        if (rangeMatcher.find()) {
            BigDecimal min = parseAmountWithUnit(rangeMatcher.group(1), rangeMatcher.group(2));
            BigDecimal max = parseAmountWithUnit(rangeMatcher.group(3), rangeMatcher.group(4));
            if (min != null && max != null && min.compareTo(BigDecimal.valueOf(5000)) >= 0 && max.compareTo(min) >= 0) {
                return new BigDecimal[]{min, max};
            }
        }
        return null;
    }

    private BigDecimal extractBudget(String lower, String normalized) {
        String cleaned = lower
                .replaceAll("\\b\\d+(?:[.,]\\d+)?\\s*(?:m2\\b|m²|m[èe]tres?\\s*carr[ée]s?\\b)", " ")
                .replaceAll("\\b\\d+\\s*(?:er|e|[èe]me|eme)?\\s*[ée]tage\\b", " ")
                .replaceAll("\\b[ée]tage\\s*\\d+\\b", " ")
                .replaceAll("\\b\\d+\\s*(?:pi[èe]ces?|chambres?)\\b", " ")
                .replaceAll("\\b[ft][1-9]\\b", " ")
                .replaceAll("\\blibert[ée]\\s*\\d+\\b", " ");

        // 1. Montant avec devise explicite (ex: "250 000 FCFA", "100k")
        Matcher currencyMatcher = BUDGET_WITH_CURRENCY_PATTERN.matcher(cleaned);
        if (currencyMatcher.find()) {
            String rawNum = currencyMatcher.group(1) != null ? currencyMatcher.group(1) : currencyMatcher.group(4);
            String rawUnit = currencyMatcher.group(2) != null ? currencyMatcher.group(2) : currencyMatcher.group(5);
            BigDecimal amount = parseAmountWithUnit(rawNum, rawUnit);
            if (amount != null && amount.compareTo(BigDecimal.valueOf(5000)) >= 0) {
                return amount;
            }
        }

        // 2. Montant introduit par "budget", "prix", "loyer", "max", "pour", "à"
        Matcher explicitMatcher = BUDGET_EXPLICIT_PATTERN.matcher(cleaned);
        while (explicitMatcher.find()) {
            BigDecimal amount = parseAmountWithUnit(explicitMatcher.group(1), explicitMatcher.group(2));
            if (amount != null && amount.compareTo(BigDecimal.valueOf(5000)) >= 0) {
                return amount;
            }
        }

        // 3. Nombre >= 10 000 seul
        Matcher fallbackMatcher = BUDGET_FALLBACK_LARGE_NUMBER.matcher(cleaned);
        if (fallbackMatcher.find()) {
            BigDecimal amount = parseAmountWithUnit(fallbackMatcher.group(1), null);
            if (amount != null && amount.compareTo(BigDecimal.valueOf(10000)) >= 0) {
                return amount;
            }
        }

        // 4. Montant exprimé en toutes lettres en français (ex: "deux cent mille francs", "cent cinquante mille")
        return extractBudgetFromFrenchWords(normalized);
    }

    private BigDecimal extractBudgetFromFrenchWords(String normalized) {
        if (!normalized.contains("mille") && !normalized.contains("million")) {
            return null;
        }

        Map<String, Integer> wordValues = Map.ofEntries(
                Map.entry("un", 1),
                Map.entry("une", 1),
                Map.entry("deux", 2),
                Map.entry("trois", 3),
                Map.entry("quatre", 4),
                Map.entry("cinq", 5),
                Map.entry("six", 6),
                Map.entry("sept", 7),
                Map.entry("huit", 8),
                Map.entry("neuf", 9),
                Map.entry("dix", 10),
                Map.entry("onze", 11),
                Map.entry("douze", 12),
                Map.entry("quinze", 15),
                Map.entry("vingt", 20),
                Map.entry("vingt-cinq", 25),
                Map.entry("trente", 30),
                Map.entry("quarante", 40),
                Map.entry("cinquante", 50),
                Map.entry("soixante", 60),
                Map.entry("soixante-dix", 70),
                Map.entry("soixante-quinze", 75),
                Map.entry("quatre-vingt", 80),
                Map.entry("quatre-vingts", 80),
                Map.entry("quatre-vingt-dix", 90),
                Map.entry("cent", 100),
                Map.entry("cents", 100)
        );

        Pattern wordsBeforeScale = Pattern.compile(
                "\\b((?:(?:un|une|deux|trois|quatre|cinq|six|sept|huit|neuf|dix|onze|douze|quinze|vingt-cinq|vingt|trente|quarante|cinquante|soixante-quinze|soixante-dix|soixante|quatre-vingt-dix|quatre-vingts?|cents?|et)[\\s-]+)+)(mille|millions?)\\b"
        );

        Matcher m = wordsBeforeScale.matcher(normalized);
        if (m.find()) {
            String prefix = m.group(1).trim();
            String scaleWord = m.group(2).trim();
            long scale = scaleWord.startsWith("million") ? 1_000_000L : 1_000L;

            long current = 0;
            String[] tokens = prefix.split("\\s+");
            for (String token : tokens) {
                if ("et".equals(token) || token.isBlank()) {
                    continue;
                }
                Integer val = wordValues.get(token);
                if (val == null && token.contains("-")) {
                    int subSum = 0;
                    for (String sub : token.split("-")) {
                        Integer sv = wordValues.get(sub);
                        if (sv != null) subSum += sv;
                    }
                    if (subSum > 0) val = subSum;
                }
                if (val != null) {
                    if (val == 100) {
                        current = (current == 0) ? 100 : current * 100;
                    } else {
                        current += val;
                    }
                }
            }
            if (current == 0) {
                current = 1;
            }
            long total = current * scale;
            if (total >= 5000) {
                return BigDecimal.valueOf(total);
            }
        } else if (normalized.matches(".*\\bmille\\s+francs?\\b.*")) {
            return null;
        }
        return null;
    }

    private BigDecimal parseAmountWithUnit(String rawNumber, String unit) {
        if (rawNumber == null) return null;
        String cleanNumber = rawNumber.replaceAll("[\\s.,]", "");
        try {
            long value = Long.parseLong(cleanNumber);
            if (unit != null) {
                String u = unit.toLowerCase(Locale.ROOT);
                if (u.equals("k") || u.equals("mille")) {
                    value *= 1_000L;
                } else if (u.startsWith("million")) {
                    value *= 1_000_000L;
                }
            }
            return BigDecimal.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Integer extractNombrePieces(String lower) {
        Matcher matcher = PIECES_PATTERN.matcher(lower);
        if (matcher.find()) {
            String g1 = matcher.group(1);
            String g2 = matcher.group(2);
            String g3 = matcher.group(3);
            if (g1 != null) {
                return Integer.parseInt(g1);
            }
            if (g2 != null) {
                return Integer.parseInt(g2);
            }
            if (g3 != null) {
                return parseFrenchWordNumber(g3);
            }
        }
        return null;
    }

    private Integer extractNombreChambres(String lower) {
        Matcher matcher = CHAMBRES_PATTERN.matcher(lower);
        if (matcher.find()) {
            if (matcher.group(1) != null) {
                return Integer.parseInt(matcher.group(1));
            }
            if (matcher.group(2) != null) {
                return parseFrenchWordNumber(matcher.group(2));
            }
        }
        return null;
    }

    private Integer parseFrenchWordNumber(String word) {
        return switch (word.toLowerCase(Locale.ROOT)) {
            case "un", "une" -> 1;
            case "deux" -> 2;
            case "trois" -> 3;
            case "quatre" -> 4;
            case "cinq" -> 5;
            case "six" -> 6;
            case "sept" -> 7;
            case "huit" -> 8;
            default -> null;
        };
    }

    private Double extractSuperficieMax(String lower) {
        Matcher matcher = SUPERFICIE_MAX_PATTERN.matcher(lower);
        if (matcher.find()) {
            try {
                String raw = matcher.group(1).replace(',', '.');
                double val = Double.parseDouble(raw);
                if (val > 0 && val <= 10000) {
                    return val;
                }
            } catch (NumberFormatException ignored) {}
        }
        return null;
    }

    private Double extractSuperficieMin(String lower, Double superficieMax) {
        Matcher matcher = SUPERFICIE_MIN_PATTERN.matcher(lower);
        while (matcher.find()) {
            try {
                String raw = matcher.group(1).replace(',', '.');
                double val = Double.parseDouble(raw);
                if (val > 0 && val <= 10000) {
                    if (superficieMax != null && Double.compare(val, superficieMax) == 0 && SUPERFICIE_MAX_PATTERN.matcher(lower).find()) {
                        continue;
                    }
                    return val;
                }
            } catch (NumberFormatException ignored) {}
        }
        return null;
    }

    private Integer extractEtage(String lower) {
        Matcher matcher = ETAGE_PATTERN.matcher(lower);
        if (matcher.find()) {
            if (matcher.group(1) != null) {
                return 0;
            }
            if (matcher.group(2) != null) {
                return Integer.parseInt(matcher.group(2));
            }
            if (matcher.group(3) != null) {
                return Integer.parseInt(matcher.group(3));
            }
            if (matcher.group(4) != null) {
                String ord = matcher.group(4).toLowerCase(Locale.ROOT);
                if (ord.startsWith("prem") || ord.equals("1er")) return 1;
                if (ord.startsWith("deux") || ord.equals("second") || ord.equals("2e")) return 2;
                if (ord.startsWith("trois") || ord.equals("3e")) return 3;
                if (ord.startsWith("quatr") || ord.equals("4e")) return 4;
                if (ord.startsWith("cinq") || ord.equals("5e")) return 5;
            }
        }
        return null;
    }

    private Boolean extractAscenseur(String lower) {
        if (lower.contains("sans ascenseur")) {
            return Boolean.FALSE;
        }
        if (lower.contains("ascenseur")) {
            return Boolean.TRUE;
        }
        return null;
    }

    private Boolean extractAnimauxAcceptes(String lower) {
        if (lower.contains("sans animaux") || lower.contains("animaux interdits") || lower.contains("pas d'animaux")) {
            return Boolean.FALSE;
        }
        if (lower.matches(".*\\b(chat|chats|chien|chiens|animaux|animal)\\b.*")) {
            return Boolean.TRUE;
        }
        return null;
    }

    private boolean isSoftMeublePreference(String normalized) {
        if (normalized == null) {
            return false;
        }
        String norm = normalizeAccents(normalized.toLowerCase(Locale.ROOT));
        if (norm.contains("non meuble") || norm.contains("non-meuble")) {
            return false;
        }
        return SOFT_MEUBLE_PATTERN.matcher(norm).find();
    }

    private Boolean extractMeuble(String lower) {
        if (lower.contains("non meublé") || lower.contains("non-meublé") || lower.contains("non meuble") || lower.contains("vide")) {
            return Boolean.FALSE;
        }
        if (lower.contains("meublé") || lower.contains("meuble")) {
            return Boolean.TRUE;
        }
        if ((lower.contains("équipé") || lower.contains("equipe"))
                && !lower.matches(".*\\bcuisine\\s+[ée]quip[ée]e?\\b.*")) {
            return Boolean.TRUE;
        }
        return null;
    }

    private List<String> extractPreferencesSouples(
            String lower,
            String normalized,
            boolean meubleSouple,
            BigDecimal budgetMin,
            BigDecimal budgetMax,
            Double superficieMin,
            Double superficieMax
    ) {
        List<String> prefs = new ArrayList<>();
        if (meubleSouple) {
            prefs.add("logement meublé de préférence");
        }
        if (normalized.matches(".*\\b(?:quartier|secteur|zone|environnement|endroit)?\\s*calme\\b.*")) {
            prefs.add("quartier calme");
        }
        if (budgetMin == null && budgetMax == null
                && normalized.matches(".*\\b(?:pas\\s+trop\\s+cher|abordable|petit\\s+budget|loyer\\s+modere)\\b.*")) {
            prefs.add("budget modéré / abordable");
        }
        if (superficieMin == null && superficieMax == null
                && normalized.matches(".*\\b(?:assez\\s+grand|spacieux|spacieuse|grand\\s+logement)\\b.*")) {
            prefs.add("espace assez grand / spacieux");
        }
        if (normalized.matches(".*\\bcour\\b.*") && normalized.contains("serait un plus")) {
            prefs.add("cour (serait un plus)");
        }
        return prefs;
    }

    private List<String> extractAttentesComplementaires(String lower, String normalized) {
        List<String> extras = new ArrayList<>();
        if (normalized.matches(".*\\bcour\\b.*")) {
            extras.add("cour");
        }
        if (normalized.matches(".*\\bcuisine\\s+equipee?\\b.*")) {
            extras.add("cuisine équipée");
        } else if (normalized.matches(".*\\bcuisine\\b.*")) {
            extras.add("cuisine");
        }
        if (normalized.matches(".*\\b(?:grand\\s+terrain|terrain\\s+(?:suffisamment\\s+)?grand|grand\\s+espace)\\b.*")) {
            extras.add("grand terrain");
        }
        if (normalized.matches(".*\\b(?:balcon|terrasse)\\b.*")) {
            extras.add(normalized.contains("terrasse") ? "terrasse" : "balcon");
        }
        if (normalized.matches(".*\\bjardin\\b.*")) {
            extras.add("jardin");
        }
        if (normalized.matches(".*\\broute\\s+principale\\b.*")) {
            extras.add("accès facile route principale");
        }
        return extras;
    }

    private boolean expectationsNeedSurface(String normalized, Double superficieMin) {
        return superficieMin == null && normalized.matches(".*\\b(?:terrain\\s+(?:suffisamment\\s+)?grand|grand\\s+terrain|assez\\s+grand)\\b.*");
    }

    private List<String> buildPrecisionsAClarifier(
            String lower,
            String normalized,
            TypeBien type,
            ModeTransaction modeTransaction,
            String ville,
            String quartier,
            List<String> quartiers,
            BigDecimal budgetMin,
            BigDecimal budgetMax,
            Integer nombrePieces,
            Integer nombreChambres,
            Double superficieMin,
            Double superficieMax,
            boolean needSurfacePrecision
    ) {
        List<String> missing = new ArrayList<>();
        if (modeTransaction == null) {
            missing.add("location ou achat");
        }
        if (type == null) {
            missing.add("type de logement (chambre, studio, appartement ou villa)");
        }
        if (budgetMax == null && budgetMin == null) {
            missing.add("budget maximum en FCFA");
        }
        boolean hasLoc = ville != null || quartier != null || (quartiers != null && !quartiers.isEmpty());
        if (!hasLoc) {
            missing.add("ville ou quartier souhaité");
        }
        if (needSurfacePrecision) {
            missing.add("superficie minimale souhaitée (en m²)");
        }
        if (nombrePieces == null && nombreChambres == null && type != TypeBien.STUDIO && type != TypeBien.CHAMBRE) {
            missing.add("nombre de chambres souhaité");
        }
        return missing;
    }

    private String buildSmartClarificationQuestion(
            TypeBien type,
            ModeTransaction modeTransaction,
            String ville,
            String quartier,
            List<String> quartiers,
            BigDecimal budgetMin,
            BigDecimal budgetMax,
            Integer nombrePieces,
            Integer nombreChambres,
            Double superficieMin,
            boolean needClarificationSignal,
            boolean askedForMissingQuestions,
            List<String> preferencesSouples,
            boolean needSurfacePrecision
    ) {
        boolean hasBudget = budgetMax != null || budgetMin != null;
        boolean hasType = type != null;
        boolean hasLoc = ville != null || quartier != null || (quartiers != null && !quartiers.isEmpty());
        boolean hasSize = nombrePieces != null || nombreChambres != null;
        boolean mentionsCalm = preferencesSouples != null && preferencesSouples.contains("quartier calme");

        if (!hasBudget && !hasType) {
            if (!hasLoc) {
                return mentionsCalm
                        ? "Pour orienter votre recherche dans un secteur calme, quel est votre budget maximum (en FCFA), quel type de bien (studio, appartement, villa, chambre) et quelle ville ou quartier souhaitez-vous ?"
                        : "Pour affiner ma recherche, quel est votre budget maximum (en FCFA) et quel type de bien (studio, appartement, villa, chambre) recherchez-vous ?";
            }
            return "Pour affiner ma recherche, quel est votre budget maximum (en FCFA) et quel type de bien (studio, appartement, villa, chambre) recherchez-vous ?";
        }
        if (!hasBudget) {
            if (!hasSize) {
                return "Quel est votre budget maximum en FCFA pour ce bien, et combien de chambres souhaitez-vous ?";
            }
            return "Quel est votre budget maximum en FCFA pour ce bien ?";
        }
        if (!hasType) {
            return "Quel type de bien (chambre, studio, appartement ou villa) recherchez-vous ?";
        }
        if (!hasLoc) {
            return mentionsCalm
                    ? "Dans quelle ville ou quel quartier calme (ex: Dakar, Mermoz, Ziguinchor, Boucotte) souhaitez-vous habiter ?"
                    : "Dans quelle ville ou quel quartier (ex: Dakar, Mermoz, Almadies, Ziguinchor, Boucotte) souhaitez-vous habiter ?";
        }
        if (needSurfacePrecision) {
            return "Quelle superficie minimale approximative (en m²) souhaitez-vous pour le terrain ou le logement ?";
        }
        if (needClarificationSignal && !hasSize) {
            return "Souhaitez-vous préciser le nombre de chambres ou un quartier en particulier ?";
        }
        if (askedForMissingQuestions) {
            List<String> complementaryMissing = new ArrayList<>();
            if (!hasSize) complementaryMissing.add("nombre de chambres");
            if (superficieMin == null) complementaryMissing.add("surface minimale");
            complementaryMissing.add("étage ou présence d'ascenseur souhaité");
            complementaryMissing.add("date d'emménagement souhaitée");
            return "Vos critères principaux sont bien clairs ; souhaitez-vous préciser un détail complémentaire comme "
                    + String.join(" ou ", complementaryMissing.subList(0, Math.min(2, complementaryMissing.size()))) + " ?";
        }
        return null;
    }

    private Set<Equipement> extractEquipements(String lower, String normalized) {
        Set<Equipement> set = new LinkedHashSet<>();
        if (normalized.matches(".*\\b(wifi|internet|fibre)\\b.*")) {
            set.add(Equipement.WIFI);
        }
        if (normalized.matches(".*\\b(parking|garage|stationnement)\\b.*")) {
            set.add(Equipement.PARKING);
        }
        if (normalized.matches(".*\\b(clim|climatisation|climatise|climatiseur)\\b.*")) {
            set.add(Equipement.CLIMATISATION);
        }
        if (normalized.matches(".*\\b(piscine)\\b.*")) {
            set.add(Equipement.PISCINE);
        }
        if (normalized.matches(".*\\b(gardien|gardiennage|securite|securise|surveillance|vigile)\\b.*")) {
            set.add(Equipement.GARDIEN);
        }
        if (normalized.matches(".*\\b(groupe\\s+electrogene)\\b.*")) {
            set.add(Equipement.GROUPE_ELECTROGENE);
        }
        if (normalized.matches(".*\\b(reserve\\s+d'eau|surpresseur|citerne|bache\\s+a\\s+eau)\\b.*")) {
            set.add(Equipement.RESERVE_EAU);
        }
        return set;
    }

    private Set<ProximiteTag> extractProximites(String lower, String normalized) {
        Set<ProximiteTag> set = new LinkedHashSet<>();
        if (normalized.matches(".*\\b(transports?|bus|ter|brt|taxis?|route\\s+principale)\\b.*")) {
            set.add(ProximiteTag.TRANSPORT);
        }
        if (normalized.matches(".*\\b(ecoles?|lycees?|colleges?)\\b.*")) {
            set.add(ProximiteTag.ECOLE);
        }
        if (normalized.matches(".*\\b(marches?)\\b.*")) {
            set.add(ProximiteTag.MARCHE);
        }
        if (normalized.matches(".*\\b(hopital|hopitaux|cliniques?|pharmacies?)\\b.*")) {
            set.add(ProximiteTag.HOPITAL);
        }
        if (normalized.matches(".*\\b(plages?|mer|ocean|corniche)\\b.*")) {
            set.add(ProximiteTag.PLAGE);
        }
        if (normalized.matches(".*\\b(commerces?|supermarches?|boutiques?|auchan)\\b.*")) {
            set.add(ProximiteTag.COMMERCE);
        }
        if (normalized.matches(".*\\b(universites?|ucad|campus|fac)\\b.*")) {
            set.add(ProximiteTag.UNIVERSITE);
        }
        return set;
    }

    private String normalizeAccents(String input) {
        if (input == null) return "";
        String norm = Normalizer.normalize(input, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        return norm.replace("œ", "oe").replace("æ", "ae");
    }
}
