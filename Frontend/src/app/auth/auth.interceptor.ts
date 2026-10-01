import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { AuthService } from './auth.service';

/** Les appels de connexion / inscription ne portent jamais de jeton. */
const ROUTES_PUBLIQUES = ['/api/auth/connexion', '/api/auth/inscription'];

/**
 * - Ajoute « Authorization: Bearer <jeton> » aux appels vers /api.
 * - Si le serveur repond 401 alors qu'un jeton a ete envoye, la session est
 *   expiree : on l'oublie et on renvoie vers la page de connexion, avec la page
 *   courante en memoire pour y revenir apres la connexion.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const router = inject(Router);

  const jeton = auth.jeton;
  const envoyer = !!jeton && req.url.startsWith('/api') && !ROUTES_PUBLIQUES.includes(req.url);
  const requete = envoyer ? req.clone({ setHeaders: { Authorization: `Bearer ${jeton}` } }) : req;

  return next(requete).pipe(
    catchError((err: unknown) => {
      if (envoyer && err instanceof HttpErrorResponse && err.status === 401) {
        auth.sessionExpiree();
        // La restauration au demarrage (/moi) gere son 401 en silence : pas de redirection
        // depuis une page publique juste parce qu'un vieux jeton traine.
        if (req.url !== '/api/auth/moi') {
          void router.navigate(['/connexion'], { queryParams: { retour: router.url } });
        }
      }
      return throwError(() => err);
    }),
  );
};
