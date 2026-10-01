import { Component, computed, effect, input, output, signal } from '@angular/core';
import { parseIso, toIso, todayIso } from '../date-utils';

interface Cell {
  key: string;
  day: number;
  blank: boolean;
  reserved: boolean;
  disabled: boolean;
  label: string;
}

@Component({
  selector: 'app-calendar',
  templateUrl: './calendar.html',
  styleUrl: './calendar.css',
})
export class Calendar {
  /** Jour choisi ("AAAA-MM-JJ") ou null */
  readonly selected = input<string | null>(null);
  /** Jours déjà réservés : affichés barrés et non cliquables */
  readonly reservedDays = input<ReadonlySet<string>>(new Set());
  /** Premier jour qu'on peut choisir : les jours avant sont grisés */
  readonly minDate = input<string>(todayIso());
  /** Bordure rouge quand il y a une erreur */
  readonly invalid = input(false);
  /** Envoie le jour choisi au composant parent */
  readonly picked = output<string>();

  readonly weekdays = ['L', 'M', 'M', 'J', 'V', 'S', 'D'];
  private readonly viewMonth = signal(this.firstOfMonth(parseIso(this.minDate())));

  constructor() {
    // Quand le premier jour possible change (ex : on choisit la date de début), le calendrier saute à ce mois
    effect(() => this.viewMonth.set(this.firstOfMonth(parseIso(this.minDate()))));
  }

  readonly title = computed(() => {
    const text = this.viewMonth().toLocaleDateString('fr-FR', { month: 'long', year: 'numeric' });
    return text.charAt(0).toUpperCase() + text.slice(1);
  });

  readonly canGoPrev = computed(
    () => this.viewMonth() > this.firstOfMonth(parseIso(this.minDate())),
  );

  readonly cells = computed<Cell[]>(() => {
    const first = this.viewMonth();
    const year = first.getFullYear();
    const month = first.getMonth();
    const daysInMonth = new Date(year, month + 1, 0).getDate();
    const offset = (first.getDay() + 6) % 7; // la semaine commence le lundi
    const cells: Cell[] = [];

    for (let i = 0; i < offset; i++) {
      cells.push({ key: `blank-${i}`, day: 0, blank: true, reserved: false, disabled: true, label: '' });
    }
    for (let day = 1; day <= daysInMonth; day++) {
      const date = new Date(year, month, day);
      const iso = toIso(date);
      const reserved = this.reservedDays().has(iso);
      const label = date.toLocaleDateString('fr-FR', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' });
      cells.push({
        key: iso,
        day,
        blank: false,
        reserved,
        disabled: reserved || iso < this.minDate(),
        label: reserved ? `${label}, déjà réservé` : label,
      });
    }
    return cells;
  });

  shift(months: number): void {
    const current = this.viewMonth();
    this.viewMonth.set(new Date(current.getFullYear(), current.getMonth() + months, 1));
  }

  pick(cell: Cell): void {
    if (!cell.disabled) this.picked.emit(cell.key);
  }

  private firstOfMonth(date: Date): Date {
    return new Date(date.getFullYear(), date.getMonth(), 1);
  }
}
