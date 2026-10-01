import { Routes } from '@angular/router';
import { connecteGuard, visiteurGuard } from './auth/auth.guard';

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
    // Reservation d'un livre. Meme principe que la fiche : la reference Open Library
    // (/works/OL82563W) est un seul segment, encode par le lien et decode par le routeur.
    // Il faut un compte pour reserver : sans connexion, on passe par /connexion puis on revient ici.
    path: 'reservation/:reference',
    canActivate: [connecteGuard],
    loadComponent: () => import('./reservation/reservation').then((m) => m.Reservation),
  },
  {
    path: 'connexion',
    canActivate: [visiteurGuard],
    loadComponent: () => import('./auth/auth.component').then((m) => m.AuthComponent),
    data: { mode: 'connexion' },
  },
  {
    path: 'inscription',
    canActivate: [visiteurGuard],
    loadComponent: () => import('./auth/auth.component').then((m) => m.AuthComponent),
    data: { mode: 'inscription' },
  },
  {
    // Profil + livres reserves, reserves aux utilisateurs connectes.
    path: 'profil',
    canActivate: [connecteGuard],
    loadComponent: () => import('./account/account.component').then((m) => m.AccountComponent),
  },
  {
    // Ancienne adresse : les reservations sont desormais dans le profil.
    path: 'reservations',
    redirectTo: 'profil',
  },
  {
    path: '**',
    redirectTo: '',
  },
];
