"use client";

import { BackofficeShell } from "../../components/backoffice-shell";
import { useEffect, useState } from "react";
import { safeApiGet, type AsyncState } from "../../services/api";
import type { SupportTicketItem } from "../../services/types";
import { SupportDashboard } from "../../features/support/support-dashboard";

export default function SupportPage() {
  const [state, setState] = useState<AsyncState<SupportTicketItem[]>>({ kind: "loading" });

  useEffect(() => {
    const token = process.env.NEXT_PUBLIC_APP_TOKEN ?? "dev-token";
    safeApiGet<SupportTicketItem[]>("/support/tickets", token).then(setState);
  }, []);

  return (
    <BackofficeShell title="Suporte" subtitle="Tickets, classificacao e resolucao com notas internas protegidas">
      <SupportDashboard state={state} viewer="support" />
    </BackofficeShell>
  );
}
