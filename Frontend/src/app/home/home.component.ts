import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Livre } from '../livre.model';
import { LivreService } from '../livre.service';

interface Filtres {
  q: string;
  auteur: string;
  titre: string;
  annee: number | null;
}

/**
 * Page d'accueil : le catalogue, avec recherche et filtres.
 *
 * Le titre de chaque livre est un lien vers sa fiche de detail. La reference
 * Open Library contient des « / » : elle est encodee par encodeURIComponent
 * pour ne pas etre interpretee comme des segments de chemin par le routeur.
 */
@Component({
  selector: 'app-home',
  imports: [FormsModule, RouterLink],
  templateUrl: './home.component.html',
  styleUrl: './home.component.css',
})
export class HomeComponent {
  private readonly livreService = inject(LivreService);
  private lastRequestId = 0;

  protected readonly filtres = signal<Filtres>({ q: '', auteur: '', titre: '', annee: null });
  protected readonly livres = signal<Livre[]>([]);
  protected readonly page = signal(1);
  protected readonly totalElements = signal(0);
  protected readonly totalPages = signal(1);
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly brokenCovers = signal<ReadonlySet<string>>(new Set());

  constructor() {
    this.charger();
  }

  /** Applique la recherche et les filtres : on revient toujours a la page 1. */
  protected appliquerFiltres(): void {
    this.page.set(1);
    this.charger();
  }

  protected changerPage(numero: number): void {
    if (numero < 1 || numero > this.totalPages() || numero === this.page()) {
      return;
    }
    this.page.set(numero);
    this.charger();
    globalThis.scrollTo?.({ top: 0, behavior: 'smooth' });
  }

  protected reinitialiserFiltres(): void {
    this.filtres.set({ q: '', auteur: '', titre: '', annee: null });
    this.appliquerFiltres();
  }

  protected trackLivre(_index: number, livre: Livre): string {
    return livre.reference;
  }

  protected pageCourante(): boolean {
    return this.page() === 1;
  }

  protected pageDerniere(): boolean {
    return this.page() === this.totalPages();
  }

  /** Une page a afficher, y compris quand il n'y en a qu'une. */
  protected pages(): number[] {
    return Array.from({ length: this.totalPages() }, (_, i) => i + 1);
  }

  /** Date JJ/MM/AAAA attendue par l'énoncé. */
  protected dateFormatee(date: string | null): string | null {
    if (!date) return null;
    const [annee, mois, jour] = date.split('-');
    if (!annee || !mois || !jour) return null;
    return `${jour}/${mois}/${annee}`;
  }

  /**
   * Cible de navigation vers la fiche.
   *
   * La reference n'est volontairement PAS encodee : Angular encode deja le
   * segment au moment de construire l'URL. Un encodeURIComponent en plus
   * produirait %252F au lieu de %2F, et la page afficherait « livre introuvable ».
   */
  protected lienDetail(livre: Livre): unknown[] {
    return ['/livre', livre.reference];
  }

  /** Une couverture peut exister dans l'API mais renvoyer une image en erreur. */
  protected hasCover(livre: Livre): boolean {
    return !!livre.coverUrl && !this.brokenCovers().has(livre.reference);
  }

  protected markCoverBroken(livre: Livre): void {
    this.brokenCovers.update((refs) => new Set(refs).add(livre.reference));
  }

  private charger(): void {
    const filtres = this.filtres();
    const requestId = ++this.lastRequestId;

    this.loading.set(true);
    this.error.set(null);
    this.brokenCovers.set(new Set());

    this.livreService
      .page({
        q: filtres.q,
        auteur: filtres.auteur,
        titre: filtres.titre,
        annee: filtres.annee,
        page: this.page(),
      })
      .subscribe({
        next: (resultat) => {
          if (requestId !== this.lastRequestId) return;
          this.livres.set(resultat.livres);
          this.totalElements.set(resultat.totalElements);
          this.totalPages.set(resultat.totalPages);
          this.loading.set(false);
        },
        error: () => {
          if (requestId !== this.lastRequestId) return;
          this.livres.set([]);
          this.totalElements.set(0);
          this.totalPages.set(1);
          this.error.set(
            "Impossible de charger le catalogue. Le service est-il démarré sur http://localhost:8080 ?",
          );
          this.loading.set(false);
        },
      });
  }
}
