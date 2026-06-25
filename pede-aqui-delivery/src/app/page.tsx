import { useState, useMemo, useRef } from 'react'
import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { Search, ChevronLeft, ChevronRight, AlertCircle } from 'lucide-react'
import { VendorCard } from '@/components/VendorCard'
import { vendorService, catalogService } from '@/lib/api/services'
import { useDebounce } from '@/lib/useDebounce'
import { groupByVertical, verticalMeta, VERTICAL_META } from '@/lib/verticals'

export default function HomePage() {
  const [search, setSearch] = useState('')
  const [activeVertical, setActiveVertical] = useState('all')
  const debouncedSearch = useDebounce(search, 300)
  const categoryScrollRef = useRef<HTMLDivElement>(null)

  function scrollCategories(dir: 'left' | 'right') {
    categoryScrollRef.current?.scrollBy({ left: dir === 'right' ? 200 : -200, behavior: 'smooth' })
  }

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

  // Carousel: 'all' + derived verticals; browse grid: derived verticals only
  const carouselVerticals = useMemo(
    () => [{ id: 'all', ...VERTICAL_META.all }, ...verticalIds.map((id) => ({ id, ...verticalMeta(id) }))],
    [verticalIds],
  )

  return (
    <>
      {/* ── Hero ── */}
      <section className="relative overflow-hidden bg-forest px-6 pb-14 pt-10">
        <div className="pointer-events-none absolute inset-0 bg-[radial-gradient(ellipse_at_75%_30%,#2A4A2E_0%,transparent_60%)]" />
        <div className="pointer-events-none absolute right-6 top-6 grid grid-cols-5 gap-2 opacity-[0.07]">
          {Array.from({ length: 25 }).map((_, i) => (
            <div key={i} className="h-1.5 w-1.5 rounded-full bg-white" />
          ))}
        </div>
        <div className="pointer-events-none absolute inset-0 overflow-hidden select-none" aria-hidden="true">
          <span className="absolute right-6 top-3 rotate-12 text-8xl opacity-[0.055]">🍔</span>
          <span className="absolute right-32 bottom-6 -rotate-12 text-6xl opacity-[0.05]">🍕</span>
          <span className="absolute left-4 bottom-2 rotate-6 text-7xl opacity-[0.045]">🥗</span>
        </div>

        <div className="relative mx-auto max-w-5xl">
          <p className="mb-3 font-body text-sm font-semibold uppercase tracking-widest text-ember">
            🛵 Entrega em Maputo
          </p>
          <h1 className="font-display text-4xl font-black leading-[1.05] text-white sm:text-5xl lg:text-6xl">
            O que queres<br />
            <em className="not-italic text-amber-300">pedir hoje?</em>
          </h1>
          <p className="mt-3 font-body text-white/60">
            Restaurantes, supermercados, farmácias — tudo à porta.
          </p>
          <div className="relative mt-6">
            <Search className="absolute left-4 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
            <input
              type="search"
              placeholder="Pesquisar restaurantes, produtos…"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              className="h-12 w-full rounded-2xl border-0 bg-white pl-11 pr-4 text-sm font-medium text-foreground shadow-warm-lg placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:ring-ember"
            />
          </div>
        </div>

        <div className="pointer-events-none absolute bottom-0 left-0 right-0" aria-hidden="true">
          <svg viewBox="0 0 1440 40" preserveAspectRatio="none" className="h-10 w-full">
            <path d="M0 40 C 360 10 1080 10 1440 40 L1440 40 L0 40 Z" fill="hsl(36, 60%, 97%)" />
          </svg>
        </div>
      </section>

      {/* ── Browse by vertical ── */}
      {verticalIds.length > 0 && (
        <div className="mx-auto max-w-5xl px-4 pt-6 pb-2">
          <h2 className="font-display mb-3 text-lg font-bold text-foreground">Explorar por categoria</h2>
          <div className="grid grid-cols-4 gap-3 lg:grid-cols-7">
            {verticalIds.map((id) => {
              const meta = verticalMeta(id)
              return (
                <Link key={id} to={`/catalogo/${id}`} className="group block">
                  <div className="flex flex-col items-center gap-2 rounded-2xl bg-white p-3 shadow-warm ring-1 ring-border/50 transition-all duration-200 group-hover:-translate-y-1 group-hover:shadow-warm-md">
                    <span className="text-3xl leading-none">{meta.emoji}</span>
                    <span className="text-center text-xs font-semibold leading-tight text-foreground">{meta.label}</span>
                  </div>
                </Link>
              )
            })}
          </div>
        </div>
      )}

      {/* ── Category carousel ── */}
      <div className="mt-4 border-b border-border bg-white shadow-sm">
        <div className="mx-auto flex max-w-5xl items-center gap-1 px-2 py-2.5">
          <button
            onClick={() => scrollCategories('left')}
            aria-label="Anterior"
            className="hidden shrink-0 rounded-full p-1.5 text-foreground/50 transition-colors hover:bg-secondary hover:text-foreground md:flex"
          >
            <ChevronLeft className="h-4 w-4" />
          </button>
          <div
            ref={categoryScrollRef}
            className="flex flex-1 gap-2 overflow-x-auto scrollbar-hide"
            style={{ scrollSnapType: 'x mandatory' }}
          >
            {carouselVerticals.map((v) => (
              <button
                key={v.id}
                onClick={() => setActiveVertical(v.id)}
                style={{ scrollSnapAlign: 'start' }}
                className={`flex shrink-0 items-center gap-1.5 rounded-full px-4 py-1.5 text-sm font-semibold transition-all duration-200 ${
                  activeVertical === v.id
                    ? 'bg-ember text-white shadow-[0_3px_10px_rgba(232,67,12,0.35)]'
                    : 'bg-secondary text-foreground hover:bg-secondary/70'
                }`}
              >
                <span>{v.emoji}</span>
                {v.label}
              </button>
            ))}
          </div>
          <button
            onClick={() => scrollCategories('right')}
            aria-label="Próximo"
            className="hidden shrink-0 rounded-full p-1.5 text-foreground/50 transition-colors hover:bg-secondary hover:text-foreground md:flex"
          >
            <ChevronRight className="h-4 w-4" />
          </button>
        </div>
      </div>

      {/* ── Vendor grid ── */}
      <div className="mx-auto max-w-5xl px-4 py-6">
        {isLoading ? (
          <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-4">
            {Array.from({ length: 8 }).map((_, i) => (
              <div key={i} className="h-56 animate-pulse rounded-2xl bg-muted" />
            ))}
          </div>
        ) : isError ? (
          <div className="flex flex-col items-center gap-4 py-20 text-center">
            <AlertCircle className="h-10 w-10 text-muted-foreground" />
            <p className="text-muted-foreground">Não foi possível carregar os restaurantes.</p>
            <button
              onClick={() => refetch()}
              className="rounded-full bg-ember px-5 py-2 text-sm font-semibold text-white hover:bg-ember/90"
            >
              Tentar novamente
            </button>
          </div>
        ) : (
          <>
            <p className="mb-4 text-sm text-muted-foreground">
              {openCount > 0
                ? <><strong className="text-foreground">{openCount}</strong> disponíveis agora</>
                : 'Nenhum disponível agora'}
            </p>
            {vendors.length === 0 ? (
              <div className="py-20 text-center">
                <p className="text-4xl">🔍</p>
                <p className="mt-3 text-muted-foreground">
                  {debouncedSearch
                    ? `Nenhum resultado para "${debouncedSearch}"`
                    : 'Nenhum restaurante disponível'}
                </p>
              </div>
            ) : (
              <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-4">
                {vendors.map((v, i) => (
                  <div key={v.vendorId} className="card-enter" style={{ animationDelay: `${i * 50}ms` }}>
                    <VendorCard vendor={v} />
                  </div>
                ))}
              </div>
            )}
          </>
        )}
      </div>
    </>
  )
}
