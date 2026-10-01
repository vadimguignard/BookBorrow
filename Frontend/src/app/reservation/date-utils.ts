// Petites fonctions pour manipuler les dates au format "AAAA-MM-JJ" (ex : "2026-10-05").
// On utilise ce format texte partout pour éviter les problèmes de fuseau horaire.

export function toIso(date: Date): string {
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, '0');
  const d = String(date.getDate()).padStart(2, '0');
  return `${y}-${m}-${d}`;
}

export function parseIso(iso: string): Date {
  const [y, m, d] = iso.split('-').map(Number);
  return new Date(y, m - 1, d);
}

export function todayIso(): string {
  return toIso(new Date());
}

export function addDays(iso: string, days: number): string {
  const date = parseIso(iso);
  date.setDate(date.getDate() + days);
  return toIso(date);
}

/** Tous les jours de start à end, inclus. */
export function eachDay(start: string, end: string): string[] {
  const days: string[] = [];
  for (let d = start; d <= end; d = addDays(d, 1)) {
    days.push(d);
  }
  return days;
}

/** Ex : "5 octobre 2026" */
export function formatFr(iso: string): string {
  return parseIso(iso).toLocaleDateString('fr-FR', {
    day: 'numeric',
    month: 'long',
    year: 'numeric',
  });
}
