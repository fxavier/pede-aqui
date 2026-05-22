"use client";

import { BackofficeShell } from "../../components/backoffice-shell";
import { useEffect, useState } from "react";
import { safeApiGet, type AsyncState } from "../../services/api";
import type { DashboardAdmin } from "../../services/types";
import { AdminDashboard } from "../../features/admin/admin-dashboard";

export default function AdminPage() {
  const [state, setState] = useState<AsyncState<DashboardAdmin>>({ kind: "loading" });

  useEffect(() => {
    const token = process.env.NEXT_PUBLIC_APP_TOKEN ?? "dev-token";
    safeApiGet<DashboardAdmin>("/dashboards/admin", token).then(setState);
  }, []);

  return (
    <BackofficeShell title="Admin Central" subtitle="Visao operacional do marketplace em Mocambique">
      <AdminDashboard state={state} />
    </BackofficeShell>
  );
}
