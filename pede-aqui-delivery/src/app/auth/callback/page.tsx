import { useEffect, useRef } from 'react'
import { useNavigate } from 'react-router-dom'
import { useDispatch } from 'react-redux'
import { userManager } from '@/features/auth/oidcConfig'
import { profileFromUser, safeRedirect, type LoginState } from '@/features/auth/session'
import { clearUser, setUser } from '@/store/auth-slice'
import type { AppDispatch } from '@/store'

/** Completes the Authorization Code + PKCE login: exchanges the code and lands on the saved target. */
export default function CallbackPage() {
  const navigate = useNavigate()
  const dispatch = useDispatch<AppDispatch>()
  // The code is single-use; StrictMode re-runs effects, so the exchange must happen once.
  const started = useRef(false)

  useEffect(() => {
    if (started.current) return
    started.current = true
    userManager
      .signinRedirectCallback()
      .then((user) => {
        dispatch(setUser(profileFromUser(user)))
        const state = user.state as LoginState | undefined
        navigate(safeRedirect(state?.redirect), { replace: true })
      })
      .catch(() => {
        dispatch(clearUser())
        navigate('/login?error=auth_failed', { replace: true })
      })
  }, [dispatch, navigate])

  return (
    <div className="flex min-h-screen items-center justify-center text-muted-foreground">
      A autenticar…
    </div>
  )
}
