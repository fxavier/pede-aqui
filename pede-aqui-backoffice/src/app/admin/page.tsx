"use client";

import { useEffect, useState } from "react";
import { AppShell } from "@/components/layout/app-shell";
import { KpiCard } from "@/components/ui/kpi-card";
import { StatusBadge } from "@/components/ui/status-badge";
import { TableSkeleton } from "@/components/ui/loading-skeleton";
import { ErrorState } from "@/components/ui/error-state";
import { EmptyState } from "@/components/ui/empty-state";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { dashboardService, orderService } from "@/lib/api/services";
import { formatCurrency, formatDate, orderStatusLabels } from "@/lib/utils";
import type { AdminDashboard, Order } from "@/lib/api/types";
import { Building2, CreditCard, Package, TrendingDown, TrendingUp, Truck, Tag, Settings } from "lucide-react";
import Link from "next/link";


function StatusChart({ data }: { data: Record<string, number> }) {
  const colorMap: Record<string, string> = {
    PENDING: "bg-yellow-400",
    ACCEPTED_BY_VENDOR: "bg-green-400",
    PREPARING: "bg-blue-400",
    READY_FOR_PICKUP: "bg-emerald-400",
    DELIVERING: "bg-indigo-400",
    DELIVERED: "bg-secondary",
    CANCELLED: "bg-red-400",
  };
  const total = Object.values(data).reduce((a, b) => a + b, 0);
  if (total === 0) return null;

  return (
    <div className="flex h-3 overflow-hidden rounded-full bg-surface-container-high">
      {Object.entries(data).map(([status, count]) => (
        <div
          key={status}
          className={colorMap[status] ?? "bg-surface-container-high"}
          style={{ width: `${(count / total) * 100}%` }}
          title={`${orderStatusLabels[status as keyof typeof orderStatusLabels] ?? status}: ${count}`}
        />
      ))}
    </div>
  );
}

