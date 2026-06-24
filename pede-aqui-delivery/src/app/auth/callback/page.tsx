import { useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { useDispatch } from 'react-redux'
import { userManager } from '@/features/auth/oidcConfig'
import { setUser } from '@/store/auth-slice'
import type { AppDispatch } from '@/store'

export default function CallbackPage() {
  const navigate = useNavigate()
  const dispatch = useDispatch<AppDispatch>()

  useEffect(() => {
    userManager
      .signinRedirectCallback()
      .then((user) => {
        dispatch(setUser(user))
        const state = user.state as { redirect?: string } | undefined
        navigate(state?.redirect ?? '/', { replace: true })
      })
      .catch(() => navigate('/login?error=auth_failed', { replace: true }))
  }, [dispatch, navigate])

  return (
    <div className="flex min-h-screen items-center justify-center text-muted-foreground">
      A autenticar…
    </div>
  )
}
