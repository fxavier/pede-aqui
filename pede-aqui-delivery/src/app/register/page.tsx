import { useState, FormEvent } from 'react'
import { Link, useSearchParams, useNavigate } from 'react-router-dom'
import { useDispatch } from 'react-redux'
import { Eye, EyeOff, Loader2 } from 'lucide-react'
import { customerRegistrationService, keycloakService } from '@/lib/api/services'
import { setUser } from '@/store/auth-slice'
import type { AppDispatch } from '@/store'

export default function RegisterPage() {
  const [params] = useSearchParams()
  const navigate = useNavigate()
  const dispatch = useDispatch<AppDispatch>()
  const redirect = params.get('redirect') ?? '/'

  const [firstName, setFirstName] = useState('')
  const [lastName, setLastName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [confirm, setConfirm] = useState('')
  const [showPwd, setShowPwd] = useState(false)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    setError(null)

    if (password !== confirm) {
      setError('As palavras-passe não coincidem.')
      return
    }
    if (password.length < 8) {
      setError('A palavra-passe deve ter pelo menos 8 caracteres.')
      return
    }

    setLoading(true)
    try {
      await customerRegistrationService.register({ firstName, lastName, email, password })
      // Auto-login after registration
      const { access_token, profile } = await keycloakService.login(email, password)
      dispatch(setUser({ access_token, profile }))
      navigate(redirect, { replace: true })
    } catch (err) {
      const msg = err instanceof Error ? err.message : ''
      if (msg.includes('email_exists') || msg.includes('409')) {
        setError('Este email já está registado. Tenta entrar.')
      } else {
        setError(msg || 'Erro ao criar conta. Tenta novamente.')
      }
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="flex min-h-screen flex-col">
      {/* Top — brand */}
      <div className="flex flex-1 flex-col items-center justify-center bg-gradient-to-br from-brand-600 to-accent-orange px-6 py-10 text-center">
        <span className="text-6xl">🛵</span>
        <h1 className="font-display mt-4 text-4xl font-black text-white">Pede Aqui</h1>
        <p className="mt-2 font-body text-white/60">Cria a tua conta grátis</p>
      </div>

      {/* Bottom — form */}
      <div className="bg-background px-6 py-8">
        <div className="mx-auto max-w-xs space-y-5">
          <div>
            <h2 className="font-display text-2xl font-bold text-foreground">Criar conta</h2>
            <p className="mt-1 text-sm text-muted-foreground">Rápido e gratuito — começa a pedir em segundos.</p>
          </div>

          <form onSubmit={handleSubmit} className="space-y-3">
            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className="mb-1 block text-xs font-semibold text-foreground">Nome</label>
                <input
                  type="text"
                  required
                  autoComplete="given-name"
                  value={firstName}
                  onChange={(e) => setFirstName(e.target.value)}
                  placeholder="João"
                  className="h-11 w-full rounded-xl border border-input bg-white px-3 text-sm text-foreground placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:ring-brand-500"
                />
              </div>
              <div>
                <label className="mb-1 block text-xs font-semibold text-foreground">Apelido</label>
                <input
                  type="text"
                  required
                  autoComplete="family-name"
                  value={lastName}
                  onChange={(e) => setLastName(e.target.value)}
                  placeholder="Silva"
                  className="h-11 w-full rounded-xl border border-input bg-white px-3 text-sm text-foreground placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:ring-brand-500"
                />
              </div>
            </div>

            <div>
              <label className="mb-1 block text-xs font-semibold text-foreground">Email</label>
              <input
                type="email"
                required
                autoComplete="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                placeholder="o.teu@email.com"
                className="h-11 w-full rounded-xl border border-input bg-white px-3 text-sm text-foreground placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:ring-brand-500"
              />
            </div>

            <div>
              <label className="mb-1 block text-xs font-semibold text-foreground">Palavra-passe</label>
              <div className="relative">
                <input
                  type={showPwd ? 'text' : 'password'}
                  required
                  autoComplete="new-password"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  placeholder="Mínimo 8 caracteres"
                  className="h-11 w-full rounded-xl border border-input bg-white px-3 pr-10 text-sm text-foreground placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:ring-brand-500"
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

            <div>
              <label className="mb-1 block text-xs font-semibold text-foreground">Confirmar palavra-passe</label>
              <input
                type={showPwd ? 'text' : 'password'}
                required
                autoComplete="new-password"
                value={confirm}
                onChange={(e) => setConfirm(e.target.value)}
                placeholder="Repete a palavra-passe"
                className="h-11 w-full rounded-xl border border-input bg-white px-3 text-sm text-foreground placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:ring-brand-500"
              />
            </div>

            {error && (
              <p className="rounded-lg bg-destructive/10 px-3 py-2 text-xs font-medium text-destructive">
                {error}
              </p>
            )}

            <button
              type="submit"
              disabled={loading}
              className="flex h-11 w-full items-center justify-center gap-2 rounded-xl bg-brand-600 text-sm font-bold text-white shadow-warm-md transition-all hover:bg-brand-500 active:scale-[0.98] disabled:opacity-60"
            >
              {loading && <Loader2 className="h-4 w-4 animate-spin" />}
              Criar conta
            </button>
          </form>

          <p className="text-center text-sm text-muted-foreground">
            Já tens conta?{' '}
            <Link to={`/login?redirect=${encodeURIComponent(redirect)}`} className="font-semibold text-foreground underline underline-offset-2">
              Entrar
            </Link>
          </p>
        </div>
      </div>
    </div>
  )
}
