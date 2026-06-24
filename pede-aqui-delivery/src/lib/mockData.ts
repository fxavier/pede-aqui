import type { Product, VendorSearchResult } from '@/lib/api/types'

export const VERTICALS = [
  { id: 'all',         label: 'Todos',         emoji: '🛍️' },
  { id: 'restaurant',  label: 'Restaurantes',  emoji: '🍲' },
  { id: 'supermarket', label: 'Supermercados', emoji: '🛒' },
  { id: 'pharmacy',    label: 'Farmácias',     emoji: '💊' },
  { id: 'fastfood',    label: 'Fast Food',     emoji: '🍔' },
  { id: 'cafe',        label: 'Cafés',         emoji: '☕' },
  { id: 'healthy',     label: 'Saudável',      emoji: '🥗' },
  { id: 'bakery',      label: 'Pastelaria',    emoji: '🎂' },
]

export function mockVendorVertical(name: string): string {
  const n = name.toLowerCase()
  if (n.includes('burger') || n.includes('fast'))      return 'fastfood'
  if (n.includes('sushi') || n.includes('zen'))        return 'restaurant'
  if (n.includes('green') || n.includes('garden') || n.includes('bowl')) return 'healthy'
  if (n.includes('farmácia') || n.includes('saúde'))   return 'pharmacy'
  if (n.includes('café') || n.includes('continental')) return 'cafe'
  if (n.includes('pastelaria') || n.includes('doce'))  return 'bakery'
  if (n.includes('super') || n.includes('mercado'))    return 'supermarket'
  return 'restaurant'
}

export const MOCK_VENDORS: VendorSearchResult[] = [
  { vendorId: 'demo-1', name: 'Avenida Gourmet',       distanceKm: 2.1, distanceMeters: 2100, available: true,  rating: 4.8, estimatedDeliveryMinutes: 32, deliveryFee: 150 },
  { vendorId: 'demo-2', name: 'Burger House Maputo',    distanceKm: 1.4, distanceMeters: 1400, available: true,  rating: 4.5, estimatedDeliveryMinutes: 22, deliveryFee: 80  },
  { vendorId: 'demo-3', name: 'Sushi Zen Master',       distanceKm: 3.5, distanceMeters: 3500, available: false, rating: 4.7, estimatedDeliveryMinutes: 45, deliveryFee: 200 },
  { vendorId: 'demo-4', name: 'Green Garden Bowl',      distanceKm: 0.8, distanceMeters: 800,  available: true,  rating: 4.9, estimatedDeliveryMinutes: 18, deliveryFee: 50  },
  { vendorId: 'demo-5', name: 'Polana Pizzas',          distanceKm: 1.9, distanceMeters: 1900, available: true,  rating: 4.3, estimatedDeliveryMinutes: 30, deliveryFee: 120 },
  { vendorId: 'demo-6', name: 'Farmácia Saúde Total',   distanceKm: 0.5, distanceMeters: 500,  available: true,  rating: 4.6, estimatedDeliveryMinutes: 15, deliveryFee: 0   },
  { vendorId: 'demo-7', name: 'Café Continental',       distanceKm: 1.1, distanceMeters: 1100, available: true,  rating: 4.4, estimatedDeliveryMinutes: 20, deliveryFee: 60  },
  { vendorId: 'demo-8', name: 'Pastelaria Doce Pátria', distanceKm: 2.8, distanceMeters: 2800, available: true,  rating: 4.7, estimatedDeliveryMinutes: 28, deliveryFee: 100 },
]

export const MOCK_PRODUCTS: Product[] = [
  { id: 'm1', vendorId: '', categoryId: 'Entradas',          name: 'Chamuças de Carne',  description: 'Conjunto de 4 unidades crocantes com molho especial da casa.',  skus: [{ id: 's1', skuCode: 'C01', name: 'Standard', price: 120, active: true }] },
  { id: 'm2', vendorId: '', categoryId: 'Entradas',          name: 'Asas Peri-Peri',     description: '6 peças marinadas no molho peri-peri, com acompanhamento.',      skus: [{ id: 's2', skuCode: 'A01', name: 'Standard', price: 450, active: true }] },
  { id: 'm3', vendorId: '', categoryId: 'Pratos Principais', name: 'Muamba de Galinha',  description: 'Arroz branco, molho tradicional de muamba e legumes frescos.',    skus: [{ id: 's3', skuCode: 'M01', name: 'Standard', price: 650, active: true }] },
  { id: 'm4', vendorId: '', categoryId: 'Pratos Principais', name: 'Caril de Camarão',   description: 'Camarões frescos em molho de caril cremoso, com arroz basanti.', skus: [{ id: 's4', skuCode: 'C02', name: 'Standard', price: 850, active: true }] },
  { id: 'm5', vendorId: '', categoryId: 'Bebidas',           name: 'Sumo de Maracujá',   description: 'Sumo natural de maracujá fresco, sem adição de açúcar.',          skus: [{ id: 's5', skuCode: 'B01', name: '500ml',    price: 120, active: true }] },
  { id: 'm6', vendorId: '', categoryId: 'Bebidas',           name: 'Água Mineral',       description: 'Água mineral natural com ou sem gás.',                           skus: [{ id: 's6', skuCode: 'B02', name: '1L',       price: 60,  active: true }] },
  { id: 'm7', vendorId: '', categoryId: 'Sobremesas',        name: 'Pudim de Côco',      description: 'Pudim artesanal de côco com calda de caramelo.',                  skus: [{ id: 's7', skuCode: 'S01', name: 'Standard', price: 180, active: true }] },
]
