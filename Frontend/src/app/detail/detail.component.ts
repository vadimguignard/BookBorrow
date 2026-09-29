import { Component, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { catchError, forkJoin, of } from 'rxjs';
import { Livre, LivreDetail } from '../livre.model';
import { LivreService } from '../livre.service';

/**
 * Page de detail d'un livre.
 *
 * Disposition demandee : couverture a gauche, informations a droite, puis les
 * recommandations en dessous.
 *
 * Le carrousel de recommandations affiche 5 livres a la fois et 10 au total.
 * Les fleches font defiler d'une carte a la fois ; quand la derniere carte
 * visible est la derniere du lot, on reboucle vers le debut.
 */
@Component({
  selector: 'app-detail',
  imports: [RouterLink],
  templateUrl: './detail.component.html',
  styleUrl: './detail.component.css',
})
export class DetailComponent {
  private readonly livreService = inject(LivreService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  /** Nombre de recommandations affichees simultanement. */
  protected readonly visibles = 5;

  protected readonly livre = signal<LivreDetail | null>(null);
  protected readonly recommandations = signal<Livre[]>([]);
  protected readonly chargement = signal(true);
  protected readonly chargementReco = signal(true);
  protected readonly erreur = signal<string | null>(null);
  protected readonly erreurReco = signal<string | null>(null);
  protected readonly brokenCovers = signal<ReadonlySet<string>>(new Set());
  protected readonly coverBroken = signal(false);

  /** Index du premier recommandation affichee. */
  protected readonly debut = signal(0);

  /**
   * Nombre de groupes de 5 que forme la liste de recommandations.
   * Utilise pour savoir si les fleches doivent boucler.
   */
  protected readonly groupes = computed(() =>
    Math.ceil(this.recommandations().length / this.visibles),
  );

  /** Recommandations visibles a l'ecran, fenetre de 5 cartes. */
  protected readonly fenetre = computed(() => {
    const liste = this.recommandations();
    const debut = this.debut();
    // Boucle : au bout de la liste, on revient au debut.
    const indexes = Array.from({ length: Math.min(this.visibles, liste.length) }, (_, i) => (debut + i) % liste.length);
    return indexes.map((index) => liste[index]);
  });

  constructor() {
    const reference = this.route.snapshot.paramMap.get('reference');
    if (!reference) {
      this.erreur.set("Aucune référence de livre fournie.");
      this.chargement.set(false);
      return;
    }

    this.livreService
      .detail(reference)
      .pipe(catchError(() => of(null)))
      .subscribe({
        next: (detail) => {
          if (!detail) {
            this.erreur.set("Ce livre est introuvable. Il peut ne plus exister dans le catalogue Open Library.");
            this.chargement.set(false);
            return;
          }
          this.livre.set(detail);
          this.chargement.set(false);
          this.chargerRecommandations(reference);
        },
      });
  }

  /**
   * Charge les recommandations a part de la fiche.
   *
   * Les deux appels sont separes pour que la page s'affiche des que la fiche
   * est prete : attendre 2 appels lents d'Open Library avant d'afficher le
   * titre ferait perdre plusieurs secondes pour rien.
   */
  private chargerRecommandations(reference: string): void {
    this.livreService
      .recommandations(reference, 10)
      .pipe(catchError(() => of([] as Livre[])))
      .subscribe({
        next: (liste) => {
          this.recommandations.set(liste);
          this.chargementReco.set(false);
        },
        error: () => {
          this.erreurReco.set("Impossible de charger les recommandations.");
          this.chargementReco.set(false);
        },
      });
  }

  protected defiler(direction: number): void {
    const total = this.recommandations().length;
    if (total === 0) {
      return;
    }
    // Un pas d'un livre ; en bout de liste, on reboucle.
    this.debut.update((d) => (d + direction + total) % total);
  }

  protected peutDefiler(direction: number): boolean {
    return this.recommandations().length > 1;
  }

  protected trackLivre(_index: number, livre: Livre): string {
    return livre.reference;
  }

  /** Cible de navigation : la reference n'est pas encodee, Angular s'en charge. */
  protected lienDetail(livre: Livre): unknown[] {
    return ['/livre', livre.reference];
  }

  protected hasCover(livre: Livre): boolean {
    return !!livre.coverUrl && !this.brokenCovers().has(livre.reference);
  }

  protected markCoverBroken(livre: Livre): void {
    this.brokenCovers.update((refs) => new Set(refs).add(livre.reference));
  }

  /** Date JJ/MM/AAAA attendue par l'énoncé. */
  protected dateFormatee(date: string | null): string | null {
    if (!date) return null;
    const [annee, mois, jour] = date.split('-');
    if (!annee || !mois || !jour) return null;
    return `${jour}/${mois}/${annee}`;
  }

  /**
   * Genre mis en avant (celui qui a servi aux recommandations), ou null.
   * Sert a expliquer d'ou viennent les propositions.
   */
  protected genrePrincipal(): string | null {
    const genres = this.livre()?.genresRecommandation ?? [];
    return genres.length > 0 ? genres[0] : null;
  }

  protected retour(): void {
    this.router.navigate(['/']);
  }
}
