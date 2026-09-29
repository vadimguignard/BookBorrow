import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute, provideRouter } from '@angular/router';
import { DetailComponent } from './detail.component';
import { LivreService } from '../livre.service';
import { Livre, LivreDetail } from '../livre.model';
import { BehaviorSubject, of } from 'rxjs';

/**
 * Regle metier du carousel : combien de pages de recommandations, et quelles
 * sont accessible par les fleches.
 *
 * Ces tests ne verifient pas le CSS, mais la logique de page. C'est elle qui
 * decide si le bouton « suivant » s'active, et une erreur ici se traduit par
 * des livres inaccessibles ou une page vide en fin de piste.
 */
function nombreDePages(total: number, cartesParPage: number): number {
  if (total === 0) {
    return 1;
  }
  return Math.floor((total - 1) / cartesParPage) + 1;
}

function livre(index: number): Livre {
  return {
    reference: `/works/OL${index}W`,
    titre: `Livre ${index}`,
    auteur: 'Auteur',
    statut: 'LIBRE',
  } as Livre;
}

function detail(): LivreDetail {
  return {
    reference: '/works/OL1W',
    titre: 'Livre de reference',
    auteur: 'Auteur',
    anneePublication: 1997,
    coverUrl: null,
    description: null,
    langue: 'Anglais',
    nombrePages: null,
    datePublication: null,
    isbn: [],
    genres: ['Fantasy'],
    genresRecommandation: ['Fantasy'],
    statut: 'LIBRE',
    dateDisponibilite: null,
    disponible: true,
  };
}

/**
 * Le composant expose son etat de page via les signaux proteges. On passe par
 * le prototype pour lire ce que le template lit, sans dupliquer la logique.
 */
function pages(component: DetailComponent): { precedente: boolean; suivante: boolean } {
  const anyComponent = component as unknown as Record<string, () => boolean>;
  return {
    precedente: anyComponent['peutPrecedent'](),
    suivante: anyComponent['peutSuivant'](),
  };
}

/**
 * Le composant s'abonne a `paramMap`, pas a `snapshot` : on lui fournit donc un
 * BehaviorSubject que le test peut faire emettre pour simuler un clic sur une
 * recommandation.
 *
 * Chaque emission est un objet `{ get }` et non une chaine, pour respecter la
 * forme reelle de `ActivatedRoute.paramMap`.
 */
interface Faux {
  fixture: ComponentFixture<DetailComponent>;
  composant: DetailComponent;
  params: BehaviorSubject<{ get: (cle: string) => string | null }>;
  livresDemandes: string[];
}

/** Largeur de fenetre simulee, en pixels. */
const LARGEUR_FENETRE = 1200;

/**
 * Simule une fenetre de carousel de largeur donnee.
 *
 * jsdom ne fait aucune mise en page : `clientWidth` vaut toujours zero et les
 * rectangles sont tous vides. Sans cette simulation, le decalage resterait nul
 * et le clic sur les fleches ne produirait aucun deplacement, ce qui laisserait
 * passer le bug le plus grave du carousel.
 */
function simulerFenetre(fixture: ComponentFixture<DetailComponent>, largeur: number): void {
  const piste = (fixture.nativeElement as HTMLElement).querySelector('.piste') as HTMLElement | null;
  if (!piste) {
    return;
  }
  Object.defineProperty(piste, 'clientWidth', { value: largeur, configurable: true });

  const pisteUl = (fixture.nativeElement as HTMLElement).querySelector('.carousel__piste') as HTMLElement | null;
  // Gouttiere de 24 px, quatre cartes par page : (1200 - 4 x 24) / 4 = 276 px.
  const carte = (largeur - 4 * 24) / 4;
  const pas = carte + 24;

  /*
   * La position des cartes est recalculee a chaque lecture, en retranchant le
   * `translateX` reellement applique par le binding. Sans cela, les rectangles
   * resteraient figes et un clic sur la fleche ne changerait rien : le test
   * validerait un carousel immobile, ce qui est exactement le bug signale.
   */
  const decalageLu = (): number => {
    const trouve = /translateX\(-?(\d+(?:\.\d+)?)px\)/.exec(pisteUl?.style.transform ?? '');
    return trouve ? Number.parseFloat(trouve[1]) : 0;
  };

  [...piste.querySelectorAll<HTMLElement>('.mini-carte')].forEach((element, index) => {
    element.getBoundingClientRect = () => {
      const gauche = index * pas - decalageLu();
      return {
        left: gauche,
        right: gauche + carte,
        width: carte,
        top: 0,
        bottom: 300,
        height: 300,
        x: gauche,
        y: 0,
        toJSON: () => ({}),
      } as DOMRect;
    };
  });
  piste.getBoundingClientRect = () =>
    ({ left: 0, right: largeur, width: largeur, top: 0, bottom: 0, height: 300, x: 0, y: 0, toJSON: () => ({}) }) as DOMRect;
}

