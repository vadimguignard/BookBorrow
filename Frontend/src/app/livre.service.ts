import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { PageLivres } from './livre.model';

@Injectable({ providedIn: 'root' })
export class LivreService {
  private readonly http = inject(HttpClient);

  /**
   * URL relative : en local ng serve la relaie via proxy.conf.json.
   * Angular ne contacte jamais Open Library ni la base directement.
   */
  private readonly baseUrl = '/api/livres';

  /**
   * Récupère une page du catalogue.
   *
   * La recherche libre (q) porte sur le titre OU l'auteur ; elle est combinée
   * en AND avec les filtres auteur, titre et année. Le backend se charge de la
   * logique de combinaison.
   */
  page(filtres: {
    q?: string;
    auteur?: string;
    titre?: string;
    annee?: number | null;
    page?: number;
  }): Observable<PageLivres> {
    let params = new HttpParams();

    if (filtres.q) params = params.set('q', filtres.q);
    if (filtres.auteur) params = params.set('auteur', filtres.auteur);
    if (filtres.titre) params = params.set('titre', filtres.titre);
    if (filtres.annee) params = params.set('annee', filtres.annee);
    if (filtres.page) params = params.set('page', filtres.page);

    return this.http.get<PageLivres>(this.baseUrl, { params });
  }
}
