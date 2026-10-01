import {
  afterNextRender,
  Component,
  computed,
  ElementRef,
  HostListener,
  inject,
  PLATFORM_ID,
  signal,
  viewChild,
} from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { catchError, of } from 'rxjs';
import { Livre, LivreDetail } from '../livre.model';
import { LivreService } from '../livre.service';

/**
 * Page de detail d'un livre.
 *
 * Disposition demandee : couverture a gauche, informations a droite, puis les
 * recommandations en dessous.
 *
 * Le carousel est une fenetre de largeur fixe qui ne montre qu'une page de
 * livres, comme un cadre de diapositives : la piste contient tous les livres et
 * la fenetre en masque la totalite, sauf une page entiere. Le decalage est
 * mesure en pixels a partir de la largeur reelle d'une carte, jamais calcule en
 * pourcentage CSS : un pourcentage se rapporte a la largeur de la piste, qui
 * contient justement tous les livres, et non a celle de la fenetre. C'est ce
 * qui faisait apparaitre les huit livres simultanement.
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

  protected readonly livre = signal<LivreDetail | null>(null);
  protected readonly recommandations = signal<Livre[]>([]);
  protected readonly chargement = signal(true);
  protected readonly chargementReco = signal(true);
  protected readonly erreur = signal<string | null>(null);
  protected readonly erreurReco = signal<string | null>(null);
  protected readonly brokenCovers = signal<ReadonlySet<string>>(new Set<string>());
  /** Couverture de la fiche principale tombee en erreur : on masque l'image. */
  protected readonly coverBroken = signal(false);

  /** Page affichee, indexee a partir de zero. */
  private readonly pageCourante = signal(0);

  /** Nombre de cartes visibles, pilote par les media queries du CSS. */
  private readonly cartesParPage = signal(4);

  /** Largeur de la fenetre du carousel, relevee apres le rendu. */
  private readonly largeurFenetre = signal(0);

  /**
   * Decalage de la piste, en pixels, applique par le CSS.
   *
   * C'est une valeur calculee et non un simple compteur : le template s'y lie
   * directement, donc l'affichage ne peut pas diverger de l'etat de la page.
   * Tant que la largeur n'a pas ete relevee, le decalage reste nul, ce qui laisse
   * la premiere page correctement alignee.
   */
  protected readonly decalage = computed(() => this.pageCourante() * this.largeurFenetre());

  /**
   * Derniere page possible.
   *
   * Une page qui ne serait pas entierement remplie reste valide : avec dix
   * livres, la troisieme page n'en montre que deux. La.floor arrondit au
   * nombre de pages remplies, et le reste est affiche tel quel.
   */
  private readonly dernierePage = computed(() => {
    const total = this.recommandations().length;
    if (total === 0) {
      return 0;
    }
    return Math.floor((total - 1) / this.cartesParPage());
  });

  protected readonly peutPrecedent = computed(() => this.pageCourante() > 0);
  protected readonly peutSuivant = computed(() => this.pageCourante() < this.dernierePage());

  private readonly piste = viewChild<ElementRef<HTMLElement>>('piste');

  private readonly estNavigateur = isPlatformBrowser(inject(PLATFORM_ID));

  /** Phrase d'introduction de la section de recommandations. */
  protected readonly introduction = "Découvrez une sélection de livres à explorer";

  constructor() {
    /*
     * La piste est mesuree apres le rendu, jamais dans le constructeur : les
     * recommandations arrivent plus tard et la largeur n'existe qu'une fois la
     * mise en page faite. Le decalage vaut aussi 0 au premier rendu, ce qui
     * laisse la premiere page correctement alignee cote serveur.
     */
    afterNextRender(() => this.mesurer());

    /*
     * On ecoute `paramMap` et non `snapshot`. Angular reutilise l'instance du
     * composant quand on passe d'un livre a un autre, car la route est la meme :
     * seul le parametre change. Lire la reference dans le constructeur
     * fonctionnerait a la premiere visite, puis le clic sur une
     * recommandation ne重新chargerait rien et afficherait encore le livre
     * precedent.
     */
    this.route.paramMap.subscribe((params) => {
      const reference = params.get('reference');
      if (!reference) {
        this.livre.set(null);
        this.recommandations.set([]);
        this.erreur.set("Aucune référence de livre fournie.");
        this.chargement.set(false);
        this.chargementReco.set(false);
        return;
      }
      this.chargerLivre(reference);
    });
  }

  /**
   * Charge la fiche puis ses recommandations.
   *
   * L'etat est remis a zero avant l'appel : sans cela, l'ancien livre resterait
   * affiche pendant le chargement du nouveau, et la page semblerait ne pas
   * reagir au clic.
   */
  private chargerLivre(reference: string): void {
    this.erreur.set(null);
    this.erreurReco.set(null);
    this.chargement.set(true);
    this.chargementReco.set(true);
    this.livre.set(null);
    this.recommandations.set([]);
    this.coverBroken.set(false);
    this.brokenCovers.set(new Set<string>());
    // On revient a la premiere page : la seconde page de l'ancien livre
    // n'existe pas forcément pour le nouveau. Le decalage en decoule, puisque
    // c'est la page multipliee par la largeur de la fenetre.
    this.pageCourante.set(0);

    this.livreService
      .detail(reference)
      .pipe(catchError(() => of(null)))
      .subscribe({
        next: (detail) => {
          if (!detail) {
            this.erreur.set("Ce livre est introuvable. Il peut ne plus exister dans le catalogue Open Library.");
            this.chargement.set(false);
            this.chargementReco.set(false);
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
   * est prete : attendre deux appels lents d'Open Library avant d'afficher le
   * titre ferait perdre plusieurs secondes pour rien.
   */
  private chargerRecommandations(reference: string): void {
    this.livreService
      .recommandations(reference, 8)
      .pipe(catchError(() => of([] as Livre[])))
      .subscribe({
        next: (liste) => {
          this.recommandations.set(liste);
          this.chargementReco.set(false);
          /*
           * La largeur des cartes n'est connue qu'une fois les livres rendus.
           * Sans cette remesure, le decalage garderait la valeur de la page
           * precedente, qui ne correspond plus a la largeur de la nouvelle piste.
           *
           * On attend une frame plutot que d'appeler directement : les liens du
           * DOM n'existent pas encore au moment de l'ecriture du signal, et
           * `afterNextRender` exige un contexte d'injection qui n'est plus
           * disponible dans cet abonnement.
           */
          requestAnimationFrame(() => this.mesurer());
        },
        error: () => {
          this.erreurReco.set("Impossible de charger les recommandations.");
          this.chargementReco.set(false);
        },
      });
  }

  /**
   * Largeur en pixels d'une page de cartes.
   *
   * Elle vaut exactement la largeur de la fenetre, et non la largeur d'une
   * carte. Chaque carte occupe (fenetre - n gouttieres) / n : n cartes et n
   * gouttieres remplissent donc la fenetre au pixel pres, la derniere
   * gouttiere faisant office d'espace avant la premiere carte de la page
   * suivante. Un cran vaut ainsi une fenetre entiere, ce qui garantit qu'aucune
   * carte n'apparait a moitie entre deux pages.
   */
  private afficherPage(page: number): void {
    const cible = Math.min(Math.max(page, 0), this.dernierePage());
    this.pageCourante.set(cible);
  }

  /**
   * Bouton suivant : une page complete de plus.
   *
   * Le pas est la largeur d'une page entiere, pas celle d'une carte, pour que
   * l'on passe de quatre livres visibles a quatre autres sans mixture.
   */
  protected pageSuivante(): void {
    this.afficherPage(this.pageCourante() + 1);
  }

  protected pagePrecedente(): void {
    this.afficherPage(this.pageCourante() - 1);
  }

  /**
   * Releve la largeur de la fenetre et le nombre de cartes visibles.
   *
   * Ces deux valeurs viennent du DOM et non du CSS : dupliquer le nombre de
   * cartes ici le desynchroniserait de la premiere media query modifiee, et le
   * decalage ne correspondrait plus a la page affichee.
   *
   * Sans cet appel, la largeur reste a zero, le decalage vaut donc toujours
   * zero et le clic sur les fleches ne deplace rien. C'est la source du
   * decalage, il doit donc etre appele apres chaque rendu de la piste.
   */
  protected mesurer(): void {
    const fenetre = this.piste()?.nativeElement;
    if (!fenetre || !this.estNavigateur) {
      return;
    }

    /*
     * Une fenetre de largeur nulle signifie que la mise en page n'est pas
     * encore faite. On ne touche alors a rien : ecrire zero ici mettrait le
     * nombre de cartes par page a 1, le carousel afficherait une page a la
     * fois et la premiere remesure ne pourrait plus revenir en arriere.
     */
    if (fenetre.clientWidth === 0) {
      return;
    }
    this.largeurFenetre.set(fenetre.clientWidth);

    const cartes = fenetre.querySelectorAll<HTMLElement>('.mini-carte');
    if (cartes.length === 0) {
      return;
    }

    /*
     * Les positions sont relevees par rapport a la fenetre, translatee de
     * l'offset courant : on recompte ainsi les cartes visibles de la page
     * affichee, et non celles de la premiere page.
     */
    const gaucheFenetre = fenetre.getBoundingClientRect().left;
    const largeur = fenetre.clientWidth;
    const decalage = this.decalage();

    let visibles = 0;
    for (const carte of cartes) {
      const rect = carte.getBoundingClientRect();
      if (rect.left - gaucheFenetre + decalage < largeur - 1) {
        visibles += 1;
      } else {
        break;
      }
    }
    this.cartesParPage.set(Math.max(visibles, 1));

    /*
     * Le nombre de cartes par page vient de changer, donc la page courante
     * n'est plus forcement la derniere : on se repositionne pour ne jamais
     * afficher une page vide apres un redimensionnement.
     */
    this.afficherPage(this.pageCourante());
  }

  /**
   * Au redimensionnement, le nombre de cartes visibles change et le decalage
   * doit etre recalcule. Sans cela, passer d'un grand ecran a un telephone
   * decalerait la piste d'une page de grand ecran, ce qui laisserait un vide.
   *
   * Le second argument limite l'ecoute aux changements de largeur : un
   * changement de hauteur seul ne change pas la largeur d'une carte.
   */
  @HostListener('window:resize')
  protected surRedimensionnement(): void {
    this.mesurer();
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

  protected retour(): void {
    this.router.navigate(['/']);
  }
}
