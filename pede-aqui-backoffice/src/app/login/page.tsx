'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { useAppDispatch } from '@/store/hooks';
import { setUser } from '@/store/slices/auth-slice';

export default function LoginPage() {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
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

      const tokenResponse = await fetch(`${keycloakUrl}/realms/${keycloakRealm}/protocol/openid-connect/token`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/x-www-form-urlencoded',
          'Accept': 'application/json',
        },
        body: `grant_type=password&client_id=${keycloakClientId}&username=${encodeURIComponent(email)}&password=${encodeURIComponent(password)}`,
      });

      if (!tokenResponse.ok) {
        throw new Error('Credenciais inválidas');
      }

      const tokenData = await tokenResponse.json();
      const accessToken = tokenData.access_token;

      sessionStorage.setItem('auth_token', accessToken);

      const profileResponse = await fetch(`${process.env.NEXT_PUBLIC_API_BASE_URL}/me`, {
        headers: {
          'Authorization': `Bearer ${accessToken}`,
          'Accept': 'application/json',
        },
      });

      if (!profileResponse.ok) {
        throw new Error('Erro ao obter perfil do utilizador');
      }

      const profileData = await profileResponse.json();
      const tenantId: string | null = profileData.tenantId ?? null;
      if (tenantId) {
        sessionStorage.setItem('tenant_id', tenantId);
      } else {
        sessionStorage.removeItem('tenant_id');
      }

      dispatch(setUser({
        name: profileData.displayName || profileData.name,
        role: profileData.roles?.[0] || 'Admin',
        tenant: 'Pede Aqui',
        tenantId,
        email: profileData.email,
        token: accessToken,
      }));

      // Redirect to dashboard
      router.push('/');
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : 'Credenciais inválidas. Verifique o email e a palavra-passe.';
      setError(message);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen bg-gray-50 flex flex-col justify-center py-12 sm:px-6 lg:px-8">
      <div className="sm:mx-auto sm:w-full sm:max-w-md">
        <h1 className="text-center text-3xl font-extrabold text-gray-900">
          Pede Aqui Backoffice
        </h1>
        <p className="mt-2 text-center text-sm text-gray-600">
          Entre com as suas credenciais de administrador.
        </p>
      </div>

      <div className="mt-8 sm:mx-auto sm:w-full sm:max-w-md">
        <div className="bg-white py-8 px-4 shadow sm:rounded-lg sm:px-10">
          <form className="space-y-6" onSubmit={handleSubmit}>
            <div>
              <label htmlFor="email" className="block text-sm font-medium text-gray-700">
                Email
              </label>
              <div className="mt-1">
                <input
                  id="email"
                  name="email"
                  type="email"
                  autoComplete="email"
                  required
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  className="appearance-none block w-full px-3 py-2 border border-gray-300 rounded-md placeholder-gray-400 focus:outline-none focus:ring-indigo-500 focus:border-indigo-500 sm:text-sm"
                />
              </div>
            </div>

            <div>
              <label htmlFor="password" className="block text-sm font-medium text-gray-700">
                Palavra-passe
              </label>
              <div className="mt-1">
                <input
                  id="password"
                  name="password"
                  type="password"
                  autoComplete="current-password"
                  required
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  className="appearance-none block w-full px-3 py-2 border border-gray-300 rounded-md placeholder-gray-400 focus:outline-none focus:ring-indigo-500 focus:border-indigo-500 sm:text-sm"
                />
              </div>
            </div>

            {error && (
              <div className="text-red-600 text-sm text-center">
                {error}
              </div>
            )}

            <div>
              <button
                type="submit"
                disabled={loading}
                className="w-full flex justify-center py-2 px-4 border border-transparent rounded-md shadow-sm text-sm font-medium text-white bg-indigo-600 hover:bg-indigo-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-indigo-500 disabled:opacity-50"
              >
                {loading ? 'A entrar...' : 'Entrar'}
              </button>
            </div>
          </form>
        </div>
      </div>
    </div>
  );
}
