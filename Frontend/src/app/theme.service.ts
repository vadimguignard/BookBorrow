import { DOCUMENT, isPlatformBrowser } from '@angular/common';
import { Injectable, PLATFORM_ID, inject, signal } from '@angular/core';

export type Theme = 'clair' | 'sombre';

const CLE_THEME = 'bookborrow.theme';

/**
 * Theme clair / sombre de l'application.
 *
 * Le choix est conserve dans le localStorage et applique par l'attribut
 * `data-theme` de <html> (les jetons CSS de styles.css font le reste).
 * Un petit script de index.html l'applique avant le premier affichage.
 */
@Injectable({ providedIn: 'root' })
export class ThemeService {
  private readonly document = inject(DOCUMENT);
  private readonly navigateur = isPlatformBrowser(inject(PLATFORM_ID));

  readonly theme = signal<Theme>(this.lire());

  constructor() {
    this.appliquer(this.theme());
  }

  /** Bascule entre mode clair et mode sombre. */
  basculer(): void {
    this.choisir(this.theme() === 'sombre' ? 'clair' : 'sombre');
  }

  choisir(theme: Theme): void {
    this.theme.set(theme);
    this.appliquer(theme);
    if (!this.navigateur) return;
    try {
      globalThis.localStorage.setItem(CLE_THEME, theme);
    } catch {
      // Stockage indisponible : le theme durera le temps de l'onglet seulement.
    }
  }

  private appliquer(theme: Theme): void {
    const racine = this.document.documentElement;
    if (theme === 'sombre') racine.setAttribute('data-theme', 'sombre');
    else racine.removeAttribute('data-theme');
  }

  private lire(): Theme {
    if (!this.navigateur) return 'clair';
    try {
      return globalThis.localStorage.getItem(CLE_THEME) === 'sombre' ? 'sombre' : 'clair';
    } catch {
      return 'clair';
    }
  }
}
