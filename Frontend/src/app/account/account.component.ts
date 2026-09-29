import { Component, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { map } from 'rxjs';

/**
 * Sections personnelles : profil et reservations.
 *
 * Les deux ecrans partagent le meme composant, distingue par la donnee de
 * route `section`. C'est volontaire : ils doivent avoir exactement la meme
 * identite visuelle, et le duplicer garantirait qu'ils divergent des le
 * premier ajustement.
 *
 * Le contenu n'existe pas encore. L'ecran affiche un etat vide plutot qu'un
 * texte d'explication : meme grille, memes cartes et meme pave de titre que
 * le reste du site, pour que le squelette visuel soit deja en place quand
 * les donnees arriveront.
 */
@Component({
  selector: 'app-account',
  imports: [RouterLink],
  templateUrl: './account.component.html',
  styleUrl: './account.component.css',
})
export class AccountComponent {
  private readonly route = inject(ActivatedRoute);

  /** Contenu de l'ecran, choisi par la route qui a ouvert le composant. */
  protected readonly section = toSignal(
    this.route.data.pipe(map((data) => data['section'] === 'profil' ? 'profil' : 'reservations')),
    { initialValue: 'reservations' as 'profil' | 'reservations' },
  );

  protected readonly contenu = toSignal(
    this.route.data.pipe(
      map((data) =>
        data['section'] === 'profil'
          ? {
              surTitre: 'Espace personnel',
              titre: 'Votre profil',
              vide: 'Aucune information enregistrée pour le moment.',
            }
          : {
              surTitre: 'Espace personnel',
              titre: 'Mes réservations',
              vide: "Vous n'avez aucune réservation en cours.",
            },
      ),
    ),
    {
      initialValue: {
        surTitre: 'Espace personnel',
        titre: 'Mes réservations',
        vide: "Vous n'avez aucune réservation en cours.",
      },
    },
  );
}
