import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';

/**
 * Coquille de l'application : elle ne fait qu'hberger le routeur.
 *
 * Tout le contenu des ecrans a ete deplace dans HomeComponent et
 * DetailComponent,charges a la demande via loadComponent.
 */
@Component({
  selector: 'app-root',
  imports: [RouterOutlet],
  templateUrl: './app.html',
  styleUrl: './app.css',
})
export class App {}