/** Deplacement reellement applique a la piste par le binding du template. */
function transformApplique(fixture: ComponentFixture<DetailComponent>): string {
  const piste = (fixture.nativeElement as HTMLElement).querySelector('.carousel__piste') as HTMLElement | null;
  return piste?.style.transform ?? '';
}

/** Titres des livres dont la couverture est visible dans la fenetre. */
function couverturesVisibles(fixture: ComponentFixture<DetailComponent>): string[] {
  const piste = (fixture.nativeElement as HTMLElement).querySelector('.piste') as HTMLElement | null;
  if (!piste) {
    return [];
  }
  const gauche = piste.getBoundingClientRect().left;
  const largeur = piste.clientWidth;
  return [...piste.querySelectorAll<HTMLElement>('.mini-carte')]
    .filter((carte) => {
      const r = carte.getBoundingClientRect();
      return r.left >= gauche - 0.5 && r.right <= gauche + largeur + 0.5;
    })
    .map((carte) => carte.querySelector('.mini-carte__titre')?.textContent?.trim() ?? '');
}

async function creerDetail(nombreRecommandations: number): Promise<Faux> {
  const livres = Array.from({ length: nombreRecommandations }, (_, i) => livre(i + 1));
  const params = new BehaviorSubject<{ get: (cle: string) => string | null }>({
    get: (cle: string) => (cle === 'reference' ? '/works/OL1W' : null),
  });
  const livresDemandes: string[] = [];

  const livreService = {
    detail: (reference: string) => {
      livresDemandes.push(reference);
      return of(detail());
    },
    recommandations: () => of(livres),
  };

  await TestBed.configureTestingModule({
    imports: [DetailComponent],
    providers: [
      provideRouter([]),
      provideHttpClient(),
      provideHttpClientTesting(),
      { provide: LivreService, useValue: livreService },
      { provide: ActivatedRoute, useValue: { paramMap: params } },
    ],
  }).compileComponents();

  const fixture = TestBed.createComponent(DetailComponent);
  fixture.detectChanges();

  return { fixture, composant: fixture.componentInstance, params, livresDemandes };
}

