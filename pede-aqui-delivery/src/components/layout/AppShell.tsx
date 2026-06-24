import { useState } from 'react'
import { Link, Outlet, useNavigate } from 'react-router-dom'
import { useSelector, useDispatch } from 'react-redux'
import { ShoppingCart, LogOut, User } from 'lucide-react'
import { CartDrawer } from '@/components/CartDrawer'
import { clearUser } from '@/store/auth-slice'
import { userManager } from '@/features/auth/oidcConfig'
import type { RootState, AppDispatch } from '@/store'
import { cartItemCount } from '@/store/cart-slice'

export function AppShell() {
  const dispatch = useDispatch<AppDispatch>()
  const navigate = useNavigate()
  const { status, displayName } = useSelector((s: RootState) => s.auth)
  const items = useSelector((s: RootState) => s.cart.items)
  const count = cartItemCount(items)
  const [cartOpen, setCartOpen] = useState(false)

  async function handleLogout() {
    dispatch(clearUser())
    await userManager.removeUser().catch(() => {})
    navigate('/')
  }

  return (
    <div className="min-h-screen bg-background">
      {/* Nav */}
      <header className="sticky top-0 z-40 bg-forest border-b border-forest-light/40">
        <div className="mx-auto flex h-14 max-w-5xl items-center justify-between px-4">
          <Link to="/" className="flex items-center gap-2">
            <span className="text-xl">🛵</span>
            <span className="font-display text-white text-lg font-bold tracking-tight">
              Pede Aqui
            </span>
          </Link>

          <div className="flex items-center gap-1">
            {/* Cart button */}
            <button
              onClick={() => setCartOpen(true)}
              className="relative flex h-9 w-9 items-center justify-center rounded-full text-white/80 hover:bg-white/10 transition-colors"
            >
              <ShoppingCart className="h-5 w-5" />
              {count > 0 && (
                <span className="absolute -right-0.5 -top-0.5 flex h-4 w-4 items-center justify-center rounded-full bg-ember text-[10px] font-bold text-white">
                  {count > 9 ? '9+' : count}
                </span>
              )}
            </button>

            {/* Auth */}
            {status === 'authenticated' ? (
              <div className="flex items-center gap-1 ml-1">
                <button
                  onClick={() => navigate('/orders')}
                  className="flex items-center gap-1.5 rounded-full bg-white/10 px-3 py-1.5 text-sm font-medium text-white hover:bg-white/20 transition-colors"
                >
                  <User className="h-3.5 w-3.5" />
                  {displayName?.split(' ')[0] ?? 'Conta'}
                </button>
                <button
                  onClick={handleLogout}
                  className="flex h-8 w-8 items-center justify-center rounded-full text-white/60 hover:text-white hover:bg-white/10 transition-colors"
                  title="Sair"
                >
                  <LogOut className="h-4 w-4" />
                </button>
              </div>
            ) : (
              <button
                onClick={() => navigate('/login')}
                className="ml-1 rounded-full border border-white/20 bg-white/10 px-3 py-1.5 text-sm font-medium text-white hover:bg-white/20 transition-colors"
              >
                Entrar
              </button>
            )}
          </div>
        </div>
      </header>

      {/* Page content — no horizontal padding; hero owns full width */}
      <main>
        <Outlet />
      </main>

      <CartDrawer open={cartOpen} onClose={() => setCartOpen(false)} />
    </div>
  )
}
