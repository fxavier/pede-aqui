import Link from "next/link";

/** Shown (with HTTP 403) by the middleware when the signed-in user's roles do not grant the requested route. */
export default function ForbiddenPage() {
  return (
    <main className="flex min-h-screen items-center justify-center bg-surface px-6">
      <div className="max-w-md text-center">
        <p className="text-sm font-bold uppercase tracking-widest text-primary">403</p>
        <h1 className="mt-2 text-2xl font-bold text-on-surface">Acesso negado</h1>
        <p className="mt-3 text-sm text-on-surface-variant">
          O seu perfil não tem permissão para abrir esta página. Se precisar de acesso, contacte o administrador.
        </p>
        <Link href="/" className="mt-6 inline-block rounded-xl bg-primary px-5 py-2.5 text-sm font-semibold text-on-primary">
          Voltar ao painel
        </Link>
      </div>
    </main>
  );
}
