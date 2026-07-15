// Spec 002 (US-7) — parses the backend's ProblemDetail error shape
// ({ title, status, detail, errors }) thrown by httpClient on non-2xx responses.
// httpClient throws the raw parsed JSON body (not an Error), so callers need a
// type guard rather than relying on `instanceof Error`.
interface ProblemDetail {
  title?: string;
  status?: number;
  detail?: string;
  errors?: Record<string, string>;
}

function isProblemDetail(value: unknown): value is ProblemDetail {
  return typeof value === "object" && value !== null && ("title" in value || "detail" in value || "errors" in value);
}

/** Extracts a human-readable message plus optional field-level errors from a caught API error. */
export function parseApiError(err: unknown, fallback: string): { message: string; fields?: Record<string, string> } {
  if (isProblemDetail(err)) {
    const fieldMessages = err.errors ? Object.values(err.errors).join(" ") : undefined;
    const message = err.detail || err.title || fieldMessages || fallback;
    return { message, fields: err.errors };
  }
  if (err instanceof Error && err.message) {
    return { message: err.message };
  }
  return { message: fallback };
}
