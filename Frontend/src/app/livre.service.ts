import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { Livre, LivreDetail, PageLivres } from './livre.model';

@Injectable({ providedIn: 'root' })
export class LivreService {
  private readonly http = inject(HttpClient);

  /**
   * URL relative : en local ng serve la relaie via proxy.conf.json.
   * Angular ne contacte jamais Open Library ni la base directement.
   */
  private readonly baseUrl = '/api/livres';

  /**
   * Recupere une page du catalogue.
   *
   * La recherche libre (q) porte sur le titre OU l'auteur ; elle est combinee
   * en AND avec les filtres auteur, titre et annee. Le backend se charge de la
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

  /**
   * Fiche complete d'un livre.
   *
   * La reference est envoyee en parametre de requete et non dans le chemin de
   * l'URL : elle contient des « / » (ex. /works/OL82563W) et Tomcat refuse un
   * caractere %2F dans un segment, en renvoyant un 400.
   */
  detail(reference: string): Observable<LivreDetail> {
    return this.http.get<LivreDetail>(`${this.baseUrl}/detail`, {
      params: new HttpParams().set('reference', reference),
    });
  }

  /**
   * Livres recommandes par genre pour le livre consulte.
   *
   * @param limite nombre maximum de recommandations, 8 par defaut
   */
  recommandations(reference: string, limite = 8): Observable<Livre[]> {
    return this.http.get<Livre[]>(`${this.baseUrl}/recommandations`, {
      params: new HttpParams().set('reference', reference).set('limite', limite),
    });
  }
}
