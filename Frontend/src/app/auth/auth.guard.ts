import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';

/** Page reservee aux utilisateurs connectes ; sinon, connexion puis retour a cette page. */
export const connecteGuard: CanActivateFn = async (_route, state) => {
  const auth = inject(AuthService);
  await auth.restaurer();
  return auth.connecte()
    ? true
    : inject(Router).createUrlTree(['/connexion'], { queryParams: { retour: state.url } });
};

/** Pages de connexion / inscription : inutiles quand on est deja connecte. */
export const visiteurGuard: CanActivateFn = async () => {
  const auth = inject(AuthService);
  await auth.restaurer();
  return auth.connecte() ? inject(Router).createUrlTree(['/profil']) : true;
};
