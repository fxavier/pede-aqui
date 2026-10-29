import { useEffect, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { useSelector } from 'react-redux'
import { Loader2 } from 'lucide-react'
import { authSession } from '@/features/auth/authSession'
import { safeRedirect } from '@/features/auth/session'
import type { RootState } from '@/store'

/**
 * Login entry point. Credentials are never typed into the app: "Entrar" starts the
 * Authorization Code + PKCE redirect to the Keycloak login page, which sends the user back to
 * /auth/callback. The page stays as a landing (instead of auto-redirecting) so new customers can
 * pick "Criar conta" — self-registration goes through the app's /register form.
 */
export default function LoginPage() {
  const [params] = useSearchParams()
  const navigate = useNavigate()
  const status = useSelector((s: RootState) => s.auth.status)
  const redirect = safeRedirect(params.get('redirect'))

  const [starting, setStarting] = useState(false)
  const [error, setError] = useState<string | null>(
    params.get('error') ? 'Não foi possível concluir a autenticação. Tenta novamente.' : null,
  )

  useEffect(() => {
    if (status === 'authenticated') navigate(redirect, { replace: true })
  }, [status, redirect, navigate])

  function startLogin() {
    setError(null)
    setStarting(true)
    authSession.login(redirect).catch(() => {
      setStarting(false)
      setError('Serviço de autenticação indisponível. Tenta novamente.')
    })
  }

  return (
    <div className="flex min-h-screen flex-col">
      {/* Top — brand */}
      <div className="flex flex-1 flex-col items-center justify-center bg-gradient-to-br from-brand-600 to-accent-orange px-6 py-12 text-center">
        <span className="text-6xl">🛵</span>
        <h1 className="font-display mt-4 text-4xl font-black text-white">Pede Aqui</h1>
        <p className="mt-2 font-body text-white/60">Entrega rápida em Maputo</p>
      </div>

      {/* Bottom — actions */}
      <div className="bg-background px-6 py-8">
        <div className="mx-auto max-w-xs space-y-5">
          <div>
            <h2 className="font-display text-2xl font-bold text-foreground">Bem-vindo de volta</h2>
            <p className="mt-1 text-sm text-muted-foreground">Entra para adicionar ao carrinho e fazer pedidos.</p>
          </div>

          {error && (
            <p className="rounded-lg bg-destructive/10 px-3 py-2 text-xs font-medium text-destructive">{error}</p>
          )}

          <button
            type="button"
            onClick={startLogin}
            disabled={starting || status === 'loading'}
            className="flex h-11 w-full items-center justify-center gap-2 rounded-xl bg-brand-600 text-sm font-bold text-white shadow-warm-md transition-all hover:bg-brand-500 active:scale-[0.98] disabled:opacity-60"
          >
            {(starting || status === 'loading') && <Loader2 className="h-4 w-4 animate-spin" />}
            Entrar
          </button>

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
