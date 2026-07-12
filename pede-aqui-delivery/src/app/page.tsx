import { useState, useMemo, useRef, useEffect } from 'react'
import type { ReactNode } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { ArrowRight, ArrowUpRight, Sparkles, AlertCircle, ChevronLeft, ChevronRight } from 'lucide-react'
import { VendorCard } from '@/components/VendorCard'
import { vendorService, catalogService } from '@/lib/api/services'
import { useDebounce } from '@/lib/useDebounce'
import { groupByVertical, verticalMeta, VERTICAL_META } from '@/lib/verticals'

/* Cosmetic: promotional banners have no backend source. Static curated copy. */
const PROMOS = [
  { tag: 'FESTIVAL',         title: 'Festival de Sabores',  subtitle: 'Até 40% de desconto nos melhores pratos africanos e de fusão.',            emoji: '🍛', friends: ['🌶️', '🥘'] },
  { tag: 'ENTREGA GRATUITA', title: 'Mercado Sem Taxa',     subtitle: 'Entrega grátis nas compras acima de 800 MT em frutas e legumes.',          emoji: '🥬', friends: ['🍅', '🥑'] },
  { tag: 'DOBRO',            title: 'Happy Hour em Dobro',  subtitle: 'Peça bebidas selecionadas e ganhe outra por nossa conta.',                 emoji: '🍹', friends: ['🍋', '🥂'] },
]

function SectionHeader({ eyebrow, title, sub, action }: { eyebrow: string; title: ReactNode; sub?: string; action?: ReactNode }) {
  return (
    <div className="mb-6 flex items-end justify-between gap-4">
      <div>
        <p className="mb-1.5 text-[11px] font-extrabold uppercase tracking-[0.18em] text-brand-600">{eyebrow}</p>
        <h2 className="font-serif text-2xl font-black tracking-tight text-slate-900 sm:text-3xl text-balance">{title}</h2>
        {sub && <p className="mt-1.5 text-xs font-medium text-slate-400">{sub}</p>}
      </div>
      {action}
    </div>
  )
}

