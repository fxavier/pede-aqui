"use client";

import { BackofficeShell } from "../../components/backoffice-shell";
import { useEffect, useState } from "react";
import { safeApiGet, type AsyncState } from "../../services/api";
import type { DispatchJob } from "../../services/types";
import { OperationsDashboard } from "../../features/operations/operations-dashboard";

export default function OperationsPage() {
  const [state, setState] = useState<AsyncState<DispatchJob[]>>({ kind: "loading" });

  useEffect(() => {
    const token = process.env.NEXT_PUBLIC_APP_TOKEN ?? "dev-token";
    safeApiGet<DispatchJob[]>("/dispatch-jobs", token).then(setState);
  }, []);

  return (
    <BackofficeShell title="Operacoes" subtitle="Despacho, atribuicoes e reassinacoes de entrega">
      <OperationsDashboard state={state} />
    </BackofficeShell>
  );
}
