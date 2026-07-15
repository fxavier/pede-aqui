// Date helpers for the /reports screen — kept local to this feature area.
import type { ReportInterval } from "@/lib/api/types";

/** Formats a Date as `YYYY-MM-DD` for use in native `<input type="date">`. */
export function toInputDate(date: Date): string {
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, "0");
  const d = String(date.getDate()).padStart(2, "0");
  return `${y}-${m}-${d}`;
}

/** Parses a `YYYY-MM-DD` input value into a local Date (midnight). */
export function fromInputDate(value: string): Date {
  const [y, m, d] = value.split("-").map(Number);
  return new Date(y, (m ?? 1) - 1, d ?? 1);
}

/** ISO date-time string at the start of the given day (local time), as required by the `from` report param. */
export function startOfDayIso(date: Date): string {
  const d = new Date(date);
  d.setHours(0, 0, 0, 0);
  return d.toISOString();
}

/** ISO date-time string at the end of the given day (local time), as required by the `to` report param. */
export function endOfDayIso(date: Date): string {
  const d = new Date(date);
  d.setHours(23, 59, 59, 999);
  return d.toISOString();
}

/** Default date range for first load: last 30 days (inclusive), ending today. */
export function defaultDateRange(): { from: Date; to: Date } {
  const to = new Date();
  const from = new Date();
  from.setDate(from.getDate() - 29);
  return { from, to };
}

/** Formats a report bucket timestamp for chart/table labels, according to the selected interval. */
export function formatBucketLabel(bucket: string, interval: ReportInterval): string {
  const date = new Date(bucket);
  if (Number.isNaN(date.getTime())) return bucket;
  if (interval === "month") {
    return new Intl.DateTimeFormat("pt-PT", { month: "short", year: "numeric" }).format(date);
  }
  return new Intl.DateTimeFormat("pt-PT", { day: "2-digit", month: "2-digit" }).format(date);
}
