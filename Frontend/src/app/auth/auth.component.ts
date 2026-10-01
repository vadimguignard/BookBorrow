import { Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { map } from 'rxjs';
import { AuthService, messageErreur } from './auth.service';

type Mode = 'connexion' | 'inscription';

interface Erreurs {
  username?: string;
  motDePasse?: string;
  confirmation?: string;
  formulaire?: string;
}

/** Niveau de robustesse du mot de passe saisi (inscription). */
interface Force {
  /** 0 a 4 : nombre de segments allumes. */
  niveau: number;
  libelle: string;
  classe: 'faible' | 'moyen' | 'fort';
}

const USERNAME = /^[A-Za-z0-9_.-]{3,30}$/;
const MDP_MIN = 8;
const MDP_MAX = 64;

/**
 * Evalue la robustesse d'un mot de passe : longueur, majuscule, chiffre, caractere special.
 * Les trois premiers criteres sont ceux exiges par le serveur ; le dernier renforce le score.
 */
export function evaluerForce(mdp: string): Force {
  let niveau = 0;
  if (mdp.length >= MDP_MIN) niveau++;
  if (/[A-Z]/.test(mdp)) niveau++;
  if (/\d/.test(mdp)) niveau++;
  if (/[^A-Za-z0-9]/.test(mdp) || mdp.length >= 12) niveau++;
  if (niveau <= 2) return { niveau: Math.max(niveau, 1), libelle: 'Faible', classe: 'faible' };
  if (niveau === 3) return { niveau, libelle: 'Moyen', classe: 'moyen' };
  return { niveau, libelle: 'Fort', classe: 'fort' };
}

/**
 * Connexion et inscription.
 *
 * Les deux ecrans partagent le meme composant, distingue par la donnee de route
 * `mode`. Apres succes, retour a la page demandee (`?retour=`) ou au catalogue.
 * Les champs sont verifies cote client AVANT tout appel : un champ vide affiche
 * un message sous ce champ et aucune requete n'est envoyee.
 */
@Component({
  selector: 'app-auth',
  imports: [FormsModule, RouterLink],
  templateUrl: './auth.component.html',
  styleUrl: './auth.component.css',
})
export class AuthComponent {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  protected readonly mode = toSignal(
    this.route.data.pipe(map((d): Mode => (d['mode'] === 'inscription' ? 'inscription' : 'connexion'))),
    { initialValue: 'connexion' as Mode },
  );
  protected readonly inscription = computed(() => this.mode() === 'inscription');

  protected readonly username = signal('');
  protected readonly motDePasse = signal('');
  protected readonly confirmation = signal('');
  protected readonly erreurs = signal<Erreurs>({});
  protected readonly envoi = signal(false);
  protected readonly motDePasseVisible = signal(false);

  /** Indicateur de force, affiche pendant la saisie. */
  protected readonly force = computed(() => (this.motDePasse() ? evaluerForce(this.motDePasse()) : null));

  /** Message d'ecart en direct : visible des que la confirmation est commencee. */
  protected readonly ecartConfirmation = computed(
    () =>
      this.inscription() &&
      this.confirmation().length > 0 &&
      this.motDePasse().length > 0 &&
      this.confirmation() !== this.motDePasse(),
  );

  /** Page a rouvrir apres la connexion. Seuls les chemins internes sont acceptes. */
  private readonly retour = (() => {
    const valeur = this.route.snapshot.queryParamMap.get('retour');
    return valeur && valeur.startsWith('/') && !valeur.startsWith('//') ? valeur : '/';
  })();

  /** Les liens entre connexion et inscription conservent la page de retour. */
  protected readonly paramsRetour = this.retour === '/' ? {} : { retour: this.retour };

  protected async soumettre(): Promise<void> {
    const erreurs = this.valider();
    this.erreurs.set(erreurs);
    if (Object.keys(erreurs).length > 0) return; // aucune requete envoyee

    this.envoi.set(true);
    try {
      if (this.inscription()) {
        await this.auth.inscription({
          username: this.username().trim(),
          motDePasse: this.motDePasse(),
          confirmationMotDePasse: this.confirmation(),
        });
      } else {
        await this.auth.connexion(this.username().trim(), this.motDePasse());
      }
      await this.router.navigateByUrl(this.retour);
    } catch (err) {
      this.erreurs.set({
        formulaire: messageErreur(
          err,
          this.inscription() ? "L'inscription a échoué. Réessayez." : 'La connexion a échoué. Réessayez.',
        ),
      });
    } finally {
      this.envoi.set(false);
    }
  }

  protected basculerVisibilite(): void {
    this.motDePasseVisible.update((v) => !v);
  }

  protected effacer(champ: keyof Erreurs): void {
    if (this.erreurs()[champ] || this.erreurs().formulaire) {
      this.erreurs.update((e) => ({ ...e, [champ]: undefined, formulaire: undefined }));
    }
  }

  private valider(): Erreurs {
    const e: Erreurs = {};
    const username = this.username().trim();
    const mdp = this.motDePasse();

    if (!username) e.username = "Le nom d'utilisateur est obligatoire.";
    else if (this.inscription() && !USERNAME.test(username)) {
      e.username = '3 à 30 caractères : lettres, chiffres, point, tiret ou underscore.';
    }

    if (!mdp) e.motDePasse = 'Le mot de passe est obligatoire.';
    else if (this.inscription()) {
      if (mdp.length < MDP_MIN || mdp.length > MDP_MAX) {
        e.motDePasse = `Le mot de passe doit contenir entre ${MDP_MIN} et ${MDP_MAX} caractères.`;
      } else if (!/[A-Z]/.test(mdp)) e.motDePasse = 'Le mot de passe doit contenir au moins une majuscule.';
      else if (!/\d/.test(mdp)) e.motDePasse = 'Le mot de passe doit contenir au moins un chiffre.';
    }

    if (this.inscription()) {
      if (!this.confirmation()) e.confirmation = 'La confirmation du mot de passe est obligatoire.';
      else if (this.confirmation() !== mdp) e.confirmation = 'Les mots de passe ne correspondent pas.';
    }
    return e;
  }
}
