import Link from "next/link";
import type { ReactNode } from "react";

const links = [
  { href: "/admin", label: "Admin Central" },
  { href: "/vendor", label: "Portal do Fornecedor" },
  { href: "/operations", label: "Operacoes" },
  { href: "/finance", label: "Financeiro" },
  { href: "/support", label: "Suporte" }
];

export function BackofficeShell({ title, subtitle, children }: { title: string; subtitle: string; children: ReactNode }) {
  return (
    <main className="bo-layout">
      <div style={{ backgroundColor: '#FFA500', padding: '12px', textAlign: 'center', fontWeight: 'bold', color: '#333', position: 'fixed', top: '0', left: '0', right: '0', zIndex: 1000 }}>
        ⚠️ PROTÓTIPO INTERNO — Esta aplicação não tem autenticação real. Não usar em produção.
      </div>
      <div style={{ paddingTop: '50px' }}>
        <aside className="bo-sidebar">
        <h1>Pede Aqui</h1>
        <p>Backoffice Mocambique</p>
        <nav>
          {links.map((link) => (
            <Link key={link.href} href={link.href} className="bo-link">
              {link.label}
            </Link>
          ))}
        </nav>
      </aside>
      <section className="bo-content">
        <header className="bo-header">
          <h2>{title}</h2>
          <p>{subtitle}</p>
        </header>
        <div className="bo-grid">{children}</div>
      </section>
      </div>
    </main>
  );
}

export function MetricCard({ label, value }: { label: string; value: string }) {
  return (
    <article className="metric-card">
      <p>{label}</p>
      <strong>{value}</strong>
    </article>
  );
}

export function StateCard({ title, detail }: { title: string; detail: string }) {
  return (
    <article className="state-card">
      <h3>{title}</h3>
      <p>{detail}</p>
    </article>
  );
}