export default function HomePage() {
  const [params] = useSearchParams()
  const [search, setSearch] = useState(params.get('q') ?? '')
  const [activeVertical, setActiveVertical] = useState('all')
  const [activePromo, setActivePromo] = useState(0)
  const debouncedSearch = useDebounce(search, 300)
  const carouselRef = useRef<HTMLDivElement>(null)
  const gridRef = useRef<HTMLDivElement>(null)

  // Keep local search in sync when the header pushes a ?q= query.
  useEffect(() => { setSearch(params.get('q') ?? '') }, [params])

  const { data: categoriesData, isError: categoriesError } = useQuery({
    queryKey: ['categories'],
    queryFn: () => catalogService.getCategories(),
    staleTime: 5 * 60 * 1000,
    retry: 1,
  })

  const verticalIds = useMemo(
    () => (categoriesError ? [] : groupByVertical(categoriesData ?? [])),
    [categoriesData, categoriesError],
  )

  const { data, isLoading, isError, refetch } = useQuery({
    queryKey: ['vendors', activeVertical],
    queryFn: () => vendorService.search({ category: activeVertical !== 'all' ? activeVertical : undefined }),
    retry: 1,
  })

  const vendors = useMemo(() => {
    const list = data?.vendors ?? []
    return list.filter((v) => v.name.toLowerCase().includes(debouncedSearch.toLowerCase()))
  }, [data, debouncedSearch])

  const openCount = vendors.filter((v) => v.available).length
  const popular = useMemo(
    () => [...vendors].sort((a, b) => b.rating - a.rating).slice(0, 3),
    [vendors],
  )

  const carouselVerticals = useMemo(
    () => [{ id: 'all', ...VERTICAL_META.all }, ...verticalIds.map((id) => ({ id, ...verticalMeta(id) }))],
    [verticalIds],
  )

  const promo = PROMOS[activePromo]

  function stepPromo(dir: 1 | -1) {
    setActivePromo((p) => (p + dir + PROMOS.length) % PROMOS.length)
  }
  function scrollPills(dir: 'left' | 'right') {
    carouselRef.current?.scrollBy({ left: dir === 'right' ? 200 : -200, behavior: 'smooth' })
  }
  function scrollToGrid() {
    gridRef.current?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  }

  return (
    <div className="space-y-12 py-6 sm:py-10">
      {/* ── Hero + promo carousel (cosmetic content, manual rotation) ── */}
      <section
        aria-roledescription="carrossel"
        className="grain relative mx-4 max-w-7xl overflow-hidden rounded-3xl bg-slate-950 text-white shadow-warm-lg sm:mx-6 lg:mx-auto"
      >
        {/* Atmosphere: rose glow field + ember, under the grain overlay */}
        <div className="pointer-events-none absolute -right-16 -top-24 h-96 w-96 rounded-full bg-brand-600/40 blur-3xl" />
        <div className="pointer-events-none absolute -bottom-32 -left-20 h-80 w-80 rounded-full bg-accent-orange/20 blur-3xl" />

        <div className="relative z-20 grid gap-8 px-6 py-12 sm:grid-cols-[minmax(0,1fr)_auto] sm:items-center sm:px-12 sm:py-16">
          <div className="max-w-2xl space-y-6">
            <div key={activePromo} aria-live="polite" className="animate-fade-up space-y-5">
              <span className="inline-flex items-center gap-1.5 rounded-full bg-brand-600 px-3 py-1 text-xs font-bold uppercase tracking-wider text-white shadow-lg shadow-brand-500/20">
                <Sparkles className="h-3.5 w-3.5 animate-spin-slow" />
                {promo.tag}
              </span>
              <h1 className="font-serif text-4xl font-black leading-[0.95] tracking-tight text-balance sm:text-6xl">
                {promo.title}
              </h1>
              <p className="max-w-md text-sm font-medium leading-relaxed text-slate-300 sm:text-base">
                {promo.subtitle}
              </p>
            </div>

            <div className="flex flex-wrap items-center gap-4 pt-1">
              <button
                onClick={scrollToGrid}
                className="flex items-center gap-2 rounded-full bg-brand-600 px-6 py-3 text-sm font-bold text-white shadow-lg shadow-brand-500/20 transition-all hover:translate-x-1 hover:bg-brand-500 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-white/70"
              >
                Ver lojas <ArrowRight className="h-4 w-4" />
              </button>
              <div className="flex items-center gap-2">
                <button
                  onClick={() => stepPromo(-1)}
                  aria-label="Promoção anterior"
                  className="rounded-full bg-white/10 p-1.5 text-white/70 transition-colors hover:bg-white/20 hover:text-white"
                >
                  <ChevronLeft className="h-4 w-4" />
                </button>
                {PROMOS.map((_, i) => (
                  <button
                    key={i}
                    onClick={() => setActivePromo(i)}
                    aria-label={`Promoção ${i + 1}`}
                    className={`h-2.5 rounded-full transition-all ${i === activePromo ? 'w-6 bg-white' : 'w-2.5 bg-white/40 hover:bg-white/60'}`}
                  />
                ))}
                <button
                  onClick={() => stepPromo(1)}
                  aria-label="Próxima promoção"
                  className="rounded-full bg-white/10 p-1.5 text-white/70 transition-colors hover:bg-white/20 hover:text-white"
                >
                  <ChevronRight className="h-4 w-4" />
                </button>
              </div>
            </div>
          </div>

          {/* Emoji artwork (decorative) */}
          <div key={`art-${activePromo}`} aria-hidden className="relative hidden h-56 w-56 animate-fade-in items-center justify-center sm:flex lg:h-64 lg:w-64">
            <div className="animate-orbit absolute inset-0 rounded-full border border-dashed border-white/15" />
            <div className="absolute h-40 w-40 rounded-full bg-brand-500/25 blur-2xl" />
            <span className="animate-floaty relative text-8xl drop-shadow-2xl lg:text-9xl">{promo.emoji}</span>
            <span className="animate-floaty absolute -top-1 right-3 text-3xl [animation-delay:1.2s]">{promo.friends[0]}</span>
            <span className="animate-floaty absolute -left-1 bottom-4 text-3xl [animation-delay:2.4s]">{promo.friends[1]}</span>
          </div>
        </div>
      </section>

      {/* ── Categories (real verticals) ── */}
      {verticalIds.length > 0 && (
        <section className="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
          <SectionHeader
            eyebrow="Categorias"
            title={<>O que quer <span className="italic text-brand-600">encomendar</span> hoje?</>}
            sub="As melhores lojas da sua região a um clique"
          />
          <div className="grid grid-cols-2 gap-4 md:grid-cols-4">
            {verticalIds.map((id) => {
              const meta = verticalMeta(id)
              return (
                <Link
                  key={id}
                  to={`/catalogo/${id}`}
                  style={{ background: `linear-gradient(135deg, ${meta.from}, ${meta.to})` }}
                  className="group relative flex h-32 items-end overflow-hidden rounded-2xl p-4 shadow-warm ring-1 ring-slate-900/5 transition-all duration-300 hover:-translate-y-1 hover:shadow-warm-md sm:h-36"
                >
                  <span aria-hidden className="absolute -right-2 -top-2 text-6xl opacity-40 transition-transform duration-300 group-hover:-rotate-6 group-hover:scale-110">
                    {meta.emoji}
                  </span>
                  <div className="relative z-10 text-left">
                    <div className="mb-1.5 flex h-8 w-8 items-center justify-center rounded-lg border border-white/80 bg-white/70 text-base shadow-sm backdrop-blur">
                      {meta.emoji}
                    </div>
                    <h3 className="flex items-center gap-1 font-display text-sm font-extrabold tracking-tight text-slate-900">
                      {meta.label}
                      <ArrowUpRight className="h-3.5 w-3.5 -translate-x-1 opacity-0 transition-all group-hover:translate-x-0 group-hover:opacity-100" />
                    </h3>
                  </div>
                </Link>
              )
            })}
          </div>
        </section>
      )}

      {/* ── Populares (real, top-rated vendors) ── */}
      {popular.length > 0 && (
        <section className="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
          <SectionHeader
            eyebrow="Favoritos da cidade"
            title={<>Mais bem <span className="italic text-brand-600">avaliados</span> 🔥</>}
            sub="As lojas que todos estão a adorar agora"
            action={
              <button onClick={scrollToGrid} className="flex shrink-0 items-center gap-1 pb-1 text-xs font-bold text-brand-600 transition-colors hover:text-brand-700">
                Ver mais <ArrowRight className="h-3.5 w-3.5" />
              </button>
            }
          />
          <div className="grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-3">
            {popular.map((v, i) => (
              <div key={v.vendorId} className="relative">
                <span
                  aria-label={`${i + 1}º lugar`}
                  className="absolute -left-2.5 -top-2.5 z-10 flex h-9 w-9 items-center justify-center rounded-full bg-brand-600 font-serif text-sm font-black text-white shadow-warm-md ring-4 ring-background"
                >
                  {i + 1}
                </span>
                <VendorCard vendor={v} />
              </div>
            ))}
          </div>
        </section>
      )}

      {/* ── Category carousel + full vendor grid ── */}
      <section ref={gridRef} className="mx-auto max-w-7xl scroll-mt-24 space-y-6 px-4 sm:px-6 lg:px-8">
        <SectionHeader eyebrow="Explorar" title="Todas as lojas" />

        <div className="flex items-center gap-1">
          <button onClick={() => scrollPills('left')} aria-label="Anterior" className="hidden shrink-0 rounded-full p-1.5 text-slate-400 transition-colors hover:bg-slate-100 hover:text-slate-700 md:flex">
            <ChevronLeft className="h-4 w-4" />
          </button>
          <div ref={carouselRef} className="scrollbar-hide fade-x-edges flex flex-1 gap-2 overflow-x-auto px-1 py-1" style={{ scrollSnapType: 'x mandatory' }}>
            {carouselVerticals.map((v) => (
              <button
                key={v.id}
                onClick={() => setActiveVertical(v.id)}
                style={{ scrollSnapAlign: 'start' }}
                className={`flex shrink-0 items-center gap-1.5 rounded-full px-4 py-2 text-xs font-bold transition-all focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-400 ${
                  activeVertical === v.id
                    ? 'scale-[1.03] bg-brand-600 text-white shadow-md shadow-brand-500/25'
                    : 'bg-white text-slate-600 ring-1 ring-slate-200 hover:text-brand-700 hover:ring-brand-300'
                }`}
              >
                <span>{v.emoji}</span> {v.label}
              </button>
            ))}
          </div>
          <button onClick={() => scrollPills('right')} aria-label="Próximo" className="hidden shrink-0 rounded-full p-1.5 text-slate-400 transition-colors hover:bg-slate-100 hover:text-slate-700 md:flex">
            <ChevronRight className="h-4 w-4" />
          </button>
        </div>

        {isLoading ? (
          <div className="grid grid-cols-1 gap-6 md:grid-cols-2 lg:grid-cols-3">
            {Array.from({ length: 6 }).map((_, i) => <div key={i} className="h-72 animate-pulse rounded-2xl bg-slate-100" />)}
          </div>
        ) : isError ? (
          <div className="flex flex-col items-center gap-4 py-20 text-center">
            <AlertCircle className="h-10 w-10 text-slate-300" />
            <p className="text-slate-500">Não foi possível carregar as lojas.</p>
            <button onClick={() => refetch()} className="rounded-full bg-brand-600 px-5 py-2 text-sm font-bold text-white hover:bg-brand-500">Tentar novamente</button>
          </div>
        ) : (
          <>
            <div className="flex items-center gap-2 text-sm text-slate-500">
              {openCount > 0 && (
                <span aria-hidden className="relative flex h-2 w-2">
                  <span className="absolute inline-flex h-full w-full animate-ping rounded-full bg-emerald-400 opacity-75" />
                  <span className="relative inline-flex h-2 w-2 rounded-full bg-emerald-500" />
                </span>
              )}
              <p>
                {openCount > 0 ? <><strong className="text-slate-800">{openCount}</strong> disponíveis agora</> : 'Nenhuma loja disponível agora'}
              </p>
            </div>
            {vendors.length === 0 ? (
              <div className="mx-auto max-w-md space-y-2 rounded-3xl border border-slate-100 bg-white p-12 text-center shadow-warm">
                <div className="mx-auto flex h-16 w-16 items-center justify-center rounded-full bg-brand-50 text-3xl">🔍</div>
                <h3 className="font-serif text-lg font-black text-slate-900">Nenhum local encontrado</h3>
                <p className="text-xs font-medium text-slate-400">
                  {debouncedSearch ? `Sem resultados para "${debouncedSearch}".` : 'Tente outra categoria.'}
                </p>
              </div>
            ) : (
              <div className="grid grid-cols-1 gap-6 md:grid-cols-2 lg:grid-cols-3">
                {vendors.map((v, i) => (
                  <div key={v.vendorId} className="card-enter h-full" style={{ animationDelay: `${i * 50}ms` }}>
                    <VendorCard vendor={v} />
                  </div>
                ))}
              </div>
            )}
          </>
        )}
      </section>

      {/* ── Partner banner (cosmetic) ── */}
      <section className="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <div className="grain relative overflow-hidden rounded-3xl bg-gradient-to-tr from-brand-800 via-brand-900 to-slate-950 p-8 text-white shadow-warm-lg sm:p-12">
          <div className="dots pointer-events-none absolute inset-0 opacity-60" />
          <div className="pointer-events-none absolute -bottom-12 -right-12 h-56 w-56 rounded-full bg-brand-500 opacity-40 blur-3xl" />
          <span aria-hidden className="absolute right-10 top-8 hidden -rotate-12 text-7xl opacity-20 sm:block">🛵</span>
          <div className="relative z-10 max-w-xl space-y-4 text-left">
            <p className="text-[11px] font-extrabold uppercase tracking-[0.18em] text-brand-200">Sabor local</p>
            <h3 className="font-serif text-2xl font-black leading-tight tracking-tight text-balance sm:text-4xl">
              Peça de estabelecimentos locais <span className="italic text-brand-300">icónicos</span>
            </h3>
            <p className="max-w-md text-xs leading-relaxed text-brand-100/80 sm:text-sm">
              Valorizamos os empreendedores locais e trazemos o autêntico sabor das redondezas até à sua mesa, com entrega rápida e garantida.
            </p>
            <button
              onClick={scrollToGrid}
              className="flex items-center gap-1 rounded-full bg-white px-5 py-2.5 text-xs font-bold text-brand-700 shadow-md transition-all hover:translate-x-1 hover:bg-brand-50"
            >
              Explorar lojas <ArrowRight className="h-3.5 w-3.5" />
            </button>
          </div>
        </div>
      </section>
    </div>
  )
}
