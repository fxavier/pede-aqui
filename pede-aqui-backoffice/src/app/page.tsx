import Link from "next/link";
import { ArrowRight, CheckCircle2, DatabaseZap, Gauge, Layers3, ShieldCheck } from "lucide-react";
import { AppShell } from "@/components/layout/app-shell";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { importedScreens, getScreenGroups } from "@/features/screens/generated-screens";

const groups = getScreenGroups();

const stats = [
  { label: "Ecras importados", value: importedScreens.length, icon: Layers3 },
  { label: "Modulos preparados", value: Object.keys(groups).length, icon: Gauge },
  { label: "Recursos mock de API", value: importedScreens.length, icon: DatabaseZap },
  { label: "Areas prontas para seguranca", value: 5, icon: ShieldCheck },
];

export default function HomePage() {
  return (
    <AppShell>
      <main className="space-y-8 p-4 md:p-8">
        <section className="relative overflow-hidden rounded-[2rem] border border-outline-variant bg-white p-8 shadow-soft">
          <div className="absolute right-0 top-0 h-52 w-52 rounded-full bg-primary-fixed blur-3xl" />
          <div className="relative max-w-4xl">
            <Badge variant="success">Prototipo orientado a producao</Badge>
            <h1 className="mt-5 font-display text-4xl text-on-surface md:text-5xl">Centro de Comando do Backoffice Pede Aqui</h1>
            <p className="mt-4 max-w-2xl text-lg text-on-surface-variant">
              Gerado a partir dos ecras de design carregados com Next.js, TypeScript, Tailwind CSS, componentes estilo shadcn, Redux Toolkit e React Query. A interface usa dados mock e esta preparada para integracao com API.
            </p>
            <div className="mt-6 flex flex-wrap gap-3">
              <Button asChild size="lg"><Link href="/screens">Abrir todos os ecras <ArrowRight className="h-4 w-4" /></Link></Button>
              <Button asChild variant="outline" size="lg"><Link href="/screens?group=Vendor%20Portal">Portal do Vendedor</Link></Button>
            </div>
          </div>
        </section>

        <section className="grid grid-cols-1 gap-5 md:grid-cols-2 xl:grid-cols-4">
          {stats.map((stat) => {
            const Icon = stat.icon;
            return (
              <Card key={stat.label} className="animate-fade-in">
                <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                  <CardDescription>{stat.label}</CardDescription>
                  <Icon className="h-5 w-5 text-primary" />
                </CardHeader>
                <CardContent>
                  <div className="font-display text-3xl text-on-surface">{stat.value}</div>
                  <p className="mt-1 flex items-center gap-1 text-xs text-secondary"><CheckCircle2 className="h-3.5 w-3.5" /> Pronto para iteracao</p>
                </CardContent>
              </Card>
            );
          })}
        </section>

        <section className="grid grid-cols-1 gap-5 lg:grid-cols-2">
          {Object.entries(groups).map(([group, screens]) => (
            <Card key={group} className="overflow-hidden">
              <CardHeader>
                <div className="flex items-center justify-between gap-3">
                  <div>
                    <CardTitle>{group}</CardTitle>
                    <CardDescription>{screens.length} ecras importados</CardDescription>
                  </div>
                  <Button asChild variant="outline" size="sm"><Link href={`/screens?group=${encodeURIComponent(group)}`}>Ver</Link></Button>
                </div>
              </CardHeader>
              <CardContent>
                <div className="space-y-2">
                  {screens.slice(0, 5).map((screen) => (
                    <Link key={screen.slug} href={screen.route} className="flex items-center justify-between rounded-xl bg-surface-container-low px-4 py-3 text-sm font-bold transition-colors hover:bg-surface-container">
                      <span>{screen.title}</span>
                      <ArrowRight className="h-4 w-4 text-primary" />
                    </Link>
                  ))}
                </div>
              </CardContent>
            </Card>
          ))}
        </section>
      </main>
    </AppShell>
  );
}
