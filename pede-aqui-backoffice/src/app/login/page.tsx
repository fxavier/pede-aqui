'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { useAppDispatch } from '@/store/hooks';
import { setUser } from '@/store/slices/auth-slice';
import { authService } from '@/lib/api/services';

export default function LoginPage() {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const router = useRouter();
  const dispatch = useAppDispatch();

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setLoading(true);

    try {
      const keycloakUrl = process.env.NEXT_PUBLIC_KEYCLOAK_URL ?? 'http://localhost:8081';
      const keycloakRealm = process.env.NEXT_PUBLIC_KEYCLOAK_REALM ?? 'delivery';
      const keycloakClientId = process.env.NEXT_PUBLIC_KEYCLOAK_CLIENT_ID ?? 'delivery-app';

      const tokenResponse = await fetch(
        `${keycloakUrl}/realms/${keycloakRealm}/protocol/openid-connect/token`,
        {
          method: 'POST',
          headers: { 'Content-Type': 'application/x-www-form-urlencoded', Accept: 'application/json' },
          body: `grant_type=password&client_id=${keycloakClientId}&username=${encodeURIComponent(email)}&password=${encodeURIComponent(password)}`,
        }
      );

      if (!tokenResponse.ok) throw new Error('Credenciais inválidas');

      const tokenData = await tokenResponse.json();
      const accessToken = tokenData.access_token;

      sessionStorage.setItem('auth_token', accessToken);
      document.cookie = `auth_token=${accessToken}; path=/; SameSite=Strict`;

      const profileData = await authService.getMe();
      const tenantId: string | null = profileData.tenantId ?? null;
      if (tenantId) sessionStorage.setItem('tenant_id', tenantId);
      else sessionStorage.removeItem('tenant_id');

      const isPlatformAdmin = !tenantId && (profileData.roles ?? []).includes('ADMIN');

      dispatch(setUser({
        name: profileData.displayName,
        role: profileData.roles?.[0] || '',
        tenant: profileData.tenantId ?? 'Pede Aqui Platform',
        tenantId,
        activeTenantId: null,
        activeTenantName: null,
        email: profileData.email,
        token: accessToken,
      }));

      router.push(isPlatformAdmin ? '/platform' : '/');
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Credenciais inválidas. Verifique o email e a palavra-passe.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <>
      <style>{`
        .login-page { font-family: 'Plus Jakarta Sans', 'DM Sans', sans-serif; }

        /* ---- left panel ---- */
        .auth-left {
          background:
            radial-gradient(ellipse at 10% 90%, rgba(179,39,0,0.35) 0%, transparent 55%),
            radial-gradient(ellipse at 85% 15%, rgba(179,39,0,0.18) 0%, transparent 45%),
            #160A04;
        }
        .dot-grid {
          background-image: radial-gradient(circle, rgba(255,255,255,0.18) 1px, transparent 1px);
          background-size: 28px 28px;
        }
        .ring-deco {
          position: absolute;
          border-radius: 50%;
          border: 1px solid rgba(255,255,255,0.07);
        }

        /* ---- form inputs ---- */
        .field-wrap { position: relative; }
        .field-icon {
          position: absolute;
          left: 14px;
          top: 50%;
          transform: translateY(-50%);
          color: #9CA3AF;
          font-size: 18px;
          pointer-events: none;
          transition: color 0.2s;
        }
        .auth-input {
          width: 100%;
          height: 52px;
          padding: 0 44px 0 44px;
          background: #F9FAFB;
          border: 1.5px solid #E5E7EB;
          border-radius: 12px;
          font-size: 14px;
          color: #111827;
          font-family: inherit;
          transition: border-color 0.2s, box-shadow 0.2s, background 0.2s;
          outline: none;
        }
        .auth-input::placeholder { color: #9CA3AF; }
        .auth-input:focus {
          border-color: #B32700;
          box-shadow: 0 0 0 3px rgba(179,39,0,0.10);
          background: #fff;
        }
        .auth-input:focus ~ .field-icon,
        .field-wrap:focus-within .field-icon { color: #B32700; }

        /* ---- eye toggle ---- */
        .eye-btn {
          position: absolute;
          right: 14px;
          top: 50%;
          transform: translateY(-50%);
          background: none;
          border: none;
          cursor: pointer;
          color: #9CA3AF;
          display: flex;
          align-items: center;
          padding: 0;
          font-size: 18px;
          transition: color 0.2s;
        }
        .eye-btn:hover { color: #6B7280; }

        /* ---- primary button ---- */
        .btn-auth {
          width: 100%;
          height: 52px;
          background: #B32700;
          color: #fff;
          border: none;
          border-radius: 12px;
          font-size: 15px;
          font-weight: 600;
          font-family: inherit;
          cursor: pointer;
          transition: background 0.2s, transform 0.15s, box-shadow 0.2s;
          display: flex;
          align-items: center;
          justify-content: center;
          gap: 8px;
        }
        .btn-auth:hover:not(:disabled) {
          background: #961F00;
          transform: translateY(-1px);
          box-shadow: 0 6px 16px rgba(179,39,0,0.28);
        }
        .btn-auth:active:not(:disabled) { transform: translateY(0); }
        .btn-auth:disabled { opacity: 0.6; cursor: not-allowed; }

        /* ---- spinner ---- */
        @keyframes pa-spin { to { transform: rotate(360deg); } }
        .pa-spinner { animation: pa-spin 0.75s linear infinite; }

        /* ---- fade-in ---- */
        @keyframes pa-fadeup {
          from { opacity: 0; transform: translateY(14px); }
          to   { opacity: 1; transform: translateY(0); }
        }
        .pa-fadeup { animation: pa-fadeup 0.45s ease both; }
        .pa-fadeup-2 { animation: pa-fadeup 0.45s 0.08s ease both; }
        .pa-fadeup-3 { animation: pa-fadeup 0.45s 0.16s ease both; }

        /* ---- error shake ---- */
        @keyframes pa-shake {
          0%,100% { transform: translateX(0); }
          20%,60% { transform: translateX(-5px); }
          40%,80% { transform: translateX(5px); }
        }
        .pa-error { animation: pa-shake 0.35s ease; }
      `}</style>

      <div className="login-page min-h-screen flex">

        {/* ═══ LEFT BRAND PANEL ═══ */}
        <div className="auth-left hidden lg:flex lg:w-[42%] xl:w-[40%] relative flex-col justify-between p-12 overflow-hidden shrink-0">

          {/* Dot-grid texture */}
          <div className="dot-grid absolute inset-0 pointer-events-none" />

          {/* Decorative rings */}
          <div className="ring-deco" style={{ width: 440, height: 440, bottom: -140, right: -140 }} />
          <div className="ring-deco" style={{ width: 260, height: 260, bottom: 80, right: 20 }} />
          <div className="ring-deco" style={{ width: 120, height: 120, top: 120, left: -40 }} />

          {/* Top: logo */}
          <div className="relative z-10 flex items-center gap-3">
            <div className="w-9 h-9 rounded-xl flex items-center justify-center shrink-0"
                 style={{ background: '#B32700' }}>
              <span className="material-symbols-outlined text-white" style={{ fontSize: 18, fontVariationSettings: "'FILL' 1" }}>
                location_on
              </span>
            </div>
            <span className="text-white font-bold text-lg tracking-tight" style={{ fontFamily: "'Plus Jakarta Sans', sans-serif" }}>
              Pede Aqui
            </span>
          </div>

          {/* Middle: headline */}
          <div className="relative z-10">
            <p className="text-xs font-semibold tracking-widest uppercase mb-5" style={{ color: '#B32700' }}>
              Backoffice Platform
            </p>
            <h1 className="text-white font-extrabold leading-tight mb-5"
                style={{ fontSize: 38, fontFamily: "'Plus Jakarta Sans', sans-serif" }}>
              A plataforma que<br />
              move entregas<br />
              <span style={{ color: '#FF6A3D' }}>em Moçambique.</span>
            </h1>
            <p className="text-sm leading-relaxed max-w-xs" style={{ color: 'rgba(255,255,255,0.48)' }}>
              Gestão centralizada de vendors, couriers, pedidos e finanças — em tempo real.
            </p>

            {/* Feature pills */}
            <div className="flex flex-wrap gap-2 mt-8">
              {['Multi-tenant', 'Tempo real', 'Relatórios', 'OTP seguro'].map(f => (
                <span key={f} className="px-3 py-1 rounded-full text-xs font-medium"
                      style={{ background: 'rgba(255,255,255,0.06)', color: 'rgba(255,255,255,0.55)', border: '1px solid rgba(255,255,255,0.10)' }}>
                  {f}
                </span>
              ))}
            </div>
          </div>

          {/* Bottom: stats */}
          <div className="relative z-10 grid grid-cols-3 gap-6 pt-8 border-t" style={{ borderColor: 'rgba(255,255,255,0.08)' }}>
            {[
              { value: '500+', label: 'Parceiros' },
              { value: '30min', label: 'Entrega média' },
              { value: '98%',  label: 'Satisfação' },
            ].map(s => (
              <div key={s.label}>
                <div className="text-xl font-bold text-white">{s.value}</div>
                <div className="text-xs mt-0.5" style={{ color: 'rgba(255,255,255,0.38)' }}>{s.label}</div>
              </div>
            ))}
          </div>
        </div>

        {/* ═══ RIGHT FORM PANEL ═══ */}
        <div className="flex-1 flex items-center justify-center bg-white px-6 py-12 sm:px-10 lg:px-16">
          <div className="w-full max-w-sm">

            {/* Mobile logo */}
            <div className="lg:hidden flex items-center gap-2 mb-10">
              <div className="w-8 h-8 rounded-lg flex items-center justify-center" style={{ background: '#B32700' }}>
                <span className="material-symbols-outlined text-white" style={{ fontSize: 16, fontVariationSettings: "'FILL' 1" }}>
                  location_on
                </span>
              </div>
              <span className="font-bold text-gray-900">Pede Aqui</span>
            </div>

            {/* Heading */}
            <div className="pa-fadeup mb-8">
              <h2 className="text-2xl font-bold text-gray-900" style={{ fontFamily: "'Plus Jakarta Sans', sans-serif" }}>
                Bem-vindo de volta
              </h2>
              <p className="text-sm text-gray-500 mt-1">
                Entre com as suas credenciais de administrador.
              </p>
            </div>

            <form onSubmit={handleSubmit} className="space-y-4">

              {/* Email */}
              <div className="pa-fadeup-2">
                <label className="block text-sm font-semibold text-gray-700 mb-1.5">Email</label>
                <div className="field-wrap">
                  <input
                    type="email"
                    value={email}
                    onChange={e => setEmail(e.target.value)}
                    required
                    autoComplete="email"
                    placeholder="admin@empresa.co.mz"
                    className="auth-input"
                    style={{ paddingLeft: 44 }}
                  />
                  <span className="field-icon material-symbols-outlined" style={{ fontSize: 18, fontVariationSettings: "'FILL' 0" }}>
                    mail
                  </span>
                </div>
              </div>

              {/* Password */}
              <div className="pa-fadeup-2">
                <label className="block text-sm font-semibold text-gray-700 mb-1.5">Palavra-passe</label>
                <div className="field-wrap">
                  <input
                    type={showPassword ? 'text' : 'password'}
                    value={password}
                    onChange={e => setPassword(e.target.value)}
                    required
                    autoComplete="current-password"
                    placeholder="••••••••"
                    className="auth-input"
                    style={{ paddingLeft: 44, paddingRight: 44 }}
                  />
                  <span className="field-icon material-symbols-outlined" style={{ fontSize: 18, fontVariationSettings: "'FILL' 0" }}>
                    lock
                  </span>
                  <button
                    type="button"
                    className="eye-btn"
                    onClick={() => setShowPassword(v => !v)}
                    tabIndex={-1}
                    aria-label={showPassword ? 'Ocultar palavra-passe' : 'Mostrar palavra-passe'}
                  >
                    <span className="material-symbols-outlined" style={{ fontSize: 18 }}>
                      {showPassword ? 'visibility_off' : 'visibility'}
                    </span>
                  </button>
                </div>
              </div>

              {/* Error */}
              {error && (
                <div className="pa-error flex items-start gap-2.5 rounded-xl px-4 py-3"
                     style={{ background: '#FEF2F2', border: '1px solid #FECACA' }}>
                  <span className="material-symbols-outlined text-red-500 shrink-0 mt-0.5" style={{ fontSize: 16, fontVariationSettings: "'FILL' 1" }}>
                    error
                  </span>
                  <p className="text-sm text-red-600">{error}</p>
                </div>
              )}

              {/* Submit */}
              <div className="pa-fadeup-3 pt-1">
                <button type="submit" disabled={loading} className="btn-auth">
                  {loading ? (
                    <>
                      <svg className="pa-spinner w-4 h-4" viewBox="0 0 24 24" fill="none">
                        <circle cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="3" strokeOpacity="0.3" />
                        <path d="M12 2a10 10 0 0 1 10 10" stroke="currentColor" strokeWidth="3" strokeLinecap="round" />
                      </svg>
                      A entrar…
                    </>
                  ) : 'Entrar'}
                </button>
              </div>
            </form>

            {/* Register link */}
            <p className="mt-6 text-center text-sm text-gray-500">
              Não tem conta?{' '}
              <a href="/register" className="font-semibold" style={{ color: '#B32700' }}>
                Registar empresa
              </a>
            </p>
          </div>
        </div>
      </div>
    </>
  );
}
