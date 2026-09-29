import { Routes } from '@angular/router';

/**
 * Deux ecrans : le catalogue et la fiche d'un livre.
 *
 * La reference Open Library contient des « / » (ex. /works/OL82563W) : elle est
 * donc encodee dans l'URL par le lien, et decodee automatiquement par le
 * routeur. Sans cela, « /works/ » serait interprete comme un segment de chemin
 * et la navigation echouerait.
 */
export const routes: Routes = [
  {
    path: '',
    pathMatch: 'full',
    loadComponent: () => import('./home/home.component').then((m) => m.HomeComponent),
  },
  {
    path: 'livre/:reference',
    loadComponent: () => import('./detail/detail.component').then((m) => m.DetailComponent),
  },
  {
    path: '**',
    redirectTo: '',
  },
];
