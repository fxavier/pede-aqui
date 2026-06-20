"use client";

import { useEffect, useState } from "react";
import { AppShell } from "@/components/layout/app-shell";
import { TableSkeleton } from "@/components/ui/loading-skeleton";
import { ErrorState } from "@/components/ui/error-state";
import { EmptyState } from "@/components/ui/empty-state";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Badge } from "@/components/ui/badge";
import { courierService } from "@/lib/api/services";
import { formatDate, cn } from "@/lib/utils";
import type { Courier, CourierDocument } from "@/lib/api/types";
import {
  CheckCircle, XCircle, Clock, User, Phone, Car,
  FileText, ChevronRight, Search, Truck,
} from "lucide-react";

type Tab = "PENDING" | "APPROVED" | "REJECTED" | "ALL";

const TABS: { id: Tab; label: string }[] = [
  { id: "PENDING", label: "Pendentes" },
  { id: "APPROVED", label: "Aprovados" },
  { id: "REJECTED", label: "Rejeitados" },
  { id: "ALL", label: "Todos" },
];

const DOC_TYPE_LABELS: Record<string, string> = {
  ID_CARD: "Bilhete de Identidade",
  DRIVING_LICENCE: "Carta de Condução",
  VEHICLE_REGISTRATION: "Registo do Veículo",
  OTHER: "Outro",
};

function VerificationBadge({ status }: { status: string }) {
  if (status === "APPROVED")
    return <Badge className="bg-green-100 text-green-800 border-green-200">Aprovado</Badge>;
  if (status === "REJECTED")
    return <Badge className="bg-red-100 text-red-800 border-red-200">Rejeitado</Badge>;
  return <Badge className="bg-yellow-100 text-yellow-800 border-yellow-200">Pendente</Badge>;
}

