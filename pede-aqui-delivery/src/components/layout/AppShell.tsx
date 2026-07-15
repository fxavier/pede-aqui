import { useState } from 'react'
import { Link, Outlet, useNavigate, useLocation } from 'react-router-dom'
import { useSelector } from 'react-redux'
import {
  ShoppingCart, Search, MapPin, ChevronDown,
  Home, ClipboardList, User, LogOut,
} from 'lucide-react'
import { CartDrawer } from '@/components/CartDrawer'
import { logout } from '@/features/auth/logout'
import type { RootState } from '@/store'
import { cartItemCount } from '@/store/cart-slice'

/** Deterministic initials avatar (backend supplies no avatar image). */
function initials(name: string | null): string {
  if (!name) return 'PA'
  const parts = name.trim().split(/\s+/)
  return ((parts[0]?.[0] ?? '') + (parts[1]?.[0] ?? '')).toUpperCase() || 'PA'
}

export function AppShell() {
  const navigate = useNavigate()
  const location = useLocation()
  const { status, displayName } = useSelector((s: RootState) => s.auth)
  const items = useSelector((s: RootState) => s.cart.items)
  const count = cartItemCount(items)
  const [cartOpen, setCartOpen] = useState(false)
  const [search, setSearch] = useState('')

  const isAuthed = status === 'authenticated'
  const path = location.pathname
  const isHome = path === '/'
  const isOrders = path.startsWith('/orders')
  const isProfile = path.startsWith('/profile')

  async function handleLogout() {
    const redirecting = await logout()
    if (!redirecting) navigate('/')
  }

  function runSearch() {
    navigate(search.trim() ? `/?q=${encodeURIComponent(search.trim())}` : '/')
  }

  return (
    <div className="min-h-screen bg-slate-50 flex flex-col font-body antialiased">
      {/* ── Header ── */}
      <header className="sticky top-0 z-50 bg-white/95 backdrop-blur-md border-b border-slate-100 shadow-sm">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="flex items-center justify-between h-16 sm:h-20 gap-4">
            {/* Logo */}
            <Link to="/" className="flex items-center gap-2 group flex-shrink-0">
              <div className="w-10 h-10 rounded-xl bg-gradient-to-tr from-brand-600 to-accent-orange flex items-center justify-center text-white font-extrabold text-xl shadow-md shadow-brand-500/20 transform group-hover:scale-105 transition-all">
                P!
              </div>
              <div className="hidden sm:block">
                <span className="font-display font-extrabold text-2xl tracking-tight bg-gradient-to-r from-brand-600 via-rose-500 to-accent-orange bg-clip-text text-transparent">
                  Pede Aqui
                </span>
                <p className="text-[10px] text-slate-400 font-medium tracking-wider uppercase -mt-1">
                  Na velocidade do agora
                </p>
              </div>
            </Link>

            {/* Location selector — cosmetic: no geolocation source in backend */}
            <div className="hidden md:flex items-center gap-2 px-3 py-1.5 bg-slate-50 hover:bg-slate-100 rounded-full cursor-pointer transition-all border border-slate-100 text-slate-700">
              <MapPin className="w-4 h-4 text-brand-500" />
              <div className="text-left text-xs">
                <p className="text-[10px] text-slate-400 font-bold leading-none">ENTREGAR EM</p>
                <p className="font-semibold text-slate-700 leading-tight flex items-center gap-1">
                  Avenida Julius Nyerere, 1200
                  <ChevronDown className="w-3 h-3 text-slate-400" />
                </p>
              </div>
            </div>

            {/* Nav links */}
            <nav className="hidden lg:flex items-center gap-6 font-medium text-slate-600 text-sm">
              <Link to="/" className={`py-2 border-b-2 transition-all hover:text-brand-600 ${isHome ? 'text-brand-600 border-brand-600 font-bold' : 'border-transparent'}`}>Início</Link>
              <Link to="/orders" className={`py-2 border-b-2 transition-all hover:text-brand-600 ${isOrders ? 'text-brand-600 border-brand-600 font-bold' : 'border-transparent'}`}>As Minhas Encomendas</Link>
              <Link to="/profile" className={`py-2 border-b-2 transition-all hover:text-brand-600 ${isProfile ? 'text-brand-600 border-brand-600 font-bold' : 'border-transparent'}`}>Perfil</Link>
            </nav>

            {/* Actions */}
            <div className="flex items-center gap-3 sm:gap-4 flex-shrink-0">
              <div className="relative hidden sm:block w-48 md:w-64">
                <input
                  type="text"
                  placeholder="Procurar pratos ou lojas..."
                  value={search}
                  onChange={(e) => setSearch(e.target.value)}
                  onKeyDown={(e) => e.key === 'Enter' && runSearch()}
                  className="w-full bg-slate-50 border border-slate-200 rounded-full py-2 pl-9 pr-4 text-xs focus:outline-none focus:ring-2 focus:ring-brand-500/20 focus:border-brand-500 transition-all text-slate-700"
                />
                <Search className="w-3.5 h-3.5 text-slate-400 absolute left-3 top-3" />
              </div>

              <button onClick={() => navigate('/')} className="sm:hidden p-2 rounded-full bg-slate-50 hover:bg-slate-100 text-slate-600" aria-label="Procurar">
                <Search className="w-5 h-5" />
              </button>

              <button
                onClick={() => setCartOpen(true)}
                className="relative p-2.5 rounded-full bg-slate-50 hover:bg-slate-100 text-slate-700 transition-all group hover:scale-105"
                aria-label="Abrir carrinho"
              >
                <ShoppingCart className="w-5 h-5 group-hover:text-brand-600 transition-colors" />
                {count > 0 && (
                  <span className="absolute -top-1 -right-1 bg-brand-600 text-white font-extrabold text-[10px] w-5 h-5 rounded-full flex items-center justify-center border-2 border-white">
                    {count > 9 ? '9+' : count}
                  </span>
                )}
              </button>

              {isAuthed ? (
                <div className="flex items-center gap-2">
                  <button
                    onClick={() => navigate('/profile')}
                    className="flex items-center gap-2 hover:opacity-90 transition-all bg-slate-50 pl-1.5 pr-3 py-1.5 rounded-full border border-slate-100"
                  >
                    <span className="w-7 h-7 rounded-full bg-gradient-to-tr from-brand-600 to-accent-orange text-white text-[10px] font-extrabold flex items-center justify-center">
                      {initials(displayName)}
                    </span>
                    <div className="hidden md:block text-left">
                      <p className="text-[10px] text-slate-400 font-bold leading-none uppercase">Conta</p>
                      <p className="text-xs font-semibold text-slate-700 leading-tight">{displayName?.split(' ')[0] ?? 'Perfil'}</p>
                    </div>
                  </button>
                  <button onClick={handleLogout} className="p-2 rounded-full text-slate-400 hover:text-brand-600 hover:bg-slate-50 transition-all" title="Terminar sessão">
                    <LogOut className="w-4 h-4" />
                  </button>
                </div>
              ) : (
                <button
                  onClick={() => navigate(`/login?redirect=${encodeURIComponent(path)}`)}
                  className="px-4 py-2 rounded-full bg-brand-600 hover:bg-brand-500 text-white font-bold text-xs shadow-md shadow-brand-500/20 transition-all"
                >
                  Entrar
                </button>
              )}
            </div>
          </div>
        </div>
      </header>

      {/* ── Page content ── */}
      <main className="flex-1 pb-20 lg:pb-0">
        <Outlet />
      </main>

      {/* ── Mobile bottom nav ── */}
      <nav className="fixed bottom-0 left-0 right-0 z-40 bg-white/95 backdrop-blur-md border-t border-slate-100 shadow-lg block lg:hidden pb-safe-bottom">
        <div className="grid grid-cols-4 h-14 items-center max-w-md mx-auto">
          <BottomTab active={isHome} onClick={() => navigate('/')} icon={<Home className="w-5 h-5" />} label="Início" />
          <BottomTab active={false} onClick={() => navigate('/')} icon={<Search className="w-5 h-5" />} label="Procurar" />
          <BottomTab active={isOrders} onClick={() => navigate('/orders')} icon={<ClipboardList className="w-5 h-5" />} label="Encomendas" />
          <BottomTab active={isProfile} onClick={() => navigate('/profile')} icon={<User className="w-5 h-5" />} label="Perfil" />
        </div>
      </nav>

      <CartDrawer open={cartOpen} onClose={() => setCartOpen(false)} />
    </div>
  )
}

function BottomTab({ active, onClick, icon, label }: { active: boolean; onClick: () => void; icon: React.ReactNode; label: string }) {
  return (
    <button
      onClick={onClick}
      className={`flex flex-col items-center justify-center gap-0.5 text-[10px] font-bold transition-colors ${active ? 'text-brand-600' : 'text-slate-400'}`}
    >
      {icon}
      {label}
    </button>
  )
}
