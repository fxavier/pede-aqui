import { useSelector } from 'react-redux'
import { Navigate, useLocation } from 'react-router-dom'
import type { RootState } from '@/store'

export function ProtectedRoute({ children }: { children: React.ReactNode }) {
  const status = useSelector((s: RootState) => s.auth.status)
  const location = useLocation()

  if (status === 'loading' || status === 'idle') {
    return <div className="flex min-h-screen items-center justify-center text-muted-foreground">A carregar…</div>
  }

  if (status !== 'authenticated') {
    return <Navigate to={`/login?redirect=${encodeURIComponent(location.pathname)}`} replace />
  }

  return <>{children}</>
}
