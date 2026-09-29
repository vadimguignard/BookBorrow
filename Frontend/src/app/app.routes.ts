import { Routes } from '@angular/router';

/**
 * Le catalogue est la racine : il n'a pas de route dediee, ni « Accueil »
 * ni « Catalogue » ne mènent ailleurs.
 *
 * `loadComponent` est conserve partout : les ecrans sont charges a la
 * demande, ce qui evite de telecharger le detail d'un livre et la fiche
 * de profil a chaque visite au catalogue.
 *
 * La reference Open Library contient des « / » (ex. /works/OL82563W) : elle
 * est donc encodee dans l'URL par le lien, et decodee automatiquement par
 * le routeur. Sans cela, « /works/ » serait interprete comme un segment de
 * chemin et la navigation echouerait.
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
    path: 'profil',
    loadComponent: () => import('./account/account.component').then((m) => m.AccountComponent),
    data: { section: 'profil' },
  },
  {
    path: 'reservations',
    loadComponent: () => import('./account/account.component').then((m) => m.AccountComponent),
    data: { section: 'reservations' },
  },
  {
    path: '**',
    redirectTo: '',
  },
];
