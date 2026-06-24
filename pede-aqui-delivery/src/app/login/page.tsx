import { useState, FormEvent } from 'react'
import { Link, useSearchParams, useNavigate } from 'react-router-dom'
import { useDispatch } from 'react-redux'
import { Eye, EyeOff, Loader2 } from 'lucide-react'
import { keycloakService } from '@/lib/api/services'
import { setUser } from '@/store/auth-slice'
import type { AppDispatch } from '@/store'

export default function LoginPage() {
  const [params] = useSearchParams()
  const navigate = useNavigate()
  const dispatch = useDispatch<AppDispatch>()
  const redirect = params.get('redirect') ?? '/'

  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [showPwd, setShowPwd] = useState(false)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    setLoading(true)
    try {
      const { access_token, profile } = await keycloakService.login(email, password)
      dispatch(setUser({ access_token, profile }))
      navigate(redirect, { replace: true })
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Erro ao entrar. Tenta novamente.')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="flex min-h-screen flex-col">
      {/* Top — brand */}
      <div className="flex flex-1 flex-col items-center justify-center bg-forest px-6 py-12 text-center">
        <span className="text-6xl">🛵</span>
        <h1 className="font-display mt-4 text-4xl font-black text-white">Pede Aqui</h1>
        <p className="mt-2 font-body text-white/60">Entrega rápida em Maputo</p>
      </div>

      {/* Bottom — form */}
      <div className="bg-background px-6 py-8">
        <div className="mx-auto max-w-xs space-y-5">
          <div>
            <h2 className="font-display text-2xl font-bold text-foreground">Bem-vindo de volta</h2>
            <p className="mt-1 text-sm text-muted-foreground">Entra para adicionar ao carrinho e fazer pedidos.</p>
          </div>

          <form onSubmit={handleSubmit} className="space-y-3">
            <div>
              <label className="mb-1 block text-xs font-semibold text-foreground">Email</label>
              <input
                type="email"
                required
                autoComplete="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                placeholder="o.teu@email.com"
                className="h-11 w-full rounded-xl border border-input bg-white px-3 text-sm text-foreground placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:ring-ember"
              />
            </div>

            <div>
              <label className="mb-1 block text-xs font-semibold text-foreground">Palavra-passe</label>
              <div className="relative">
                <input
                  type={showPwd ? 'text' : 'password'}
                  required
                  autoComplete="current-password"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  placeholder="••••••••"
                  className="h-11 w-full rounded-xl border border-input bg-white px-3 pr-10 text-sm text-foreground placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:ring-ember"
                />
                <button
                  type="button"
                  onClick={() => setShowPwd((v) => !v)}
                  className="absolute right-3 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground"
                >
                  {showPwd ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                </button>
              </div>
            </div>

            {error && (
              <p className="rounded-lg bg-destructive/10 px-3 py-2 text-xs font-medium text-destructive">
                {error}
              </p>
            )}

            <button
              type="submit"
              disabled={loading}
              className="flex h-11 w-full items-center justify-center gap-2 rounded-xl bg-ember text-sm font-bold text-white shadow-warm-md transition-all hover:bg-ember/90 active:scale-[0.98] disabled:opacity-60"
            >
              {loading && <Loader2 className="h-4 w-4 animate-spin" />}
              Entrar
            </button>
          </form>

          <p className="text-center text-sm text-muted-foreground">
            Não tens conta?{' '}
            <Link to={`/register?redirect=${encodeURIComponent(redirect)}`} className="font-semibold text-foreground underline underline-offset-2">
              Criar conta
            </Link>
          </p>

          <p className="text-center text-xs text-muted-foreground">
            <Link to="/" className="underline">Explorar sem conta</Link>
          </p>
        </div>
      </div>
    </div>
  )
}
