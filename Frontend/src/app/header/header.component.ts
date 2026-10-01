import { Component, afterNextRender, inject } from '@angular/core';
import { Router, RouterLink, RouterLinkActive } from '@angular/router';
import { AuthService } from '../auth/auth.service';

/**
 * Barre de navigation commune a toutes les pages.
 *
 * La zone de compte reste vide tant que la session n'est pas restauree (`pret`) :
 * le serveur ne connait pas le jeton du navigateur, afficher « Connexion » puis le
 * remplacer par le nom de l'utilisateur ferait clignoter le menu et casserait
 * l'hydratation.
 */
@Component({
  selector: 'app-header',
  imports: [RouterLink, RouterLinkActive],
  templateUrl: './header.component.html',
  styleUrl: './header.component.css',
})
export class HeaderComponent {
  protected readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  constructor() {
    // Uniquement dans le navigateur, une fois la page affichee.
    afterNextRender(() => void this.auth.restaurer());
  }

  protected deconnecter(): void {
    this.auth.deconnexion();
    void this.router.navigateByUrl('/');
  }
}
