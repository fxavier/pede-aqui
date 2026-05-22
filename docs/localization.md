# Localization Guide

## Language
- **Language**: Portuguese (Portugal) — `pt_PT`
- **Locale**: Mozambique — `pt_MZ`
- **Do NOT use**: Brazilian Portuguese variants (e.g., "você" instead of "tu", "abrir sessão" instead of "iniciar sessão")

## Key Terminology

### Navigation & Actions
| Portuguese (PT) | English | Notes |
|---|---|---|
| Início | Home | |
| Pesquisar | Search | |
| Carrinho | Cart | |
| Encomendas | Orders | NOT "Pedidos" for orders list |
| Perfil | Profile | |
| Entrar | Login | |
| Registar | Register | NOT "Cadastrar" (BR) |
| Iniciar sessão | Sign in | |
| Terminar sessão | Sign out | |
| Confirmar | Confirm | |
| Cancelar | Cancel | |
| Guardar | Save | |
| Editar | Edit | |
| Eliminar | Delete | |
| Voltar | Back | |

### Order & Delivery
| Portuguese (PT) | English | Notes |
|---|---|---|
| Encomenda | Order | |
| Entrega | Delivery | |
| Recolha | Pickup | |
| Estafeta | Courier / Driver | |
| Motorista | Driver | Use for vehicle context |
| Pendente | Pending | |
| Aceite | Accepted | |
| Rejeitado | Rejected | |
| Cancelado | Cancelled | |
| Entregue | Delivered | |
| Preparo | Preparation | |
| Código de entrega | Delivery code | |

### Financial
| Portuguese (PT) | English | Notes |
|---|---|---|
| Carteira | Wallet | |
| Pagamento | Payment | |
| Reembolso | Refund | |
| Factura | Invoice | |
| Subtotal | Subtotal | |
| Taxa de entrega | Delivery fee | |
| Impostos | Taxes | |
| Desconto | Discount | |
| Total | Total | |
| Ganhos | Earnings | |

### Account & Profile
| Portuguese (PT) | English | Notes |
|---|---|---|
| Nome completo | Full name | |
| Telemóvel | Mobile phone | NOT "Celular" (BR) |
| Palavra-passe | Password | NOT "Senha" (BR) |
| Morada | Address | NOT "Endereço" (BR) |
| Contacto | Contact | NOT "Contato" (BR) |
| Notificações | Notifications | |

### Status Labels
| Portuguese (PT) | English | Color |
|---|---|---|
| Pendente | Pending | Yellow/Gray |
| Confirmado | Confirmed | Green |
| Em preparo | Preparing | Blue |
| A caminho | On the way | Blue |
| Entregue | Delivered | Green |
| Falhou | Failed | Red |
| Cancelado | Cancelled | Red |
| Pago | Paid | Green |
| Reembolsado | Refunded | Green |

### Time & Date
- Date format: `dd/mm/aaaa` (e.g., 21/05/2026)
- Time format: `HH:mm` (24h)
- Today: Hoje
- Yesterday: Ontem
- Tomorrow: Amanhã
- Weekdays: Segunda, Terça, Quarta, Quinta, Sexta, Sábado, Domingo

## Currency Formatting

### Default Currency: MZN (Metical)
- Symbol: MT
- Display format: `1 250,00 MT` or `MZN 1 250,00`
- Decimal separator: `,` (comma)
- Thousands separator: ` ` (space)
- Example amounts:
  - 1250.00 → `1 250,00 MT`
  - 45.50 → `45,50 MT`
  - 100000.00 → `100 000,00 MT`

### Implementation

#### Flutter
```dart
// Use NumberFormat with pt_MZ locale
NumberFormat.currency(locale: 'pt_MZ', symbol: 'MT')
```

#### Next.js / TypeScript
```typescript
// Use Intl with pt-PT locale
new Intl.NumberFormat('pt-PT', { style: 'currency', currency: 'MZN' })
```

## Phone Numbers
- Format: `+258 XX XXX XXXX` (Mozambique country code +258)
- Example: `+258 84 123 4567`

## Addresses (Mozambique Format)
- Street, Number, District
- City, Province/Region
- Example: `Av. Julius Nyerere, 123, Polana, Maputo`

## Setup per App

### Flutter Apps
- Locale set in `MaterialApp.locale`: `Locale('pt', 'MZ')`
- Supported locales: `[Locale('pt', 'MZ'), Locale('pt', 'PT')]`
- Localizations delegates: `GlobalMaterialLocalizations`, `GlobalWidgetsLocalizations`, `GlobalCupertinoLocalizations`
- Currency formatter: `NumberFormat.currency(locale: 'pt_MZ', symbol: 'MT')`

### Next.js Backoffice
- Locale set in HTML tag: `<html lang="pt">`
- Number formatting: `Intl.NumberFormat('pt-PT')`
- Currency formatting: `Intl.NumberFormat('pt-PT', { style: 'currency', currency: 'MZN' })`
