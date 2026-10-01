import { NgTemplateOutlet } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService, messageErreur } from '../auth/auth.service';
import { formatFr } from '../reservation/date-utils';
import { ThemeService } from '../theme.service';
import { MaReservation, ReservationService } from '../reservation/reservation.service';

/**
 * Espace personnel : informations du compte, theme clair / sombre et livres reserves.
 *
 * La route est protegee par connecteGuard : l'utilisateur est deja charge quand
 * ce composant s'affiche.
 */
@Component({
  selector: 'app-account',
  imports: [NgTemplateOutlet, RouterLink],
  templateUrl: './account.component.html',
  styleUrl: './account.component.css',
})
export class AccountComponent {
  protected readonly auth = inject(AuthService);
  private readonly reservationService = inject(ReservationService);
  protected readonly themeService = inject(ThemeService);
  protected readonly sombre = computed(() => this.themeService.theme() === 'sombre');

  protected readonly formatFr = formatFr;

  // ---- Reservations
  protected readonly reservations = signal<MaReservation[]>([]);
  protected readonly chargement = signal(true);
  protected readonly erreurChargement = signal<string | null>(null);
  protected readonly annulationDemandee = signal<number | null>(null);
  protected readonly annulationEnCours = signal<number | null>(null);
  protected readonly erreurAnnulation = signal<string | null>(null);
  protected readonly couverturesCassees = signal<ReadonlySet<number>>(new Set());

  protected readonly aVenirOuEnCours = computed(() =>
    this.reservations()
      .filter((r) => r.etat !== 'TERMINEE')
      // Les prochaines echeances d'abord.
      .sort((a, b) => a.dateDebut.localeCompare(b.dateDebut)),
  );
  protected readonly terminees = computed(() => this.reservations().filter((r) => r.etat === 'TERMINEE'));

  constructor() {
    void this.charger();
  }

  // ------------------------------------------------------------------ theme

  /** Bascule entre mode clair et mode sombre (memorise dans le navigateur). */
  protected basculerTheme(): void {
    this.themeService.basculer();
  }

  // ------------------------------------------------------------------ reservations

  protected async charger(): Promise<void> {
    this.chargement.set(true);
    this.erreurChargement.set(null);
    try {
      this.reservations.set(await this.reservationService.mesReservations());
    } catch (err) {
      this.erreurChargement.set(messageErreur(err, 'Impossible de charger vos réservations.'));
    } finally {
      this.chargement.set(false);
    }
  }

  protected demanderAnnulation(id: number): void {
    this.erreurAnnulation.set(null);
    this.annulationDemandee.set(id);
  }

  protected renoncerAnnulation(): void {
    this.annulationDemandee.set(null);
  }

  protected async confirmerAnnulation(id: number): Promise<void> {
    this.annulationEnCours.set(id);
    this.erreurAnnulation.set(null);
    try {
      await this.reservationService.annuler(id);
      this.reservations.update((liste) => liste.filter((r) => r.id !== id));
      this.annulationDemandee.set(null);
      void this.auth.rafraichir().catch(() => undefined); // met a jour le compteur du profil
    } catch (err) {
      this.erreurAnnulation.set(messageErreur(err, "L'annulation a échoué. Réessayez."));
    } finally {
      this.annulationEnCours.set(null);
    }
  }

  protected libelleEtat(etat: MaReservation['etat']): string {
    return etat === 'EN_COURS' ? 'En cours' : etat === 'A_VENIR' ? 'À venir' : 'Terminée';
  }

  protected aCouverture(r: MaReservation): boolean {
    return !!r.coverUrl && !this.couverturesCassees().has(r.id);
  }

  protected couvertureCassee(r: MaReservation): void {
    this.couverturesCassees.update((ids) => new Set(ids).add(r.id));
  }
}
