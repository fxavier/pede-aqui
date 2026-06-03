import Link from "next/link";

export default function HomePage() {
  return (
    <main className="page-shell">
      <div style={{ backgroundColor: '#FFA500', padding: '12px', textAlign: 'center', fontWeight: 'bold', color: '#333', marginBottom: '20px' }}>
        ⚠️ PROTÓTIPO INTERNO — Esta aplicação não tem autenticação real. Não usar em produção.
      </div>
      <section className="panel">
        <p className="eyebrow">Pede Aqui</p>
        <h1>Plataforma de Entregas - Mocambique</h1>
        <p>Backoffice, app do cliente e app do estafeta alinhados com o backend MVP.</p>
        <div className="home-links">
          <Link href="/admin">Admin</Link>
          <Link href="/vendor">Fornecedor</Link>
          <Link href="/operations">Operacoes</Link>
          <Link href="/finance">Financeiro</Link>
          <Link href="/support">Suporte</Link>
        </div>
      </section>
    </main>
  );
}
