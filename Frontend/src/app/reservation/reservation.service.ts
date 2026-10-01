import { HttpClient, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { eachDay } from './date-utils';

/** Le nom et le prenom ne sont plus envoyes : le serveur reserve au nom du compte connecte. */
export interface ReservationDemande {
  reference: string;
  dateDebut: string; // "AAAA-MM-JJ"
  dateFin: string; // "AAAA-MM-JJ"
}

/** Une reservation de l'utilisateur connecte (GET /api/reservations/mes). */
export interface MaReservation {
  id: number;
  reference: string;
  titre: string;
  auteur: string;
  coverUrl: string | null;
  dateDebut: string; // "AAAA-MM-JJ"
  dateFin: string; // "AAAA-MM-JJ"
  etat: 'A_VENIR' | 'EN_COURS' | 'TERMINEE';
}

interface PeriodeReservee {
  dateDebut: string;
  dateFin: string;
}

/** Lancee quand le serveur repond 409 : la periode est deja prise. */
export class ConflictError extends Error {}

/**
 * Appels HTTP de la reservation. Les informations du livre (titre, auteur, couverture)
 * viennent de LivreService.detail(), comme sur la fiche : ici, uniquement les reservations.
 *
 * La reference Open Library contient des « / » : elle part en parametre de requete (GET)
 * ou dans le corps (POST), jamais dans le chemin.
 */
@Injectable({ providedIn: 'root' })
export class ReservationService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/livres/reservations';

  /** Jours deja reserves pour ce livre, un par un, au format "AAAA-MM-JJ". */
  async getReservedDates(reference: string): Promise<Set<string>> {
    const periodes = await firstValueFrom(
      this.http.get<PeriodeReservee[]>(this.baseUrl, {
        params: new HttpParams().set('reference', reference),
      }),
    );
    return new Set(periodes.flatMap((p) => eachDay(p.dateDebut, p.dateFin)));
  }

  /** Enregistre la reservation. Lance ConflictError si la periode est deja prise. */
  async createReservation(demande: ReservationDemande): Promise<void> {
    try {
      await firstValueFrom(this.http.post(this.baseUrl, demande));
    } catch (err) {
      if (err instanceof HttpErrorResponse && err.status === 409) throw new ConflictError();
      throw err;
    }
  }

  /** Reservations de l'utilisateur connecte, la plus recente d'abord. */
  mesReservations(): Promise<MaReservation[]> {
    return firstValueFrom(this.http.get<MaReservation[]>('/api/reservations/mes'));
  }

  /** Annule l'une de ses reservations (a venir ou en cours). */
  async annuler(id: number): Promise<void> {
    await firstValueFrom(this.http.delete<void>(`/api/reservations/${id}`));
  }
}
