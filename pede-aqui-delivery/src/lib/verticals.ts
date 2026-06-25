import type { Category } from '@/lib/api/types'

export interface VerticalMeta { label: string; emoji: string }

export const VERTICAL_META: Record<string, VerticalMeta> = {
  all:         { label: 'Todos',         emoji: '🛍️' },
  restaurant:  { label: 'Restaurantes',  emoji: '🍲' },
  supermarket: { label: 'Supermercados', emoji: '🛒' },
  pharmacy:    { label: 'Farmácias',     emoji: '💊' },
  fastfood:    { label: 'Fast Food',     emoji: '🍔' },
  cafe:        { label: 'Cafés',         emoji: '☕' },
  healthy:     { label: 'Saudável',      emoji: '🥗' },
  bakery:      { label: 'Pastelaria',    emoji: '🎂' },
}

export function groupByVertical(categories: Category[]): string[] {
  const seen = new Set<string>()
  for (const c of categories) {
    if (c.vertical) seen.add(c.vertical)
  }
  return [...seen]
}

export function verticalMeta(id: string): VerticalMeta {
  return VERTICAL_META[id] ?? { label: id, emoji: '🛍️' }
}
