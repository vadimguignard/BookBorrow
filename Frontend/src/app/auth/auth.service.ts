import { isPlatformBrowser } from '@angular/common';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, PLATFORM_ID, computed, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';

/** Profil de l'utilisateur connecte (jamais de mot de passe). */
export interface Utilisateur {
  id: number;
  username: string;
  dateInscription: string; // "AAAA-MM-JJ"
  nombreReservations: number;
}

export interface Inscription {
  username: string;
  motDePasse: string;
  confirmationMotDePasse: string;
}

interface AuthReponse {
  token: string;
  utilisateur: Utilisateur;
}

const CLE_JETON = 'bookborrow.jeton';

/** Message d'erreur renvoye par l'API ({ "error": "..." }), ou un texte par defaut. */
export function messageErreur(err: unknown, parDefaut: string): string {
  if (err instanceof HttpErrorResponse) {
    if (err.status === 0) return 'Le serveur est injoignable. Réessayez dans un instant.';
    const message = (err.error as { error?: unknown } | null)?.error;
    if (typeof message === 'string' && message) return message;
  }
  return parDefaut;
}

/**
 * Etat d'authentification de l'application.
 *
 * Le jeton est garde dans le localStorage (la session survit a un rechargement).
 * Le constructeur ne fait AUCUN appel HTTP : l'intercepteur injecte ce service,
 * un appel ici creerait une dependance circulaire. La session est restauree par
 * {@link restaurer}, appele par le menu et par les gardes de route.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly navigateur = isPlatformBrowser(inject(PLATFORM_ID));

  private jetonCourant: string | null = this.lireJeton();
  private restauration: Promise<void> | null = null;

  readonly utilisateur = signal<Utilisateur | null>(null);
  readonly connecte = computed(() => this.utilisateur() !== null);
  /** Vrai une fois la session restauree (ou constatee absente). */
  readonly pret = signal(false);

  /** Jeton a envoyer dans l'en-tete Authorization, ou null. */
  get jeton(): string | null {
    return this.jetonCourant;
  }

  /** Restaure la session depuis le jeton memorise. Idempotent : un seul appel reseau. */
  restaurer(): Promise<void> {
    this.restauration ??= this.charger();
    return this.restauration;
  }

  async inscription(donnees: Inscription): Promise<void> {
    this.ouvrirSession(await firstValueFrom(this.http.post<AuthReponse>('/api/auth/inscription', donnees)));
  }

  async connexion(username: string, motDePasse: string): Promise<void> {
    this.ouvrirSession(
      await firstValueFrom(this.http.post<AuthReponse>('/api/auth/connexion', { username, motDePasse })),
    );
  }

  deconnexion(): void {
    this.oublier();
  }

  /** Appelee par l'intercepteur quand le serveur refuse un jeton (expire ou invalide). */
  sessionExpiree(): void {
    this.oublier();
  }

  /** Recharge le profil (par exemple pour mettre a jour le nombre de reservations). */
  async rafraichir(): Promise<void> {
    this.utilisateur.set(await firstValueFrom(this.http.get<Utilisateur>('/api/auth/moi')));
  }

  // ------------------------------------------------------------------ interne

  private async charger(): Promise<void> {
    if (this.jetonCourant) {
      try {
        await this.rafraichir();
      } catch (err) {
        // 401 : le jeton est expire ou invalide, on l'oublie.
        // Autre erreur (serveur arrete...) : on garde le jeton, l'utilisateur pourra reessayer.
        if (err instanceof HttpErrorResponse && err.status === 401) this.oublier();
      }
    }
    this.pret.set(true);
  }

  private ouvrirSession(reponse: AuthReponse): void {
    this.jetonCourant = reponse.token;
    this.ecrireJeton(reponse.token);
    this.utilisateur.set(reponse.utilisateur);
    // Une session ouverte a la main n'a plus rien a restaurer.
    this.restauration = Promise.resolve();
    this.pret.set(true);
  }

  private oublier(): void {
    this.jetonCourant = null;
    this.ecrireJeton(null);
    this.utilisateur.set(null);
  }

  // Le localStorage n'existe pas cote serveur (SSR) et peut etre bloque par le navigateur.
  private lireJeton(): string | null {
    if (!this.navigateur) return null;
    try {
      return globalThis.localStorage.getItem(CLE_JETON);
    } catch {
      return null;
    }
  }

  private ecrireJeton(jeton: string | null): void {
    if (!this.navigateur) return;
    try {
      if (jeton) globalThis.localStorage.setItem(CLE_JETON, jeton);
      else globalThis.localStorage.removeItem(CLE_JETON);
    } catch {
      // Stockage indisponible : la session durera le temps de l'onglet seulement.
    }
  }
}
