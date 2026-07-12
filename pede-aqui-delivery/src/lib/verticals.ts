import type { Category } from '@/lib/api/types'

/* from/to: light tile tints, same pastel language as covers.ts */
export interface VerticalMeta { label: string; emoji: string; from: string; to: string }

export const VERTICAL_META: Record<string, VerticalMeta> = {
  all:         { label: 'Todos',         emoji: '🛍️', from: '#FFF1F2', to: '#FECDD3' },
  restaurant:  { label: 'Restaurantes',  emoji: '🍲', from: '#FFF7ED', to: '#FED7AA' },
  supermarket: { label: 'Supermercados', emoji: '🛒', from: '#F7FEE7', to: '#D9F99D' },
  pharmacy:    { label: 'Farmácias',     emoji: '💊', from: '#EFF6FF', to: '#BFDBFE' },
  fastfood:    { label: 'Fast Food',     emoji: '🍔', from: '#FEFCE8', to: '#FDE68A' },
  cafe:        { label: 'Cafés',         emoji: '☕', from: '#FAF5EF', to: '#E7D8C3' },
  healthy:     { label: 'Saudável',      emoji: '🥗', from: '#F0FDF4', to: '#BBF7D0' },
  bakery:      { label: 'Pastelaria',    emoji: '🎂', from: '#FDF2F8', to: '#F9A8D4' },
}

export function groupByVertical(categories: Category[]): string[] {
  const seen = new Set<string>()
  for (const c of categories) {
    if (c.vertical) seen.add(c.vertical)
  }
  return [...seen]
}

export function verticalMeta(id: string): VerticalMeta {
  return VERTICAL_META[id] ?? { label: id, emoji: '🛍️', from: '#FFF1F2', to: '#FECDD3' }
}
