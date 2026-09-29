package com.biblio.backend.service;

import com.biblio.backend.dto.LivreCatalogue;
import com.biblio.backend.dto.LivreDto;
import com.biblio.backend.dto.WorkDoc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Recomande des livres proches du livre consulte, par GENRE.
 *
 * Pourquoi le genre : c'est le seul signal de proximite reellement exploitable
 * ici. Le catalogue de l'accueil est volontairement diversifie (un seul livre
 * par auteur), donc "meme auteur" ne renverrait presque rien. L'annee de
 * publication ne dit rien du contenu. Les sujets Open Library, en revanche,
 * decrivent le genre ("Magic and Supernatural", "Vampires", "Historical
 * fiction") et se recoupent fortement entre livres du meme type.
 *
 * Pourquoi ne pas comparer les sujets de tous les livres du catalogue : il
 * faudrait une centaine d'appels, a 5-10 secondes chacun. On s'appuie donc sur
 * le classement de l'API : les 3 premiers genres du livre servent de requetes
 * de recherche, et l'API renvoie les livres les plus proches de chaque genre.
 * Cela tient en 4 appels au total, quel que soit le nombre de livres en base.
 */
@Service
public class RecommandationService {

    private static final Logger log = LoggerFactory.getLogger(RecommandationService.class);

    /** Nombre de genres utilises comme requetes. Au-dela, le gain devient marginal. */
    private static final int GENRES_UTILISES = 3;

    /** Nombre maximum de recommandations renvoyees a l'interface. */
    private static final int LIMITE_PAR_DEFAUT = 10;

    /** Nombre de candidats demandes par genre, avant deduplication. */
    private static final int CANDIDATS_PAR_GENRE = 12;

    private final OpenLibraryService openLibraryService;
    private final OpenLibraryDetailService openLibraryDetailService;
    private final CatalogueCache catalogueCache;
    private final RecommandationFusionService fusionService;
    private final Map<String, List<LivreDto>> cache = new ConcurrentHashMap<>();

    public RecommandationService(
            OpenLibraryService openLibraryService,
            OpenLibraryDetailService openLibraryDetailService,
            CatalogueCache catalogueCache,
            RecommandationFusionService fusionService) {
        this.openLibraryService = openLibraryService;
        this.openLibraryDetailService = openLibraryDetailService;
        this.catalogueCache = catalogueCache;
        this.fusionService = fusionService;
    }

    /**
     * Livres recommandes pour une reference donnee.
     *
     * @param reference cle Open Library du livre consulte
     * @param limite    nombre maximum de recommandations (10 par defaut)
     */
    public List<LivreDto> pourLivre(String reference, Integer limite) {
        int maximum = limite == null || limite < 1 ? LIMITE_PAR_DEFAUT : Math.min(limite, 20);
        String cle = OpenLibraryDetailService.normaliser(reference);
        if (cle == null) {
            return List.of();
        }

        String cacheKey = cle + "|" + maximum;
        List<LivreDto> enCache = cache.get(cacheKey);
        if (enCache != null) {
            return enCache;
        }

        List<LivreDto> resultat = calculer(cle, maximum);
        if (!resultat.isEmpty()) {
            cache.put(cacheKey, resultat);
        }
        return resultat;
    }

    private List<LivreDto> calculer(String cle, int maximum) {
        WorkDoc oeuvre = openLibraryDetailService.oeuvre(cle);
        List<String> genres = genresRecommandation(oeuvre);

        if (genres.isEmpty()) {
            log.info("Aucun genre exploitable pour {} : pas de recommandation", cle);
            return List.of();
        }

        // Un livre ne doit jamais se recommander lui-meme.
        Set<String> dejaVus = new LinkedHashSet<>(Set.of("/works/" + cle));
        Set<String> auteursVus = new LinkedHashSet<>();
        Set<String> titresVus = new LinkedHashSet<>();
        Set<String> motsDeTitreVus = new LinkedHashSet<>();
        List<LivreDto> recommandations = new ArrayList<>();

        // Tour de role sur les genres : on prend un livre du 1er genre, puis du
        // 2e, puis du 3e, et on recommence. Sans cela le 1er genre remplit
        // tout le carrousel : sur Harry Potter, "ghosts" aurait produit dix
        // livres intitules "Ghost", "Ghost Story", "Ghost World"...
        List<List<LivreCatalogue>> parGenre = genres.stream()
                .map(genre -> catalogue(genre, CANDIDATS_PAR_GENRE))
                .toList();

        boolean encoreDesCandidats = true;
        while (recommandations.size() < maximum && encoreDesCandidats) {
            encoreDesCandidats = false;
            for (List<LivreCatalogue> candidats : parGenre) {
                if (recommandations.size() >= maximum) {
                    break;
                }
                LivreDto choisi = choisir(candidats, dejaVus, auteursVus, titresVus, motsDeTitreVus);
                if (choisi != null) {
                    recommandations.add(choisi);
                    encoreDesCandidats = true;
                }
            }
        }

        log.info("Recommandations pour {} : {} genres -> {} livres", cle, genres, recommandations.size());
        return List.copyOf(recommandations);
    }

    /**
     * Premier candidat acceptable d'une liste de genres.
     *
     * Quatre filtres, du plus evident au plus subtil :
     * - on ne recommande jamais le livre consulte ;
     * - un meme auteur ne remplit pas le carrousel ;
     * - deux titres ne se ressemblent pas (comparaison complete) ;
     * - deux titres ne partagent pas leur mot principal, ce qui evite
     *   "Ghost", "Ghost Story", "Ghost World" dans le meme carrousel.
     */
    private LivreDto choisir(List<LivreCatalogue> candidats,
                             Set<String> dejaVus,
                             Set<String> auteursVus,
                             Set<String> titresVus,
                             Set<String> motsDeTitreVus) {
        for (LivreCatalogue candidat : candidats) {
            if (dejaVus.contains(candidat.reference())) {
                continue;
            }
            String auteur = normaliser(candidat.auteur());
            String titre = normaliser(candidat.titre());
            if (!auteursVus.add(auteur)) {
                continue;
            }
            if (!titresVus.add(titre)) {
                auteursVus.remove(auteur);
                continue;
            }
            if (!motsDeTitreVus.add(motPrincipal(titre))) {
                auteursVus.remove(auteur);
                titresVus.remove(titre);
                continue;
            }
            dejaVus.add(candidat.reference());
            return fusionService.versDto(candidat);
        }
        return null;
    }

    /**
     * Mot le plus significatif d'un titre.
     *
     * On ignore les mots generiques, presents dans la plupart des titres
     * ("the", "a", "of", "et", "le", "la") : ils ne distinguent rien.
     */
    private String motPrincipal(String titreNormalise) {
        for (String mot : titreNormalise.split(" ")) {
            if (!mot.isEmpty() && !MOTS_GENERIQUES.contains(mot)) {
                return mot;
            }
        }
        return titreNormalise;
    }

    private static final Set<String> MOTS_GENERIQUES = Set.of(
            "the", "a", "an", "of", "and", "or", "in", "on", "to",
            "le", "la", "les", "de", "des", "du", "un", "une", "et", "ou",
            "story", "book", "tome", "roman", "edition", "livre"
    );

    /**
     * Genres retenus pour la recherche, du plus utile au moins utile.
     *
     * Certains sujets ne designent pas un genre mais une region ("England"),
     * un siecle ("20th century") ou une technique ("Large type books").
     * On les ecarte : ils rameneraient des livres sans rapport.
     */
    /**
     * Genres retenus pour la recherche, du plus utile au moins utile.
     *
     * Public car la page de detail affiche ces memes genres : l'utilisateur doit
     * pouvoir lire « recommandations sur ce genre » et voir reellement ce qui a servi.
     */
    static List<String> genresRecommandation(WorkDoc oeuvre) {
        if (oeuvre == null) {
            return List.of();
        }
        return oeuvre.genresUtiles().stream()
                .filter(RecommandationService::estUnGenre)
                .limit(GENRES_UTILISES)
                .toList();
    }

    /**
     * Ecart les sujets qui ne designent pas un genre.
     *
     * Deux filtres. D'abord une liste de sujets de contexte evidents
     * ("20th century", "Large type books"). Ensuite un vocabulaire de genres :
     * Open Library melange aux genres des noms propres et des themes precis
     * (sur The Hobbit : "Arkenstone", "thrushes", "invisibility"), qui
     * ramèneraient des livres sans rapport. Un sujet n'est retenu que s'il
     * contient un mot de ce vocabulaire.
     */
    private static boolean estUnGenre(String sujet) {
        if (sujet == null || CONTEXTE.contains(sujet)) {
            return false;
        }
        // Un sujet long est une liste de descripteurs, pas un genre nommable :
        // "Dune (Imaginary place), Fiction" ne ramene rien d'utile comme requete.
        if (sujet.contains(",") || sujet.contains("(")) {
            return false;
        }
        for (String mot : sujet.split("[^a-z]+")) {
            if (!mot.isEmpty() && MOTS_GENRE.contains(mot)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Mots caracterisant un genre litteraire. Volontairement large : on prefere
     * garder un sujet un peu etroit ("historical fiction") plutot que de
     * perdre une recommandation correcte.
     */
    private static final Set<String> MOTS_GENRE = Set.of(
            "fiction", "fantasy", "novel", "magic", "historical", "history",
            "adventure", "romance", "mystery", "detective", "thriller",
            "horror", "science", "war", "military", "western", "westerns",
            "juvenile", "children", "childrens", "picture", "biography",
            "autobiography", "memoir", "poetry", "poems", "drama", "comedy",
            "satire", "tragedy", "epic", "saga", "legend", "mythology",
            "myth", "fairy", "folklore", "witch", "witches", "wizard",
            "wizards", "wizardry", "vampire", "vampires", "ghost", "ghosts",
            "monster", "monsters", "dragon", "dragons", "dwarf", "dwarves",
            "superhero", "superheroes",             "crime", "espionage",
            "spy", "spies", "love", "revenge", "survival", "quest", "knights",
            "medieval", "classics", "nineteenth", "twentieth", "urban",
            "dystopia", "utopia", "gothic", "cyberpunk", "steampunk", "space",
            "planet", "aliens", "pirates", "sea", "nature", "animals",
            "christian", "biblical", "religieux", "psychological", "social"
    );

    private static final Set<String> CONTEXTE = Set.of(
            "accessible book", "large type books", "protected daisy",
            "in library", "internet archive wishlist", "reading level-grade 9",
            "overdrive", "large print books", "audio cassette", "paperback",
            "protected print disabled", "french as second language",
            "textbook", "juvenile fiction", "newspaper", "periodicals",
            "20th century", "21st century", "19th century", "18th century",
            "17th century", "16th century", "15th century", "14th century",
            "13th century", "12th century", "11th century", "10th century",
            "middle Ages", "modern times", "history", "biography"
    );

    /** Catalogue pour un genre, en reutilisant le cache de l'accueil. */
    private List<LivreCatalogue> catalogue(String genre, int taille) {
        List<LivreCatalogue> enCache = catalogueCache.get(genre);
        if (enCache != null) {
            return enCache.size() >= taille ? enCache.subList(0, taille) : enCache;
        }
        List<LivreCatalogue> livres = openLibraryService.search(genre, taille);
        if (!livres.isEmpty()) {
            catalogueCache.put(genre, livres);
        }
        return livres;
    }

    private String normaliser(String valeur) {
        return valeur == null ? "" : valeur.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
    }
}