export default function CouriersPage() {
  const [couriers, setCouriers] = useState<Courier[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [activeTab, setActiveTab] = useState<Tab>("PENDING");
  const [searchQuery, setSearchQuery] = useState("");
  const [selected, setSelected] = useState<Courier | null>(null);
  const [documents, setDocuments] = useState<CourierDocument[]>([]);
  const [docsLoading, setDocsLoading] = useState(false);
  const [actionLoading, setActionLoading] = useState<"approve" | "reject" | null>(null);

  const fetchCouriers = async () => {
    setLoading(true);
    setError(null);
    try {
      setCouriers(await courierService.list());
    } catch {
      setError("Erro ao carregar estafetas. Tente novamente.");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { fetchCouriers(); }, []);

  async function selectCourier(courier: Courier) {
    setSelected(courier);
    setDocuments([]);
    setDocsLoading(true);
    try {
      setDocuments(await courierService.getDocuments(courier.id));
    } catch {
      // no docs or error — show empty
    } finally {
      setDocsLoading(false);
    }
  }

  async function handleApprove() {
    if (!selected) return;
    setActionLoading("approve");
    try {
      const updated = await courierService.approve(selected.id);
      setCouriers((prev) => prev.map((c) => (c.id === updated.id ? updated : c)));
      setSelected(updated);
    } catch {
      setError("Erro ao aprovar estafeta.");
    } finally {
      setActionLoading(null);
    }
  }

  async function handleReject() {
    if (!selected) return;
    setActionLoading("reject");
    try {
      const updated = await courierService.reject(selected.id);
      setCouriers((prev) => prev.map((c) => (c.id === updated.id ? updated : c)));
      setSelected(updated);
    } catch {
      setError("Erro ao rejeitar estafeta.");
    } finally {
      setActionLoading(null);
    }
  }

  const filtered = couriers.filter((c) => {
    const matchesTab = activeTab === "ALL" || c.verificationStatus === activeTab;
    const q = searchQuery.toLowerCase();
    const matchesSearch =
      !q ||
      (c.fullName ?? "").toLowerCase().includes(q) ||
      (c.phone ?? "").toLowerCase().includes(q) ||
      (c.nif ?? "").toLowerCase().includes(q) ||
      (c.vehiclePlate ?? "").toLowerCase().includes(q);
    return matchesTab && matchesSearch;
  });

  const counts = {
    PENDING: couriers.filter((c) => c.verificationStatus === "PENDING").length,
    APPROVED: couriers.filter((c) => c.verificationStatus === "APPROVED").length,
    REJECTED: couriers.filter((c) => c.verificationStatus === "REJECTED").length,
    ALL: couriers.length,
  };

  return (
    <AppShell>
      <main className="space-y-6 p-4 md:p-8">
        <div>
          <h1 className="font-headline-lg text-headline-lg text-on-surface">Estafetas</h1>
          <p className="mt-1 text-body-md text-on-surface-variant">
            Gestão e aprovação de estafetas pelo administrador.
          </p>
        </div>

        {error && <ErrorState message={error} onRetry={fetchCouriers} />}

        {/* Summary KPIs */}
        <div className="grid grid-cols-2 gap-4 sm:grid-cols-4">
          {[
            { label: "Pendentes", count: counts.PENDING, color: "text-yellow-600", bg: "bg-yellow-50" },
            { label: "Aprovados", count: counts.APPROVED, color: "text-green-600", bg: "bg-green-50" },
            { label: "Rejeitados", count: counts.REJECTED, color: "text-red-600", bg: "bg-red-50" },
            { label: "Total", count: counts.ALL, color: "text-on-surface", bg: "bg-surface-container" },
          ].map(({ label, count, color, bg }) => (
            <div key={label} className={cn("rounded-2xl p-4", bg)}>
              <p className="text-xs font-bold uppercase tracking-wider text-on-surface-variant">{label}</p>
              <p className={cn("mt-1 text-3xl font-bold", color)}>{count}</p>
            </div>
          ))}
        </div>

        <div className="flex flex-col gap-5 lg:flex-row">
          {/* Left: list */}
          <Card className="flex-1">
            <CardHeader>
              <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
                <CardTitle>Lista de Estafetas</CardTitle>
                <div className="relative sm:w-72">
                  <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-on-surface-variant" />
                  <Input
                    placeholder="Pesquisar..."
                    value={searchQuery}
                    onChange={(e) => setSearchQuery(e.target.value)}
                    className="pl-9"
                  />
                </div>
              </div>

              {/* Tabs */}
              <div className="flex gap-1 border-b border-outline-variant pt-2">
                {TABS.map((tab) => (
                  <button
                    key={tab.id}
                    onClick={() => { setActiveTab(tab.id); setSelected(null); }}
                    className={cn(
                      "relative pb-2 px-3 text-sm font-bold transition-colors",
                      activeTab === tab.id
                        ? "text-primary after:absolute after:bottom-0 after:left-0 after:right-0 after:h-0.5 after:bg-primary"
                        : "text-on-surface-variant hover:text-on-surface",
                    )}
                  >
                    {tab.label}
                    {counts[tab.id] > 0 && (
                      <span className={cn(
                        "ml-1.5 rounded-full px-1.5 py-0.5 text-xs",
                        tab.id === "PENDING" ? "bg-yellow-100 text-yellow-700" : "bg-surface-container text-on-surface-variant",
                      )}>
                        {counts[tab.id]}
                      </span>
                    )}
                  </button>
                ))}
              </div>
            </CardHeader>

            <CardContent>
              {loading ? (
                <TableSkeleton />
              ) : filtered.length === 0 ? (
                <EmptyState message="Nenhum estafeta encontrado." />
              ) : (
                <div className="space-y-2">
                  {filtered.map((courier) => (
                    <button
                      key={courier.id}
                      onClick={() => selectCourier(courier)}
                      className={cn(
                        "flex w-full items-center gap-3 rounded-xl border p-3 text-left transition-all",
                        selected?.id === courier.id
                          ? "border-primary bg-primary-fixed"
                          : "border-outline-variant hover:bg-surface-container",
                      )}
                    >
                      <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-surface-container-high text-sm font-bold text-on-surface-variant">
                        {(courier.fullName ?? "?").charAt(0).toUpperCase()}
                      </div>
                      <div className="min-w-0 flex-1">
                        <p className="truncate text-sm font-bold text-on-surface">
                          {courier.fullName ?? "Sem nome"}
                        </p>
                        <p className="text-xs text-on-surface-variant">
                          {courier.vehicleType ?? "—"} {courier.vehiclePlate ? `· ${courier.vehiclePlate}` : ""}
                        </p>
                      </div>
                      <VerificationBadge status={courier.verificationStatus} />
                      <ChevronRight className="h-4 w-4 shrink-0 text-on-surface-variant" />
                    </button>
                  ))}
                </div>
              )}
            </CardContent>
          </Card>

          {/* Right: detail panel */}
          <div className="w-full lg:w-96">
            {selected ? (
              <Card className="sticky top-24">
                <CardHeader>
                  <div className="flex items-center justify-between">
                    <CardTitle className="text-base">Perfil do Estafeta</CardTitle>
                    <VerificationBadge status={selected.verificationStatus} />
                  </div>
                </CardHeader>
                <CardContent className="space-y-5">
                  {/* Identity */}
                  <div className="space-y-2">
                    <p className="text-xs font-bold uppercase tracking-wider text-on-surface-variant">Identidade</p>
                    <div className="space-y-1 text-sm">
                      <div className="flex items-center gap-2">
                        <User className="h-4 w-4 text-on-surface-variant" />
                        <span className="font-bold">{selected.fullName ?? "—"}</span>
                      </div>
                      <div className="flex items-center gap-2">
                        <Phone className="h-4 w-4 text-on-surface-variant" />
                        <span>{selected.phone ?? "—"}</span>
                      </div>
                      <p className="pl-6 text-on-surface-variant">NUIT: {selected.nif ?? "—"}</p>
                    </div>
                  </div>

                  {/* Vehicle */}
                  <div className="space-y-2">
                    <p className="text-xs font-bold uppercase tracking-wider text-on-surface-variant">Veículo</p>
                    <div className="space-y-1 text-sm">
                      <div className="flex items-center gap-2">
                        <Car className="h-4 w-4 text-on-surface-variant" />
                        <span>{selected.vehicleType ?? "—"}</span>
                      </div>
                      <p className="pl-6 text-on-surface-variant">Matrícula: {selected.vehiclePlate ?? "—"}</p>
                    </div>
                  </div>

                  {/* Documents */}
                  <div className="space-y-2">
                    <p className="text-xs font-bold uppercase tracking-wider text-on-surface-variant">Documentos</p>
                    {docsLoading ? (
                      <p className="text-sm text-on-surface-variant">A carregar...</p>
                    ) : documents.length === 0 ? (
                      <p className="text-sm text-on-surface-variant">Nenhum documento submetido.</p>
                    ) : (
                      <div className="space-y-2">
                        {documents.map((doc) => (
                          <div key={doc.id} className="flex items-center gap-2 rounded-xl border border-outline-variant p-2">
                            <FileText className="h-4 w-4 shrink-0 text-on-surface-variant" />
                            <div className="min-w-0 flex-1">
                              <p className="truncate text-sm font-bold text-on-surface">
                                {DOC_TYPE_LABELS[doc.documentType] ?? doc.documentType}
                              </p>
                              <p className="text-xs text-on-surface-variant">{formatDate(doc.createdAt)}</p>
                            </div>
                            <Badge variant="outline" className="shrink-0 text-xs">{doc.status}</Badge>
                          </div>
                        ))}
                      </div>
                    )}
                  </div>

                  {/* Approval actions — only for PENDING */}
                  {selected.verificationStatus === "PENDING" && (
                    <div className="flex gap-2 pt-2">
                      <Button
                        className="flex-1 bg-green-600 hover:bg-green-700 text-white"
                        onClick={handleApprove}
                        disabled={actionLoading !== null}
                      >
                        <CheckCircle className="mr-2 h-4 w-4" />
                        {actionLoading === "approve" ? "A aprovar..." : "Aprovar"}
                      </Button>
                      <Button
                        variant="outline"
                        className="flex-1 border-red-300 text-red-600 hover:bg-red-50"
                        onClick={handleReject}
                        disabled={actionLoading !== null}
                      >
                        <XCircle className="mr-2 h-4 w-4" />
                        {actionLoading === "reject" ? "A rejeitar..." : "Rejeitar"}
                      </Button>
                    </div>
                  )}

                  {selected.verificationStatus === "APPROVED" && (
                    <div className="flex items-center gap-2 rounded-xl bg-green-50 p-3 text-sm text-green-700">
                      <CheckCircle className="h-4 w-4 shrink-0" />
                      Estafeta aprovado. Pode ficar online.
                    </div>
                  )}

                  {selected.verificationStatus === "REJECTED" && (
                    <div className="flex items-center gap-2 rounded-xl bg-red-50 p-3 text-sm text-red-700">
                      <XCircle className="h-4 w-4 shrink-0" />
                      Registo rejeitado.
                    </div>
                  )}
                </CardContent>
              </Card>
            ) : (
              <Card className="flex h-48 items-center justify-center">
                <div className="text-center text-on-surface-variant">
                  <Truck className="mx-auto mb-2 h-8 w-8 opacity-40" />
                  <p className="text-sm">Selecione um estafeta para ver o perfil</p>
                </div>
              </Card>
            )}
          </div>
        </div>
      </main>
    </AppShell>
  );
}
