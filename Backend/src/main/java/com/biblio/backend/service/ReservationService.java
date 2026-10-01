package com.biblio.backend.service;

import com.biblio.backend.domain.Livre;
import com.biblio.backend.domain.Reservation;
import com.biblio.backend.domain.Statut;
import com.biblio.backend.domain.Utilisateur;
import com.biblio.backend.dto.LivreDetailDto;
import com.biblio.backend.dto.MesReservationDto;
import com.biblio.backend.dto.PeriodeReserveeDto;
import com.biblio.backend.dto.ReservationDemandeDto;
import com.biblio.backend.dto.ReservationDto;
import com.biblio.backend.repository.LivreRepository;
import com.biblio.backend.repository.ReservationRepository;
import com.biblio.backend.repository.UtilisateurRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reservation des livres.
 *
 * Les reservations vivent dans la table reservation. Le statut (LIBRE / RESERVE)
 * et la date de disponibilite du Livre restent, comme avant, la seule source lue
 * par le catalogue et la fiche : ce service les met a jour a chaque reservation,
 * et {@link #synchroniserStatuts()} les rafraichit chaque jour (une reservation
 * qui commence demain doit faire passer le livre a RESERVE demain, pas aujourd'hui).
 */
@Service
public class ReservationService {

    private static final Logger log = LoggerFactory.getLogger(ReservationService.class);
    private static final Pattern COVER_ID = Pattern.compile("/b/id/(\\d+)-");

    private final LivreRepository livreRepository;
    private final ReservationRepository reservationRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final LivreDetailService livreDetailService;
    private final TransactionTemplate transaction;

    public ReservationService(LivreRepository livreRepository,
                              ReservationRepository reservationRepository,
                              UtilisateurRepository utilisateurRepository,
                              LivreDetailService livreDetailService,
                              TransactionTemplate transaction) {
        this.livreRepository = livreRepository;
        this.reservationRepository = reservationRepository;
        this.utilisateurRepository = utilisateurRepository;
        this.livreDetailService = livreDetailService;
        this.transaction = transaction;
    }

    /** Periodes deja reservees (pas encore terminees) pour ce livre. Vide si le livre n'a jamais ete reserve. */
    @Transactional(readOnly = true)
    public List<PeriodeReserveeDto> periodesReservees(String reference) {
        String canonique = canonique(reference);
        return livreRepository.findByReference(canonique)
                .map(livre -> reservationRepository
                        .findByLivreIdAndDateFinGreaterThanEqualOrderByDateDebutAsc(livre.getId(), LocalDate.now())
                        .stream()
                        .map(r -> new PeriodeReserveeDto(r.getDateDebut(), r.getDateFin()))
                        .toList())
                .orElse(List.of());
    }

    /**
     * Enregistre une reservation.
     *
     * Pas de @Transactional global : la premiere reservation d'un livre absent de
     * notre base appelle Open Library (plusieurs secondes), et on ne veut pas garder
     * une transaction ouverte pendant ce temps. Seule la partie « verifier + enregistrer »
     * est transactionnelle, avec le livre verrouille.
     */
    public ReservationDto reserver(ReservationDemandeDto demande, Long utilisateurId) {
        Utilisateur utilisateur = utilisateurRepository.findById(utilisateurId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Session expirée."));
        LocalDate aujourdhui = LocalDate.now();
        if (demande.dateDebut().isBefore(aujourdhui)) {
            throw new ReservationInvalideException("La date de début ne peut pas être dans le passé.");
        }
        if (!demande.dateFin().isAfter(demande.dateDebut())) {
            throw new ReservationInvalideException("La date de fin doit être après la date de début.");
        }

        Livre livre = obtenirOuCreerLivre(demande.reference());

        Reservation reservation = transaction.execute(status -> {
            Livre verrouille = livreRepository.findByIdForUpdate(livre.getId()).orElseThrow();

            if (reservationRepository.existeChevauchement(verrouille.getId(), demande.dateDebut(), demande.dateFin())) {
                throw new ReservationConflitException();
            }

            Reservation creee = reservationRepository.save(new Reservation(
                    verrouille,
                    utilisateur,
                    demande.dateDebut(),
                    demande.dateFin()));
            appliquerStatut(verrouille, aujourdhui);
            return creee;
        });

        log.info("Reservation {} par l'utilisateur {} : {} du {} au {}", reservation.getId(), utilisateurId,
                livre.getReference(), reservation.getDateDebut(), reservation.getDateFin());

        return new ReservationDto(reservation.getId(), livre.getReference(),
                reservation.getDateDebut(), reservation.getDateFin());
    }

    /** Reservations de l'utilisateur connecte, la plus recente d'abord. */
    @Transactional(readOnly = true)
    public List<MesReservationDto> mesReservations(Long utilisateurId) {
        LocalDate aujourdhui = LocalDate.now();
        return reservationRepository.findByUtilisateurAvecLivre(utilisateurId).stream()
                .map(r -> new MesReservationDto(
                        r.getId(),
                        r.getLivre().getReference(),
                        r.getLivre().getTitre(),
                        r.getLivre().getAuteur(),
                        r.getLivre().getCoverId() == null ? null
                                : "https://covers.openlibrary.org/b/id/%d-M.jpg".formatted(r.getLivre().getCoverId()),
                        r.getDateDebut(),
                        r.getDateFin(),
                        etat(r, aujourdhui)))
                .toList();
    }

    /**
     * Annule une reservation de l'utilisateur (a venir ou en cours).
     * Une reservation d'un autre utilisateur est traitee comme introuvable (404) :
     * on ne revele pas son existence.
     */
    public void annuler(Long utilisateurId, Long reservationId) {
        LocalDate aujourdhui = LocalDate.now();
        transaction.executeWithoutResult(status -> {
            Reservation reservation = reservationRepository.findByIdAndUtilisateurId(reservationId, utilisateurId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Réservation introuvable."));
            if (reservation.getDateFin().isBefore(aujourdhui)) {
                throw new ReservationInvalideException("Cette réservation est terminée, elle ne peut plus être annulée.");
            }
            Livre verrouille = livreRepository.findByIdForUpdate(reservation.getLivre().getId()).orElseThrow();
            reservationRepository.delete(reservation);
            reservationRepository.flush();
            appliquerStatut(verrouille, aujourdhui);
        });
        log.info("Reservation {} annulee par l'utilisateur {}", reservationId, utilisateurId);
    }

    /** Recalcule le statut de tous les livres connus. Appele au demarrage puis chaque nuit. */
    @Transactional
    public void synchroniserStatuts() {
        LocalDate aujourdhui = LocalDate.now();
        for (Livre livre : livreRepository.findAll()) {
            appliquerStatut(livre, aujourdhui);
        }
    }

    // ------------------------------------------------------------------ interne

    /** Reference stockee en base : toujours « /works/OLxxxxW ». Accepte aussi « OLxxxxW ». */
    private String canonique(String reference) {
        String cle = OpenLibraryDetailService.normaliser(reference);
        if (cle == null) {
            throw new OuvrageIntrouvableException(String.valueOf(reference));
        }
        return "/works/" + cle;
    }

    /**
     * Livre de notre base, cree a la volee s'il n'y est pas encore.
     *
     * Le catalogue affiche des livres Open Library qui ne sont pas tous dans notre
     * base. Pour pouvoir en reserver un, on l'y ajoute avec ses informations
     * descriptives (titre, auteur, annee, couverture), exactement comme data.sql.
     */
    private Livre obtenirOuCreerLivre(String reference) {
        String canonique = canonique(reference);
        var existant = livreRepository.findByReference(canonique);
        if (existant.isPresent()) {
            return existant.get();
        }

        // Leve OuvrageIntrouvableException (404) si l'ouvrage n'existe pas chez Open Library.
        LivreDetailDto detail = livreDetailService.detail(canonique);

        Livre livre = new Livre();
        livre.setReference(canonique);
        livre.setTitre(tronquer(detail.titre(), 512));
        livre.setAuteur(tronquer(detail.auteur() == null ? "Auteur inconnu" : detail.auteur(), 255));
        livre.setAnneePublication(detail.anneePublication());
        livre.setCoverId(extraireCoverId(detail.coverUrl()));
        livre.setStatut(Statut.LIBRE);

        try {
            return livreRepository.saveAndFlush(livre);
        } catch (DataIntegrityViolationException e) {
            // Une autre requete vient de creer le meme livre : on reprend le sien.
            return livreRepository.findByReference(canonique).orElseThrow(() -> e);
        }
    }

    private void appliquerStatut(Livre livre, LocalDate aujourdhui) {
        List<Reservation> reservations = reservationRepository
                .findByLivreIdAndDateFinGreaterThanEqualOrderByDateDebutAsc(livre.getId(), aujourdhui);
        StatutCalcule calcule = calculerStatut(reservations, aujourdhui);
        livre.setStatut(calcule.statut());
        livre.setDateDisponibilite(calcule.dateDisponibilite());
        livreRepository.save(livre);
    }

    /** Resultat du calcul : statut du jour et, si RESERVE, premier jour libre. */
    record StatutCalcule(Statut statut, LocalDate dateDisponibilite) {
    }

    /**
     * Statut d'un livre a une date donnee, deduit de ses reservations (triees par date de debut).
     *
     * RESERVE si une reservation couvre la date ; la date de disponibilite est alors le
     * lendemain de la derniere reservation de la chaine (des reservations qui s'enchainent
     * jour pour jour comptent comme une seule). Sinon LIBRE, sans date.
     */
    static StatutCalcule calculerStatut(List<Reservation> reservations, LocalDate aujourdhui) {
        LocalDate curseur = aujourdhui;
        boolean occupe = false;
        for (Reservation r : reservations) {
            boolean couvreCurseur = !r.getDateDebut().isAfter(curseur) && !r.getDateFin().isBefore(curseur);
            if (couvreCurseur) {
                occupe = true;
                curseur = r.getDateFin().plusDays(1);
            }
        }
        return occupe
                ? new StatutCalcule(Statut.RESERVE, curseur)
                : new StatutCalcule(Statut.LIBRE, null);
    }

    /** A_VENIR, EN_COURS ou TERMINEE selon la position d'aujourd'hui par rapport aux deux bornes. */
    static String etat(Reservation r, LocalDate aujourdhui) {
        if (r.getDateFin().isBefore(aujourdhui)) {
            return "TERMINEE";
        }
        return r.getDateDebut().isAfter(aujourdhui) ? "A_VENIR" : "EN_COURS";
    }

    private static String tronquer(String valeur, int max) {
        String propre = valeur.trim();
        return propre.length() <= max ? propre : propre.substring(0, max);
    }

    private static Long extraireCoverId(String coverUrl) {
        if (coverUrl == null) {
            return null;
        }
        Matcher m = COVER_ID.matcher(coverUrl);
        return m.find() ? Long.valueOf(m.group(1)) : null;
    }
}
