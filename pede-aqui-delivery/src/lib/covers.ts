/*
 * Visual fallbacks. The backend exposes no images for vendors or products,
 * so we derive a deterministic emoji + gradient from the entity name/category.
 * This keeps the ported (image-heavy) UI coherent without inventing image data.
 */

export interface Cover { emoji: string; from: string; to: string }

export function vendorCover(name: string): Cover {
  const n = (name ?? '').toLowerCase()
  if (n.includes('burger') || n.includes('fast'))       return { emoji: '🍔', from: '#FFF7E6', to: '#FDE68A' }
  if (n.includes('pizza'))                               return { emoji: '🍕', from: '#FFF5F5', to: '#FECACA' }
  if (n.includes('sushi') || n.includes('zen'))          return { emoji: '🍣', from: '#F0F9FF', to: '#BAE6FD' }
  if (n.includes('green') || n.includes('garden')
    || n.includes('saudável') || n.includes('bowl'))     return { emoji: '🥗', from: '#F0FDF4', to: '#BBF7D0' }
  if (n.includes('farmácia') || n.includes('saúde')
    || n.includes('pharma'))                             return { emoji: '💊', from: '#EFF6FF', to: '#BFDBFE' }
  if (n.includes('café') || n.includes('cafe')
    || n.includes('continental'))                        return { emoji: '☕', from: '#FFFBEB', to: '#FDE68A' }
  if (n.includes('pastel') || n.includes('doce')
    || n.includes('bakery') || n.includes('padaria'))    return { emoji: '🎂', from: '#FDF2F8', to: '#F9A8D4' }
  if (n.includes('super') || n.includes('mercado')
    || n.includes('market'))                             return { emoji: '🛒', from: '#F7FEE7', to: '#D9F99D' }
  if (n.includes('bar') || n.includes('bebida')
    || n.includes('drink') || n.includes('adega'))       return { emoji: '🍹', from: '#FAF5FF', to: '#E9D5FF' }
  return                                                  { emoji: '🍲', from: '#FFF7ED', to: '#FED7AA' }
}

const PRODUCT_EMOJI: Record<string, string> = {
  Entradas: '🥟',
  'Pratos Principais': '🍛',
  Acompanhamentos: '🍟',
  Bebidas: '🥤',
  Refrigerante: '🥤',
  Agua: '💧',
  'Água': '💧',
  Vinhos: '🍷',
  Cerveja: '🍺',
  'Fast Food': '🍔',
  Humburges: '🍔',
  Sobremesas: '🍮',
  Outros: '📦',
}

/** Keyed by category NAME (resolve the id first); unknown names fall back to a generic plate. */
export function productEmoji(categoryName?: string): string {
  return PRODUCT_EMOJI[categoryName ?? ''] ?? '🍽️'
}