export default function AdminPage() {
  const [dashboard, setDashboard] = useState<AdminDashboard | null>(null);
  const [orders, setOrders] = useState<Order[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const fetchData = async () => {
    setLoading(true);
    setError(null);
    try {
      const [dashData, ordersData] = await Promise.all([
        dashboardService.getAdmin(),
        orderService.list(),
      ]);
      setDashboard(dashData);
      setOrders(ordersData);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Erro ao carregar dados");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { fetchData(); }, []);

  return (
    <AppShell>
      <main className="space-y-6 p-4 md:p-8">
        <div className="flex justify-between items-start">
          <div>
            <h1 className="font-headline-lg text-headline-lg text-on-surface">Admin Central</h1>
            <p className="mt-1 text-body-md text-on-surface-variant">
              Visão geral do marketplace e métricas operacionais.
            </p>
          </div>
          <div className="flex gap-3">
            <Button asChild variant="outline">
              <Link href="/admin/categorias" className="flex items-center gap-2">
                <Tag className="h-4 w-4" />
                Gerir Categorias
              </Link>
            </Button>
            <Button asChild variant="outline">
              <Link href="/admin/familias" className="flex items-center gap-2">
                <Settings className="h-4 w-4" />
                Gerir Famílias
              </Link>
            </Button>
          </div>
        </div>

        {error && (
          <ErrorState
            message={error}
            onRetry={fetchData}
          />
        )}

        {loading && !dashboard ? (
          <div className="grid grid-cols-1 gap-5 md:grid-cols-2 xl:grid-cols-4">
            <KpiCard title="" value="" loading />
            <KpiCard title="" value="" loading />
            <KpiCard title="" value="" loading />
            <KpiCard title="" value="" loading />
          </div>
        ) : dashboard ? (
          <>
            <section className="grid grid-cols-1 gap-5 md:grid-cols-2 xl:grid-cols-4">
              <KpiCard
                title="Total de Encomendas"
                value={dashboard.totalOrders.toLocaleString("pt-PT")}
                subtitle={`${(dashboard.ordersByStatus.DELIVERED ?? 0).toLocaleString("pt-PT")} entregues`}
                icon={<Package className="h-6 w-6" />}
                trend={{ value: "12%", positive: true }}
              />
              <KpiCard
                title="Receita Total"
                value={formatCurrency(dashboard.totalRevenue)}
                subtitle="Valor bruto acumulado"
                icon={<CreditCard className="h-6 w-6" />}
                trend={{ value: "8.3%", positive: true }}
              />
              <KpiCard
                title="Vendedores Activos"
                value={dashboard.activeVendors.toLocaleString("pt-PT")}
                subtitle="Com loja aberta"
                icon={<Building2 className="h-6 w-6" />}
                trend={{ value: "5", positive: true }}
              />
              <KpiCard
                title="Estafetas Activos"
                value={dashboard.activeCouriers.toLocaleString("pt-PT")}
                subtitle="Disponíveis para entregas"
                icon={<Truck className="h-6 w-6" />}
                trend={{ value: "3", positive: true }}
              />
            </section>

            <section className="grid grid-cols-1 gap-5 lg:grid-cols-3">
              <Card className="lg:col-span-2">
                <CardHeader>
                  <CardTitle>Encomendas Recentes</CardTitle>
                </CardHeader>
                <CardContent>
                  {loading ? (
                    <TableSkeleton />
                  ) : orders.length === 0 ? (
                    <EmptyState message="Nenhuma encomenda registada." />
                  ) : (
                    <div className="overflow-x-auto">
                      <table className="w-full text-left text-sm">
                        <thead>
                          <tr className="border-b border-outline-variant text-xs font-bold uppercase text-on-surface-variant">
                            <th className="pb-3 pr-4">Referência</th>
                            <th className="pb-3 pr-4">Cliente</th>
                            <th className="pb-3 pr-4">Vendedor</th>
                            <th className="pb-3 pr-4">Valor</th>
                            <th className="pb-3 pr-4">Estado</th>
                            <th className="pb-3 pr-4">Data</th>
                          </tr>
                        </thead>
                        <tbody>
                          {orders.slice(0, 7).map((order) => (
                            <tr key={order.id} className="border-b border-outline-variant/50 last:border-0">
                              <td className="py-3 pr-4 font-bold text-on-surface">{order.reference}</td>
                              <td className="py-3 pr-4 text-on-surface-variant">{order.customerName}</td>
                              <td className="py-3 pr-4 text-on-surface-variant">{order.vendorName}</td>
                              <td className="py-3 pr-4 font-bold">{formatCurrency(order.total)}</td>
                              <td className="py-3 pr-4"><StatusBadge status={order.status} /></td>
                              <td className="py-3 pr-4 text-on-surface-variant">{order.createdAt ? formatDate(order.createdAt) : "—"}</td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </div>
                  )}
                </CardContent>
              </Card>

              <Card>
                <CardHeader>
                  <CardTitle>Distribuição de Estados</CardTitle>
                </CardHeader>
                <CardContent className="space-y-4">
                  <StatusChart data={dashboard.ordersByStatus} />
                  <div className="space-y-2">
                    {Object.entries(dashboard.ordersByStatus).map(([status, count]) => (
                      <div key={status} className="flex items-center justify-between text-sm">
                        <StatusBadge status={status} />
                        <span className="font-bold text-on-surface">{count.toLocaleString("pt-PT")}</span>
                      </div>
                    ))}
                  </div>
                </CardContent>
              </Card>
            </section>

            <section className="grid grid-cols-1 gap-5 md:grid-cols-2">
              <Card>
                <CardHeader>
                  <CardTitle>Cancelamentos</CardTitle>
                </CardHeader>
                <CardContent>
                  <div className="flex items-center gap-3">
                    <div className="flex h-12 w-12 items-center justify-center rounded-2xl bg-error-container">
                      <TrendingDown className="h-6 w-6 text-error" />
                    </div>
                    <div>
                      <p className="font-display text-3xl text-on-surface">
                        {dashboard.cancellations.toLocaleString("pt-PT")}
                      </p>
                      <p className="text-xs text-on-surface-variant">
                        {dashboard.totalOrders > 0 ? ((dashboard.cancellations / dashboard.totalOrders) * 100).toFixed(1) : "0.0"}% do total de encomendas
                      </p>
                    </div>
                  </div>
                </CardContent>
              </Card>
              <Card>
                <CardHeader>
                  <CardTitle>Entregas Falhadas</CardTitle>
                </CardHeader>
                <CardContent>
                  <div className="flex items-center gap-3">
                    <div className="flex h-12 w-12 items-center justify-center rounded-2xl bg-error-container">
                      <TrendingUp className="h-6 w-6 text-error" />
                    </div>
                    <div>
                      <p className="font-display text-3xl text-on-surface">
                        {dashboard.failedDeliveries.toLocaleString("pt-PT")}
                      </p>
                      <p className="text-xs text-on-surface-variant">
                        {dashboard.totalOrders > 0 ? ((dashboard.failedDeliveries / dashboard.totalOrders) * 100).toFixed(1) : "0.0"}% do total de entregas
                      </p>
                    </div>
                  </div>
                </CardContent>
              </Card>
            </section>
          </>
        ) : (
          <EmptyState
            title="Nenhum dado disponível"
            message="Não foi possível carregar os dados do painel administrativo."
          />
        )}
      </main>
    </AppShell>
  );
}
