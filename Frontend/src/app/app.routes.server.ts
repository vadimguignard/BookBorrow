import { RenderMode, ServerRoute } from '@angular/ssr';

/**
 * Toutes les pages rendent cote serveur (RenderMode.Server), pas en prerendu.
 *
 * Un prerendu genere le HTML au build, donc sans que le navigateur soit
 * charge : la page d'accueil appelle /api/livres au demarrage, et cet appel
 * Open Library peut mettre 5 a 15 secondes. Le build echouait sur un timeout.
 *
 * Le rendu serveur garde l'avantage du prerendu : le titre, l'auteur et la
 * description du livre sont presents dans le code HTML livre au navigateur.
 */
export const serverRoutes: ServerRoute[] = [
  // Reservation : calendrier calcule a partir de la date du jour -> rendu dans le navigateur,
  // pour qu'un decalage d'horloge/fuseau entre serveur et client ne casse pas l'hydratation.
  {
    path: 'reservation/:reference',
    renderMode: RenderMode.Client,
  },
  {
    path: '**',
    renderMode: RenderMode.Server,
  },
];
