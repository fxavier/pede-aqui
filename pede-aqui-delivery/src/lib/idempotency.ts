import { useRef } from 'react'

/**
 * Idempotency key for checkout submissions: generated once per mount and
 * stable across re-renders, so retries of the same order reuse the same key.
 */
export function useIdempotencyKey(): string {
  const ref = useRef<string | null>(null)
  if (ref.current === null) ref.current = crypto.randomUUID()
  return ref.current
}