describe('carousel de recommandations', () => {
  describe('nombre de pages', () => {
    it('tient en une page avec quatre livres', () => {
      expect(nombreDePages(4, 4)).toBe(1);
    });

    it('decoupe huit livres en deux pages de quatre', () => {
      expect(nombreDePages(8, 4)).toBe(2);
    });

    it('laisse la derniere page partielle avec dix livres', () => {
      // 4 + 4 + 2 : la troisieme page n'est pas pleine mais elle doit exister,
      // sinon les deux derniers livres seraient hors de la fenetre.
      expect(nombreDePages(10, 4)).toBe(3);
    });

    it('gere plus de dix livres', () => {
      expect(nombreDePages(12, 4)).toBe(3);
      expect(nombreDePages(14, 4)).toBe(4);
    });

    it('suit le nombre de cartes par ecran', () => {
      expect(nombreDePages(8, 3)).toBe(3);
      expect(nombreDePages(8, 2)).toBe(4);
      expect(nombreDePages(8, 1)).toBe(8);
    });

    it('ne perd aucun livre, quelle que soit la repartition', () => {
      for (const total of [1, 2, 3, 4, 5, 7, 8, 9, 10, 11, 12, 13, 14, 20]) {
        for (const parPage of [4, 3, 2, 1]) {
          const nbPages = nombreDePages(total, parPage);
          const couverts = (nbPages - 1) * parPage + Math.min(parPage, total - (nbPages - 1) * parPage);
          expect(couverts).toBe(total);
        }
      }
    });
  });

  /*
   * Deplacement reel de la piste.
   *
   * Ces tests rejouent le geste exact du signalement : charger la page, voir
   * quatre couvertures, cliquer sur la fleche, verifier que les couvertures
   * ont change, puis revenir en arriere. Ils controlent la valeur du `transform`
   * reellement applique par le binding, pas seulement l'etat interne.
   */
  describe('deplacement de la piste', () => {
    function cliquer(fixture: ComponentFixture<DetailComponent>, position: 'precedent' | 'suivant'): void {
      const boutons = (fixture.nativeElement as HTMLElement).querySelectorAll<HTMLButtonElement>('.carousel__bouton');
      const bouton = position === 'suivant' ? boutons[1] : boutons[0];
      expect(bouton.disabled).toBe(false);
      bouton.click();
      fixture.detectChanges();
    }

    function preparer(nombre: number): Promise<Faux> {
      return creerDetail(nombre).then((faux) => {
        simulerFenetre(faux.fixture, LARGEUR_FENETRE);
        const anyComponent = faux.composant as unknown as Record<string, () => void>;
        anyComponent['mesurer']();
        faux.fixture.detectChanges();
        return faux;
      });
    }

    it('affiche quatre livres entiers au chargement', async () => {
      const { fixture } = await preparer(8);
      expect(couverturesVisibles(fixture)).toEqual(['Livre 1', 'Livre 2', 'Livre 3', 'Livre 4']);
    });

    it('remplace les quatre couvertures apres un clic sur suivant', async () => {
      const { fixture } = await preparer(8);
      const avant = couverturesVisibles(fixture);

      cliquer(fixture, 'suivant');

      const apres = couverturesVisibles(fixture);
      expect(apres).toEqual(['Livre 5', 'Livre 6', 'Livre 7', 'Livre 8']);
      // Les couvertures doivent avoir change, pas seulement la position interne.
      expect(apres).not.toEqual(avant);
    });

    it('retrouve les couvertures precedentes apres un clic sur precedent', async () => {
      const { fixture } = await preparer(8);
      const premiere = couverturesVisibles(fixture);

      cliquer(fixture, 'suivant');
      cliquer(fixture, 'precedent');

      expect(couverturesVisibles(fixture)).toEqual(premiere);
    });

    it('applique un transform reel correspondant a la page', async () => {
      const { fixture } = await preparer(8);
      expect(transformApplique(fixture)).toBe('translateX(-0px)');

      cliquer(fixture, 'suivant');
      // Un cran vaut la largeur entiere de la fenetre, pas celle d'une carte.
      expect(transformApplique(fixture)).toBe(`translateX(-${LARGEUR_FENETRE}px)`);

      cliquer(fixture, 'precedent');
      expect(transformApplique(fixture)).toBe('translateX(-0px)');
    });

    it('n affiche jamais de carte coupee, sur aucune page', async () => {
      const { fixture } = await preparer(8);
      const piste = fixture.nativeElement.querySelector('.piste') as HTMLElement;
      const gauche = 0;
      const largeur = piste.clientWidth;

      for (let page = 0; page < 2; page += 1) {
        if (page > 0) {
          cliquer(fixture, 'suivant');
        }
        // Toute carte qui chevauche la fenetre doit y etre entierement
        // contenue : une carte partiellement visible est un bug de decalage.
        const cartes = [...piste.querySelectorAll<HTMLElement>('.mini-carte')];
        for (const carte of cartes) {
          const r = carte.getBoundingClientRect();
          const chevauche = r.right > gauche + 0.5 && r.left < gauche + largeur - 0.5;
          if (chevauche) {
            expect(r.left).toBeGreaterThanOrEqual(gauche - 0.5);
            expect(r.right).toBeLessThanOrEqual(gauche + largeur + 0.5);
          }
        }
        // Quatre cartes exactement a l'ecran.
        expect(couverturesVisibles(fixture)).toHaveLength(4);
      }
    });

    it('affiche la derniere page partielle avec dix livres', async () => {
      const { fixture } = await preparer(10);
      cliquer(fixture, 'suivant');
      expect(couverturesVisibles(fixture)).toEqual(['Livre 5', 'Livre 6', 'Livre 7', 'Livre 8']);
      cliquer(fixture, 'suivant');
      // La troisieme page ne contient que deux livres, mais ils sont entiers.
      expect(couverturesVisibles(fixture)).toEqual(['Livre 9', 'Livre 10']);
    });

    it('desactive la fleche suivante sur la derniere page', async () => {
      const { fixture } = await preparer(8);
      cliquer(fixture, 'suivant');
      const boutons = (fixture.nativeElement as HTMLElement).querySelectorAll<HTMLButtonElement>('.carousel__bouton');
      expect(boutons[1].disabled).toBe(true);
      expect(boutons[0].disabled).toBe(false);
    });
  });

  describe('navigation', () => {
    it('desactive la fleche precedente sur la premiere page', async () => {
      const { composant: component } = await creerDetail(8);
      expect(pages(component)).toEqual({ precedente: false, suivante: true });
    });

    it('desactive la fleche suivante quand tout tient sur une page', async () => {
      const { composant: component } = await creerDetail(4);
      expect(pages(component)).toEqual({ precedente: false, suivante: false });
    });

    it('avance d une page entiere de quatre cartes', async () => {
      const { composant: component } = await creerDetail(8);
      const anyComponent = component as unknown as Record<string, () => number>;
      anyComponent['pageSuivante']();

      // Une page vaut 4 cartes : le second cran commence au livre 5, donc
      // decalage = 1 * largeur de page. La derniere page est atteinte.
      expect(anyComponent['pageCourante']()).toBe(1);
      expect(pages(component)).toEqual({ precedente: true, suivante: false });
    });

    it('ne depasse jamais la derniere page', async () => {
      const { composant: component } = await creerDetail(8);
      const anyComponent = component as unknown as Record<string, () => void>;
      anyComponent['pageSuivante']();
      anyComponent['pageSuivante']();
      anyComponent['pageSuivante']();

      expect(anyComponent['pageCourante']()).toBe(1);
    });

    it('ne recule jamais avant la premiere page', async () => {
      const { composant: component } = await creerDetail(8);
      const anyComponent = component as unknown as Record<string, () => void>;
      anyComponent['pagePrecedente']();

      expect(anyComponent['pageCourante']()).toBe(0);
    });
  });

  /*
   * Navigation entre deux fiches.
   *
   * Angular reutilise l'instance du composant quand on passe d'un livre a un
   * autre : la route ne change pas, seul le parametre. Lire la reference dans
   * le constructeur faisait que le clic sur une recommandation ne rechargeait
   * rien, et la page continuait d'afficher le livre precedent.
   */
  describe('navigation entre deux livres', () => {
    function emettre(faux: Faux, reference: string): void {
      faux.params.next({ get: (cle: string) => (cle === 'reference' ? reference : null) });
      faux.fixture.detectChanges();
    }

    function titreAffiche(faux: Faux): string | null {
      const anyComponent = faux.composant as unknown as Record<string, () => { titre: string } | null>;
      return anyComponent['livre']()?.titre ?? null;
    }

    it('recharge la fiche quand le parametre de route change', async () => {
      const faux = await creerDetail(8);
      expect(titreAffiche(faux)).toBe('Livre de reference');

      emettre(faux, '/works/OL999W');

      expect(titreAffiche(faux)).toBe('Livre de reference');
      // La nouvelle reference a bien ete demandee au service : c'est ce qui
      // distingue un rechargement d'un simple reaffichage de l'ancien livre.
      expect(faux.livresDemandes).toContain('/works/OL999W');
    });

    it('revient a la premiere page apres un changement de livre', async () => {
      const faux = await creerDetail(8);
      const anyComponent = faux.composant as unknown as Record<string, () => void>;
      anyComponent['pageSuivante']();
      expect(anyComponent['pageCourante']()).toBe(1);

      emettre(faux, '/works/OL999W');

      expect(anyComponent['pageCourante']()).toBe(0);
    });

    it('affiche une erreur si la reference est absente', async () => {
      const faux = await creerDetail(8);
      const anyComponent = faux.composant as unknown as Record<string, () => string | null>;

      faux.params.next({ get: () => null });
      faux.fixture.detectChanges();

      expect(anyComponent['erreur']()).not.toBeNull();
    });
  });
});
