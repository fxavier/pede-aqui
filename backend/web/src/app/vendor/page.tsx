"use client";

import { BackofficeShell } from "../../components/backoffice-shell";
import { useEffect, useState } from "react";
import { safeApiGet, type AsyncState } from "../../services/api";
import type { Vendor } from "../../services/types";
import { VendorDashboard } from "../../features/vendor/vendor-dashboard";

export default function VendorPage() {
  const [state, setState] = useState<AsyncState<Vendor[]>>({ kind: "loading" });

  useEffect(() => {
    const token = process.env.NEXT_PUBLIC_APP_TOKEN ?? "dev-token";
    safeApiGet<Vendor[]>("/vendors?available=true", token).then(setState);
  }, []);

  return (
    <BackofficeShell title="Portal do Fornecedor" subtitle="Gestao de loja, catalogo e desempenho">
      <VendorDashboard state={state} />
    </BackofficeShell>
  );
}
