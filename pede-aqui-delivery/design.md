# Design — Main Screen (Home, `/`)

Source: `src/app/page.tsx`. Tokens: `src/index.css` + `tailwind.config.js`.

## Identity

| Token | Value |
|---|---|
| Brand | Rose scale (`brand-500 #f43f5e`, `brand-600 #e11d48` primary) |
| Neutrals | Slate (`slate-50` background, `slate-800` foreground) |
| Accent | `accent-orange #FF5A1F` (rarely used) |
| Font | Plus Jakarta Sans (display + body); Fraunces serif fallback |
| Radius | `--radius: 0.875rem`; cards `rounded-2xl`, sections `rounded-3xl`, pills/CTAs `rounded-full` |
| Shadows | `shadow-warm*` (brown-tinted) available; home mostly uses `shadow-sm/md/xl` |
| Page bg | `slate-50` (`--background`), white cards |

## Layout (top → bottom)

Vertical stack, `space-y-10`, content capped at `max-w-7xl`.

1. **Hero / promo carousel** — dark `slate-900` rounded-3xl banner, left gradient overlay. Static `PROMOS` array (3 entries, cosmetic — no backend source). Uppercase tag pill (`brand-600`, spinning Sparkles icon), `text-3xl/5xl` extrabold title, subtitle, "Ver lojas" CTA (scrolls to grid), dot indicators (active dot widens to `w-6`). Manual rotation only — no autoplay.
2. **Categories** — "O que quer encomendar hoje?". 2-col mobile / 4-col desktop grid of vertical tiles (`h-32/36`, dark gradient, big translucent emoji top-right, glass emoji chip + label bottom-left, `hover:-translate-y-1`). Links to `/catalogo/:verticalId`. Data: `GET /catalog/categories` grouped by `vertical` (`src/lib/verticals.ts` maps slug → emoji/label). Section hidden if no categories.
3. **Populares** — "Mais bem avaliados 🔥": top 3 vendors by rating from the current result set, rendered as `VendorCard`s. Hidden when empty.
4. **Filter pills + vendor grid** — horizontally scrollable pill carousel ("Todos" + verticals; active = `brand-600` filled, inactive = `slate-50` bordered), snap scrolling, desktop chevron scroll buttons. Grid is 1/2/3 cols of `VendorCard`s with staggered `card-enter` fade-up (50 ms/card delay). Above the grid: "N disponíveis agora" count.
5. **Partner banner** — cosmetic dark gradient (`slate-900 → brand-900`) rounded-3xl with a blurred brand-500 glow circle; "Explorar lojas" white CTA scrolls to grid.

## VendorCard (`src/components/VendorCard.tsx`)

White `rounded-2xl` card, `border-slate-100`, hover lifts shadow.
- Cover: `h-44` deterministic gradient + emoji from `vendorCover(name)` (backend has no images); emoji scales on hover.
- Badges: "Rápido 🔥" (brand pill, when `estimatedDeliveryMinutes ≤ 20` and open); "Aberto" (white pill, pulsing emerald dot) or full-cover frosted overlay with "Fechado" pill.
- Footer strip (`slate-50/50`): rating chip (amber star), ETA min, distance km, delivery fee (`Grátis` when 0, else MZN).

## Data & states

| Query | Endpoint | Notes |
|---|---|---|
| `['categories']` | `GET /catalog/categories` | 5 min staleTime, public |
| `['vendors', vertical]` | `GET /search/vendors?category=` | public; refetch on pill change |

- Search: `?q=` from header synced into local state, debounced 300 ms, filters vendor names client-side.
- Loading: 6 pulse skeletons (`h-72 rounded-2xl bg-slate-100`).
- Error: AlertCircle icon + "Tentar novamente" retry button.
- Empty: white rounded-3xl card, 🔍 circle, message varies with/without active search.

## Motion

- `card-enter` / `fade-up`: 0.45 s translateY(18px) fade, staggered.
- `spin-slow`: 4 s rotation on hero Sparkles.
- Smooth scroll for pill carousel and scroll-to-grid; `scroll-mt-24` offsets sticky header.
- Hover micro-moves: CTA `translate-x-1`, tiles `-translate-y-1`, cover emoji `scale-110`.

## Language

All copy in Portuguese (Mozambique market). Currency: MZN via `formatMZN`.
