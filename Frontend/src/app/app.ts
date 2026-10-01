import { Component, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { HeaderComponent } from './header/header.component';
import { ThemeService } from './theme.service';

/**
 * Coquille de l'application : la barre de navigation et le routeur.
 *
 * Tout le contenu des ecrans a ete deplace dans HomeComponent et
 * DetailComponent, charges a la demande via loadComponent.
 */
@Component({
  selector: 'app-root',
  imports: [HeaderComponent, RouterOutlet],
  templateUrl: './app.html',
  styleUrl: './app.css',
})
export class App {
  // Instancie le service : le theme memorise est applique des le demarrage, sur toutes les pages.
  private readonly theme = inject(ThemeService);
}
