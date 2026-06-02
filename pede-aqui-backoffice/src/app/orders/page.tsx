"use client";

import { useEffect, useState, useMemo } from "react";
import { AppShell } from "@/components/layout/app-shell";
import { orderService } from "@/lib/api/services";
import type { Order } from "@/lib/api/types";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { ShoppingBag, TrendingUp, Clock, CheckCircle, AlertCircle } from "lucide-react";

const STATUS_LABELS: Record<string, string> = {
  PAYMENT_PENDING: "Pag. Pendente",
  PAYMENT_CONFIRMED: "Pag. Confirmado",
  ACCEPTED_BY_VENDOR: "Aceite",
  REJECTED_BY_VENDOR: "Rejeitado",
  PREPARING: "Em Preparação",
  READY_FOR_PICKUP: "Pronto p/ Recolha",
  DISPATCH_PENDING: "Desp. Pendente",
  ASSIGNED_TO_COURIER: "Atribuído",
  PICKED_UP: "Recolhido",
  DELIVERING: "Em Entrega",
  DELIVERED: "Entregue",
  CANCELLED: "Cancelado",
  REFUND_PENDING: "Reemb. Pendente",
  REFUNDED: "Reembolsado",
};

const STATUS_VARIANT: Record<string, "default" | "secondary" | "destructive" | "outline"> = {
  PAYMENT_PENDING: "secondary",
  PAYMENT_CONFIRMED: "outline",
  ACCEPTED_BY_VENDOR: "default",
  REJECTED_BY_VENDOR: "destructive",
  PREPARING: "default",
  READY_FOR_PICKUP: "default",
  DISPATCH_PENDING: "secondary",
  ASSIGNED_TO_COURIER: "default",
  PICKED_UP: "default",
  DELIVERING: "default",
  DELIVERED: "secondary",
  CANCELLED: "destructive",
  REFUND_PENDING: "secondary",
  REFUNDED: "secondary",
};

const ACTIVE_STATUSES = new Set([
  "PAYMENT_CONFIRMED", "ACCEPTED_BY_VENDOR", "PREPARING",
  "READY_FOR_PICKUP", "DISPATCH_PENDING", "ASSIGNED_TO_COURIER",
  "PICKED_UP", "DELIVERING",
]);
const PENDING_STATUSES = new Set(["PAYMENT_PENDING", "PAYMENT_CONFIRMED"]);
const DONE_STATUSES = new Set(["DELIVERED", "CANCELLED", "REJECTED_BY_VENDOR", "REFUNDED"]);

function fmt(v: number) {
  return new Intl.NumberFormat("pt-MZ", { style: "currency", currency: "MZN" }).format(v);
}

function fmtDate(iso: string | null) {
  if (!iso) return "—";
  return new Date(iso).toLocaleString("pt-MZ", { dateStyle: "short", timeStyle: "short" });
}

