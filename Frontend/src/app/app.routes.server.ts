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
  {
    path: '**',
    renderMode: RenderMode.Server,
  },
];
