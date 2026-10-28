import { Suspense } from "react";
import { AppShell } from "@/components/layout/app-shell";
import { ScreenList } from "@/features/screens/screen-list";

export default function ScreensPage() {
  return (
    <AppShell>
      <main className="space-y-6 p-4 md:p-8">
        <div>
          <h1 className="font-headline-lg text-headline-lg text-on-surface">Ecras Importados</h1>
          <p className="mt-2 max-w-2xl text-on-surface-variant">
            Todos os ecras do ZIP estao disponiveis como rotas navegaveis. Os templates visuais importados mantem-se proximos do design original, enquanto a shell da aplicacao oferece navegacao moderna e preparacao para APIs.
          </p>
        </div>
        <Suspense fallback={<div className="rounded-2xl bg-white p-6 shadow-card">A carregar...</div>}>
          <ScreenList />
        </Suspense>
      </main>
    </AppShell>
  );
}