export default function OrdersPage() {
  const [orders, setOrders] = useState<Order[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [selected, setSelected] = useState<Order | null>(null);
  const [actionLoading, setActionLoading] = useState(false);
  const [rejectReason, setRejectReason] = useState("");
  const [showReject, setShowReject] = useState(false);

  useEffect(() => {
    orderService.list()
      .then(setOrders)
      .catch(() => setError("Erro ao carregar encomendas"))
      .finally(() => setLoading(false));
  }, []);

  const stats = useMemo(() => {
    const total = orders.length;
    const revenue = orders.filter(o => o.status === "DELIVERED").reduce((s, o) => s + o.total, 0);
    const pending = orders.filter(o => PENDING_STATUSES.has(o.status)).length;
    const active = orders.filter(o => ACTIVE_STATUSES.has(o.status)).length;
    return { total, revenue, pending, active };
  }, [orders]);

  const allOrders = orders;
  const pendingOrders = orders.filter(o => PENDING_STATUSES.has(o.status));
  const activeOrders = orders.filter(o => ACTIVE_STATUSES.has(o.status));
  const doneOrders = orders.filter(o => DONE_STATUSES.has(o.status));

  function updateOrder(updated: Order) {
    setOrders(prev => prev.map(o => o.id === updated.id ? updated : o));
    setSelected(updated);
  }

  async function handleAction(action: () => Promise<Order>) {
    setActionLoading(true);
    try {
      const updated = await action();
      updateOrder(updated);
    } catch {
      // keep panel open so user sees the error
    } finally {
      setActionLoading(false);
    }
  }

  async function handleReject() {
    if (!selected || !rejectReason.trim()) return;
    await handleAction(() => orderService.reject(selected.id, rejectReason));
    setShowReject(false);
    setRejectReason("");
  }

  function OrderTable({ rows }: { rows: Order[] }) {
    return (
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>Referência</TableHead>
            <TableHead>Cliente</TableHead>
            <TableHead>Fornecedor</TableHead>
            <TableHead>Estado</TableHead>
            <TableHead className="text-right">Total</TableHead>
            <TableHead>Data</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {rows.length === 0 && (
            <TableRow>
              <TableCell colSpan={6} className="text-center text-muted-foreground py-8">Nenhuma encomenda</TableCell>
            </TableRow>
          )}
          {rows.map(order => (
            <TableRow
              key={order.id}
              className="cursor-pointer hover:bg-muted/50"
              onClick={() => { setSelected(order); setShowReject(false); setRejectReason(""); }}
            >
              <TableCell className="font-mono text-sm">{order.reference}</TableCell>
              <TableCell>{order.customerName ?? "—"}</TableCell>
              <TableCell>{order.vendorName ?? "—"}</TableCell>
              <TableCell>
                <Badge variant={STATUS_VARIANT[order.status] ?? "outline"}>
                  {STATUS_LABELS[order.status] ?? order.status}
                </Badge>
              </TableCell>
              <TableCell className="text-right font-medium">{fmt(order.total)}</TableCell>
              <TableCell className="text-muted-foreground text-sm">{fmtDate(order.createdAt)}</TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    );
  }

  if (loading) return <div className="p-8 text-muted-foreground">A carregar encomendas…</div>;
  if (error) return <div className="p-8 text-destructive">{error}</div>;

  return (
    <AppShell>
      <main className="space-y-6 p-4 md:p-8">
      <div>
        <h1 className="text-2xl font-bold">Encomendas</h1>
        <p className="text-muted-foreground">Gestão e acompanhamento de encomendas dos clientes.</p>
      </div>

      {/* KPI row */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
        <Card>
          <CardHeader className="pb-2 flex flex-row items-center justify-between">
            <CardTitle className="text-sm font-medium text-muted-foreground">Total</CardTitle>
            <ShoppingBag className="h-4 w-4 text-muted-foreground" />
          </CardHeader>
          <CardContent><div className="text-2xl font-bold">{stats.total}</div></CardContent>
        </Card>
        <Card>
          <CardHeader className="pb-2 flex flex-row items-center justify-between">
            <CardTitle className="text-sm font-medium text-muted-foreground">Receita Entregue</CardTitle>
            <TrendingUp className="h-4 w-4 text-muted-foreground" />
          </CardHeader>
          <CardContent><div className="text-2xl font-bold">{fmt(stats.revenue)}</div></CardContent>
        </Card>
        <Card>
          <CardHeader className="pb-2 flex flex-row items-center justify-between">
            <CardTitle className="text-sm font-medium text-muted-foreground">Pendentes</CardTitle>
            <Clock className="h-4 w-4 text-muted-foreground" />
          </CardHeader>
          <CardContent><div className="text-2xl font-bold">{stats.pending}</div></CardContent>
        </Card>
        <Card>
          <CardHeader className="pb-2 flex flex-row items-center justify-between">
            <CardTitle className="text-sm font-medium text-muted-foreground">Em Curso</CardTitle>
            <AlertCircle className="h-4 w-4 text-muted-foreground" />
          </CardHeader>
          <CardContent><div className="text-2xl font-bold">{stats.active}</div></CardContent>
        </Card>
      </div>

      {/* Orders table + detail panel */}
      <div className="flex gap-4">
        <div className="flex-1 min-w-0">
          <Tabs defaultValue="all">
            <TabsList>
              <TabsTrigger value="all">Todas ({allOrders.length})</TabsTrigger>
              <TabsTrigger value="pending">Pendentes ({pendingOrders.length})</TabsTrigger>
              <TabsTrigger value="active">Em Curso ({activeOrders.length})</TabsTrigger>
              <TabsTrigger value="done">Concluídas ({doneOrders.length})</TabsTrigger>
            </TabsList>
            <TabsContent value="all"><Card><CardContent className="p-0"><OrderTable rows={allOrders} /></CardContent></Card></TabsContent>
            <TabsContent value="pending"><Card><CardContent className="p-0"><OrderTable rows={pendingOrders} /></CardContent></Card></TabsContent>
            <TabsContent value="active"><Card><CardContent className="p-0"><OrderTable rows={activeOrders} /></CardContent></Card></TabsContent>
            <TabsContent value="done"><Card><CardContent className="p-0"><OrderTable rows={doneOrders} /></CardContent></Card></TabsContent>
          </Tabs>
        </div>

        {/* Detail panel */}
        {selected && (
          <div className="w-80 shrink-0">
            <Card className="sticky top-6">
              <CardHeader className="pb-3">
                <div className="flex items-center justify-between">
                  <CardTitle className="text-sm font-mono">{selected.reference}</CardTitle>
                  <Button variant="ghost" size="sm" onClick={() => setSelected(null)}>✕</Button>
                </div>
                <Badge variant={STATUS_VARIANT[selected.status] ?? "outline"} className="w-fit">
                  {STATUS_LABELS[selected.status] ?? selected.status}
                </Badge>
              </CardHeader>
              <CardContent className="space-y-4 text-sm">
                <div className="grid grid-cols-2 gap-2 text-muted-foreground">
                  <span>Cliente</span><span className="text-foreground font-medium">{selected.customerName ?? "—"}</span>
                  <span>Fornecedor</span><span className="text-foreground font-medium">{selected.vendorName ?? "—"}</span>
                  <span>Data</span><span className="text-foreground">{fmtDate(selected.createdAt)}</span>
                  <span>Total</span><span className="text-foreground font-bold">{fmt(selected.total)}</span>
                  {selected.deliveryCode && <><span>Cód. Entrega</span><span className="text-foreground font-mono">{selected.deliveryCode}</span></>}
                </div>

                {selected.items && selected.items.length > 0 && (
                  <div>
                    <p className="font-medium mb-2">Artigos</p>
                    <div className="space-y-1">
                      {selected.items.map(item => (
                        <div key={item.id} className="flex justify-between text-xs">
                          <span className="truncate mr-2">{item.quantity}× {item.productName} – {item.skuName}</span>
                          <span className="shrink-0 text-muted-foreground">{fmt(item.lineTotal)}</span>
                        </div>
                      ))}
                    </div>
                  </div>
                )}

                {/* Fulfillment actions */}
                <div className="space-y-2 pt-2 border-t">
                  {selected.status === "PAYMENT_CONFIRMED" && (
                    <>
                      <Button
                        size="sm"
                        className="w-full"
                        disabled={actionLoading}
                        onClick={() => handleAction(() => orderService.accept(selected.id))}
                      >
                        <CheckCircle className="h-4 w-4 mr-1" /> Aceitar
                      </Button>
                      <Button
                        size="sm"
                        variant="destructive"
                        className="w-full"
                        disabled={actionLoading}
                        onClick={() => setShowReject(v => !v)}
                      >
                        Rejeitar
                      </Button>
                      {showReject && (
                        <div className="space-y-1">
                          <input
                            className="w-full border rounded px-2 py-1 text-xs"
                            placeholder="Motivo da rejeição"
                            value={rejectReason}
                            onChange={e => setRejectReason(e.target.value)}
                          />
                          <Button size="sm" variant="destructive" className="w-full" disabled={actionLoading || !rejectReason.trim()} onClick={handleReject}>
                            Confirmar Rejeição
                          </Button>
                        </div>
                      )}
                    </>
                  )}
                  {selected.status === "ACCEPTED_BY_VENDOR" && (
                    <Button size="sm" className="w-full" disabled={actionLoading} onClick={() => handleAction(() => orderService.markPreparing(selected.id))}>
                      Marcar Em Preparação
                    </Button>
                  )}
                  {selected.status === "PREPARING" && (
                    <Button size="sm" className="w-full" disabled={actionLoading} onClick={() => handleAction(() => orderService.markReadyForPickup(selected.id))}>
                      Pronto para Recolha
                    </Button>
                  )}
                  {DONE_STATUSES.has(selected.status) && (
                    <p className="text-xs text-muted-foreground text-center">Encomenda concluída</p>
                  )}
                </div>
              </CardContent>
            </Card>
          </div>
        )}
      </div>
      </main>
    </AppShell>
  );
}
