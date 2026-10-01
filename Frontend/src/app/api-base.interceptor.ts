import { isPlatformServer } from '@angular/common';
import { HttpInterceptorFn } from '@angular/common/http';
import { PLATFORM_ID, inject } from '@angular/core';

/**
 * Cote serveur (SSR), une URL relative comme /api/livres serait resolue vers le
 * serveur du front lui-meme. On la redirige donc vers le backend (variable
 * d'environnement BACKEND_URL, ex. http://backend:8080 dans docker compose).
 *
 * Dans le navigateur, rien ne change : l'URL reste relative et le serveur du
 * front relaie /api vers le backend (voir src/server.ts).
 */
export const apiBaseInterceptor: HttpInterceptorFn = (req, next) => {
  if (!isPlatformServer(inject(PLATFORM_ID)) || !req.url.startsWith('/api')) {
    return next(req);
  }
  const backend = (globalThis as { process?: { env?: Record<string, string | undefined> } }).process?.env?.[
    'BACKEND_URL'
  ];
  return next(req.clone({ url: `${backend ?? 'http://localhost:8080'}${req.url}` }));
};
