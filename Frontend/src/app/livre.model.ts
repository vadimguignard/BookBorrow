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
