/**
 * Livre affiche sur la page d'accueil.
 *
 * Les champs descriptifs (titre, auteur, annee, couverture) proviennent de
 * l'API Open Library. Le statut et la date de disponibilite proviennent
 * uniquement de la base de donnees de la bibliotheque.
 */
export interface Livre {
  reference: string;
  titre: string;
  auteur: string;
  anneePublication: number | null;
  coverUrl: string | null;
  statut: 'LIBRE' | 'RESERVE';
  dateDisponibilite: string | null;
  disponible: boolean;
}

/** Une page de 25 livres, avec les informations de pagination. */
export interface PageLivres {
  livres: Livre[];
  page: number;
  taillePage: number;
  totalElements: number;
  totalPages: number;
}

/**
 * Fiche complete d'un livre, sur sa page de detail.
 *
 * Comme pour l'accueil, la frontiere entre les deux sources est stricte :
 * - description, langue, pages, date, ISBN, genres : Open Library
 * - statut, date de disponibilite : notre base de donnees
 *
 * Les champs descriptifs valent null quand Open Library ne les fournit pas :
 * l'interface affiche alors « Non renseigne » plutot qu'une valeur inventee.
 */
export interface LivreDetail {
  reference: string;
  titre: string;
  auteur: string | null;
  anneePublication: number | null;
  coverUrl: string | null;

  /** Description en anglais, telle que fournie par Open Library. */
  description: string | null;
  langue: string | null;
  nombrePages: number | null;
  datePublication: string | null;
  isbn: string[];
  /** Genres (sujets Open Library) affiches sur la page. */
  genres: string[];
  /** Genres ayant servi aux recommandations, affiches pour l'expliquer. */
  genresRecommandation: string[];

  statut: 'LIBRE' | 'RESERVE';
  dateDisponibilite: string | null;
  disponible: boolean;
}
