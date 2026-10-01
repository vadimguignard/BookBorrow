import { Component, computed, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Calendar } from './calendar/calendar';
import { firstValueFrom } from 'rxjs';
import { addDays, eachDay, formatFr, todayIso } from './date-utils';
import { LivreDetail } from '../livre.model';
import { LivreService } from '../livre.service';
import { ConflictError, ReservationService } from './reservation.service';

interface FormErrors {
  lastName?: string;
  firstName?: string;
  startDate?: string;
  endDate?: string;
  form?: string;
}

@Component({
  selector: 'app-reservation',
  imports: [Calendar, RouterLink],
  templateUrl: './reservation.html',
  styleUrl: './reservation.css',
})
export class Reservation {
  private readonly service = inject(ReservationService);
  private readonly livreService = inject(LivreService);
  /** Reference Open Library du livre, ex. "/works/OL82563W" (decodee par le routeur). */
  private readonly reference = inject(ActivatedRoute).snapshot.paramMap.get('reference') ?? '';
  readonly backLink = ['/livre', this.reference];

  readonly book = signal<LivreDetail | null>(null);
  readonly loading = signal(true);
  readonly loadError = signal<string | null>(null);
  readonly coverFailed = signal(false);

  readonly lastName = signal('');
  readonly firstName = signal('');
  readonly startDate = signal<string | null>(null);
  readonly endDate = signal<string | null>(null);

  readonly reservedDays = signal<ReadonlySet<string>>(new Set());
  readonly errors = signal<FormErrors>({});
  readonly submitting = signal(false);
  readonly confirming = signal(false);
  readonly formatFr = formatFr;
  readonly success = signal<string | null>(null);

  // La date de fin doit être après la date de début
  readonly endMin = computed(() => addDays(this.startDate() ?? todayIso(), 1));

  constructor() {
    this.load();
  }

  private async load(): Promise<void> {
    try {
      // Les deux appels partent ensemble : la fiche peut mettre plusieurs secondes (Open Library).
      const [book, reserved] = await Promise.all([
        firstValueFrom(this.livreService.detail(this.reference)),
        this.service.getReservedDates(this.reference),
      ]);
      this.book.set(book);
      this.reservedDays.set(reserved);
    } catch (err) {
      this.loadError.set(
        err instanceof HttpErrorResponse && err.status === 404
          ? "Ce livre n'existe pas."
          : 'Impossible de charger la page. Réessayez dans un instant.',
      );
    } finally {
      this.loading.set(false);
    }
  }

  setLastName(value: string): void {
    this.lastName.set(value);
    this.clearError('lastName');
  }

  setFirstName(value: string): void {
    this.firstName.set(value);
    this.clearError('firstName');
  }

  pickStart(iso: string): void {
    this.startDate.set(iso);
    // Si la date de fin n'est plus après le début, on l'efface pour que le client la rechoisisse
    const end = this.endDate();
    if (end && end <= iso) this.endDate.set(null);
    this.clearError('startDate');
    this.clearError('form');
  }

  pickEnd(iso: string): void {
    this.endDate.set(iso);
    this.clearError('endDate');
    this.clearError('form');
  }

  /** Clic sur "Réserver" : on vérifie le formulaire, puis on affiche le récapitulatif. */
  review(): void {
    this.success.set(null);
    const errors = this.validate();
    this.errors.set(errors);
    if (Object.keys(errors).length === 0) this.confirming.set(true);
  }

  /** Clic sur "Modifier" : retour au formulaire (rien n'est perdu). */
  edit(): void {
    this.confirming.set(false);
  }

  /** Clic sur "Confirmer la réservation" : on enregistre vraiment. */
  async confirm(): Promise<void> {
    const book = this.book()!;
    const startDate = this.startDate()!;
    const endDate = this.endDate()!;
    this.submitting.set(true);
    try {
      await this.service.createReservation({
        reference: this.reference,
        prenom: this.firstName().trim(),
        nom: this.lastName().trim(),
        dateDebut: startDate,
        dateFin: endDate,
      });
      // Le livre est maintenant réservé sur cette période : les jours deviennent indisponibles
      this.reservedDays.set(new Set([...this.reservedDays(), ...eachDay(startDate, endDate)]));
      this.success.set(`Réservation enregistrée : « ${book.titre} » est à vous du ${formatFr(startDate)} au ${formatFr(endDate)}.`);
      this.lastName.set('');
      this.firstName.set('');
      this.startDate.set(null);
      this.endDate.set(null);
    } catch (err) {
      if (err instanceof ConflictError) {
        this.errors.set({ form: "Cette période vient d'être réservée par quelqu'un d'autre. Choisissez d'autres dates." });
        this.startDate.set(null);
        this.endDate.set(null);
        this.reservedDays.set(await this.service.getReservedDates(this.reference));
      } else {
        this.errors.set({ form: "La réservation n'a pas pu être enregistrée. Réessayez dans un instant." });
      }
    } finally {
      this.submitting.set(false);
      this.confirming.set(false);
    }
  }

  private validate(): FormErrors {
    const e: FormErrors = {};
    const start = this.startDate();
    const end = this.endDate();
    const reserved = this.reservedDays();

    if (!this.lastName().trim()) e.lastName = 'Le nom est obligatoire.';
    if (!this.firstName().trim()) e.firstName = 'Le prénom est obligatoire.';

    if (!start) e.startDate = 'La date de début est obligatoire.';
    else if (start < todayIso()) e.startDate = 'La date de début ne peut pas être dans le passé.';
    else if (reserved.has(start)) e.startDate = "Cette date de début n'est pas disponible.";

    if (!end) e.endDate = 'La date de fin est obligatoire.';
    else if (reserved.has(end)) e.endDate = "Cette date de fin n'est pas disponible.";

    if (start && end && !e.startDate && !e.endDate) {
      if (end <= start) e.endDate = 'La date de fin doit être après la date de début.';
      else if (eachDay(start, end).some((d) => reserved.has(d))) {
        e.form = 'Des jours de cette période sont déjà réservés. Choisissez une autre période.';
      }
    }
    return e;
  }

  private clearError(field: keyof FormErrors): void {
    if (this.errors()[field]) this.errors.update((e) => ({ ...e, [field]: undefined }));
  }
}
