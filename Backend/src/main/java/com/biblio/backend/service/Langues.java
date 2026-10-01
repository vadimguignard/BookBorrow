package com.biblio.backend.service;

import com.biblio.backend.dto.EditionsResponse;

import java.util.List;
import java.util.Map;

/**
 * Traduit les codes de langue d'Open Library ("eng", "fre") en libelles
 * francais lisibles ("Anglais", "Francais").
 *
 * Open Library renvoie /languages/eng et non un nom : sans cette table la page
 * de detail afficherait « eng », ce qui n'apprend rien a l'utilisateur.
 */
final class Langues {

    private static final Map<String, String> LIBELLES = Map.ofEntries(
            Map.entry("eng", "Anglais"),
            Map.entry("fre", "Francais"),
            Map.entry("fra", "Francais"),
            Map.entry("spa", "Espagnol"),
            Map.entry("ger", "Allemand"),
            Map.entry("deu", "Allemand"),
            Map.entry("ita", "Italien"),
            Map.entry("por", "Portugais"),
            Map.entry("nld", "Neerlandais"),
            Map.entry("dut", "Neerlandais"),
            Map.entry("rus", "Russe"),
            Map.entry("jpn", "Japonais"),
            Map.entry("chi", "Chinois"),
            Map.entry("zho", "Chinois"),
            Map.entry("ara", "Arabe"),
            Map.entry("heb", "Hebreu"),
            Map.entry("hin", "Hindi"),
            Map.entry("kor", "Coreen"),
            Map.entry("pol", "Polonais"),
            Map.entry("swe", "Suédois"),
            Map.entry("nor", "Norvégien"),
            Map.entry("dan", "Danois"),
            Map.entry("fin", "Finnois"),
            Map.entry("tur", "Turc"),
            Map.entry("hun", "Hongrois"),
            Map.entry("cze", "Tchèque"),
            Map.entry("ces", "Tchèque"),
            Map.entry("ron", "Roumain"),
            Map.entry("rum", "Roumain"),
            Map.entry("gre", "Grec"),
            Map.entry("ell", "Grec"),
            Map.entry("lat", "Latin"),
            Map.entry("vie", "Vietnamien"),
            Map.entry("tha", "Thaïs"),
            Map.entry("ind", "Indonésien"),
            Map.entry("cat", "Catalan"),
            Map.entry("gle", "Irlandais"),
            Map.entry("ice", "Islandais"),
            Map.entry("srp", "Serbe"),
            Map.entry("hrv", "Croat"),
            Map.entry("bul", "Bulgare"),
            Map.entry("est", "Estonien"),
            Map.entry("lav", "Letton"),
            Map.entry("lit", "Lituanien"),
            Map.entry("slv", "Slovène"),
            Map.entry("slk", "Slovaque"),
            Map.entry("ukr", "Ukrainien"),
            Map.entry("mkd", "Macedonien"),
            Map.entry("bel", "Biélorusse"),
            Map.entry("baq", "Basque"),
            Map.entry("wel", "Gallois"),
            Map.entry("bre", "Breton"),
            Map.entry("glg", "Galicien"),
            Map.entry("isl", "Islandais"),
            Map.entry("afr", "Afrikaans"),
            Map.entry("urd", "Ourdou"),
            Map.entry("ben", "Bengali"),
            Map.entry("tam", "Tamoul"),
            Map.entry("tel", "Telugu"),
            Map.entry("mar", "Marathi"),
            Map.entry("mal", "Malayalam"),
            Map.entry("kan", "Kannada"),
            Map.entry("guj", "Gujarati"),
            Map.entry("pan", "Panjabi"),
            Map.entry("nep", "Nepalais"),
            Map.entry("mlt", "Maltais"),
            Map.entry("arm", "Arménien"),
            Map.entry("geo", "Géorgien"),
            Map.entry("yid", "Yiddish"),
            Map.entry("lad", "Ladino"),
            Map.entry("bih", "Bihari"),
            Map.entry("ori", "Odia"),
            Map.entry("san", "Sanskrit"),
            Map.entry("tib", "Tibétain"),
            Map.entry("div", "Divehi"),
            Map.entry("kur", "Kurde")
    );

    private Langues() {
    }

    /** Libelle francais d'un code de langue, ou null si le code est inconnu. */
    static String libelle(String code) {
        if (code == null) {
            return null;
        }
        String propre = code.trim().toLowerCase();
        if (propre.isEmpty()) {
            return null;
        }
        return LIBELLES.get(propre);
    }

    /**
     * Extrait le code du premier langage d'une edition.
     *
     * Open Library renvoie soit une liste de references, soit un objet unique
     * selon les editions : les deux formes sont traitees ici.
     */
    static String premierCode(List<EditionsResponse.LanguageRef> langages) {
        if (langages == null || langages.isEmpty()) {
            return null;
        }
        EditionsResponse.LanguageRef ref = langages.get(0);
        if (ref == null || ref.key() == null) {
            return null;
        }
        int slash = ref.key().lastIndexOf('/');
        return slash < 0 ? ref.key() : ref.key().substring(slash + 1);
    }
}
