import { describe, it, expect } from 'vitest'
import { renderHook } from '@testing-library/react'
import { useIdempotencyKey } from './idempotency'

describe('useIdempotencyKey', () => {
  it('returns a UUID-shaped key', () => {
    const { result } = renderHook(() => useIdempotencyKey())
    expect(result.current).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i)
  })

  it('stays stable across re-renders within a mount', () => {
    const { result, rerender } = renderHook(() => useIdempotencyKey())
    const first = result.current
    rerender()
    rerender()
    expect(result.current).toBe(first)
  })

  it('generates a fresh key for a new mount', () => {
    const a = renderHook(() => useIdempotencyKey())
    const b = renderHook(() => useIdempotencyKey())
    expect(a.result.current).not.toBe(b.result.current)
  })
})
