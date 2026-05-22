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
import { Input } from "@/components/ui/input";
import { dashboardService, orderService, vendorService } from "@/lib/api/services";
import { formatCurrency, formatDate, cn } from "@/lib/utils";
import type { VendorDashboard, Order } from "@/lib/api/types";
import { DollarSign, ShoppingBag, TrendingUp, XCircle } from "lucide-react";

const mockDashboard: VendorDashboard = {
  salesSummary: {
    totalRevenue: 458750.00,
    totalOrders: 1247,
    averageOrderValue: 368.00,
  },
  ordersByStatus: {
    PENDING: 23,
    ACCEPTED_BY_VENDOR: 45,
    PREPARING: 18,
    READY_FOR_PICKUP: 12,
    DELIVERING: 8,
    DELIVERED: 1102,
    CANCELLED: 39,
  },
  topProducts: [
    { name: "Frango Grelhado com Fries", quantity: 342, revenue: 123120.00 },
    { name: "Pizza Margherita XXL", quantity: 287, revenue: 100450.00 },
    { name: "Sushi Combo 24 peças", quantity: 156, revenue: 93600.00 },
    { name: "Hambúrguer Especial da Casa", quantity: 298, revenue: 74500.00 },
    { name: "Bacalhau à Brás", quantity: 164, revenue: 67240.00 },
  ],
  rejectedOrders: 12,
};

const mockOrders: Order[] = [
  { id: "1", reference: "PA-2024-001", customerName: "Maria Silva", vendorName: "Restaurante Central", status: "DELIVERING", total: 850.00, createdAt: "2024-01-15T10:30:00Z", items: [] },
  { id: "2", reference: "PA-2024-002", customerName: "João Santos", vendorName: "Restaurante Central", status: "PREPARING", total: 1250.00, createdAt: "2024-01-15T11:00:00Z", items: [] },
  { id: "3", reference: "PA-2024-003", customerName: "Ana Pereira", vendorName: "Restaurante Central", status: "DELIVERED", total: 2200.00, createdAt: "2024-01-15T09:15:00Z", items: [] },
  { id: "4", reference: "PA-2024-004", customerName: "Carlos Mendes", vendorName: "Restaurante Central", status: "PENDING", total: 680.00, createdAt: "2024-01-15T12:00:00Z", items: [] },
  { id: "5", reference: "PA-2024-005", customerName: "Sofia Rodrigues", vendorName: "Restaurante Central", status: "READY_FOR_PICKUP", total: 1750.00, createdAt: "2024-01-15T11:30:00Z", items: [] },
  { id: "6", reference: "PA-2024-006", customerName: "Rui Oliveira", vendorName: "Restaurante Central", status: "ACCEPTED_BY_VENDOR", total: 320.00, createdAt: "2024-01-15T12:15:00Z", items: [] },
  { id: "7", reference: "PA-2024-007", customerName: "Inês Costa", vendorName: "Restaurante Central", status: "DELIVERED", total: 940.00, createdAt: "2024-01-15T08:00:00Z", items: [] },
  { id: "8", reference: "PA-2024-008", customerName: "Pedro Lopes", vendorName: "Restaurante Central", status: "CANCELLED", total: 450.00, createdAt: "2024-01-14T19:30:00Z", items: [] },
];

type OrderTab = "all" | "pending" | "active" | "delivered" | "cancelled";

type VendorRecord = {
  id: string;
  nome: string;
  categoria: string;
  estado: string;
};

const mockVendors: VendorRecord[] = [
  { id: "v-1", nome: "Restaurante Central", categoria: "Restaurante", estado: "Activo" },
  { id: "v-2", nome: "Mercado Matola", categoria: "Mercearia", estado: "Activo" },
  { id: "v-3", nome: "Farmacia Baixa", categoria: "Farmacia", estado: "Em validacao" },
];

const tabs: { key: OrderTab; label: string }[] = [
  { key: "all", label: "Todas" },
  { key: "pending", label: "Pendentes" },
  { key: "active", label: "Activas" },
  { key: "delivered", label: "Entregues" },
  { key: "cancelled", label: "Canceladas" },
];

const pendingStatuses = ["PENDING", "PAYMENT_PENDING"];
const activeStatuses = ["PAYMENT_CONFIRMED", "ACCEPTED_BY_VENDOR", "PREPARING", "READY_FOR_PICKUP", "DISPATCH_PENDING", "ASSIGNED_TO_COURIER", "PICKED_UP", "DELIVERING"];
const deliveredStatuses = ["DELIVERED"];
const cancelledStatuses = ["CANCELLED", "REJECTED_BY_VENDOR", "REFUND_PENDING", "REFUNDED"];

function filterOrders(orders: Order[], tab: OrderTab): Order[] {
  switch (tab) {
    case "pending": return orders.filter((o) => pendingStatuses.includes(o.status));
    case "active": return orders.filter((o) => activeStatuses.includes(o.status));
    case "delivered": return orders.filter((o) => deliveredStatuses.includes(o.status));
    case "cancelled": return orders.filter((o) => cancelledStatuses.includes(o.status));
    default: return orders;
  }
}

export default function VendorsPage() {
  const [dashboard, setDashboard] = useState<VendorDashboard | null>(null);
  const [orders, setOrders] = useState<Order[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [activeTab, setActiveTab] = useState<OrderTab>("all");
  const [vendorFormMode, setVendorFormMode] = useState<"create" | "edit">("create");
  const [vendorForm, setVendorForm] = useState<VendorRecord>({ id: "", nome: "", categoria: "Restaurante", estado: "Activo" });
  const [vendorRecords, setVendorRecords] = useState<VendorRecord[]>(mockVendors);

  const fetchData = async () => {
    setLoading(true);
    setError(null);
    try {
      const [dashData, ordersData, vendorsData] = await Promise.all([
        dashboardService.getVendor(),
        orderService.list(),
        vendorService.list(),
      ]);
      setDashboard(dashData);
      setOrders(ordersData);
      setVendorRecords(vendorsData.map((vendor) => ({
        id: vendor.id,
        nome: vendor.name,
        categoria: vendor.category,
        estado: vendor.status,
      })));
    } catch {
      setDashboard(mockDashboard);
      setOrders(mockOrders);
      setVendorRecords(mockVendors);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { fetchData(); }, []);

  const filteredOrders = filterOrders(orders, activeTab);

  function resetVendorForm() {
    setVendorFormMode("create");
    setVendorForm({ id: "", nome: "", categoria: "Restaurante", estado: "Activo" });
  }

  async function submitVendorForm(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!vendorForm.nome.trim()) return;

    if (vendorFormMode === "create") {
      try {
        const created = await vendorService.create({
          name: vendorForm.nome,
          category: vendorForm.categoria,
          status: vendorForm.estado,
        });
        setVendorRecords((prev) => [{
          id: created.id,
          nome: created.name,
          categoria: created.category,
          estado: created.status,
        }, ...prev]);
      } catch {
        setVendorRecords((prev) => [{ ...vendorForm, id: crypto.randomUUID() }, ...prev]);
      }
    } else {
      try {
        const updated = await vendorService.update(vendorForm.id, {
          name: vendorForm.nome,
          category: vendorForm.categoria,
          status: vendorForm.estado,
        });
        setVendorRecords((prev) => prev.map((vendor) => (
          vendor.id === vendorForm.id
            ? { id: updated.id, nome: updated.name, categoria: updated.category, estado: updated.status }
            : vendor
        )));
      } catch {
        setVendorRecords((prev) => prev.map((vendor) => (vendor.id === vendorForm.id ? vendorForm : vendor)));
      }
    }

    resetVendorForm();
  }

  return (
    <AppShell>
      <main className="space-y-6 p-4 md:p-8">
        <div>
          <h1 className="font-headline-lg text-headline-lg text-on-surface">Vendedores</h1>
          <p className="mt-1 text-body-md text-on-surface-variant">
            Gestão de vendas, encomendas e produtos dos vendedores do marketplace.
          </p>
        </div>

        {error && (
          <ErrorState
            message={error}
            onRetry={fetchData}
          />
        )}

        {loading && !dashboard ? (
          <div className="grid grid-cols-1 gap-5 md:grid-cols-2 xl:grid-cols-4">
            {[1, 2, 3, 4].map((i) => <KpiCard key={i} title="" value="" loading />)}
          </div>
        ) : dashboard ? (
          <>
            <section className="grid grid-cols-1 gap-5 md:grid-cols-2 xl:grid-cols-4">
              <KpiCard
                title="Receita Total"
                value={formatCurrency(dashboard.salesSummary.totalRevenue)}
                icon={<DollarSign className="h-6 w-6" />}
                trend={{ value: "15.2%", positive: true }}
              />
              <KpiCard
                title="Total de Encomendas"
                value={dashboard.salesSummary.totalOrders.toLocaleString("pt-PT")}
                icon={<ShoppingBag className="h-6 w-6" />}
                trend={{ value: "8.7%", positive: true }}
              />
              <KpiCard
                title="Ticket Médio"
                value={formatCurrency(dashboard.salesSummary.averageOrderValue)}
                icon={<TrendingUp className="h-6 w-6" />}
                trend={{ value: "3.2%", positive: true }}
              />
              <KpiCard
                title="Encomendas Rejeitadas"
                value={dashboard.rejectedOrders.toLocaleString("pt-PT")}
                icon={<XCircle className="h-6 w-6" />}
                trend={{ value: "2", positive: false }}
              />
            </section>

            <section className="grid grid-cols-1 gap-5 lg:grid-cols-3">
              <Card className="lg:col-span-2">
                <CardHeader>
                  <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
                    <CardTitle>Gestão de Encomendas</CardTitle>
                    <div className="flex gap-1 rounded-xl bg-surface-container-low p-1">
                      {tabs.map((tab) => (
                        <button
                          key={tab.key}
                          onClick={() => setActiveTab(tab.key)}
                          className={cn(
                            "rounded-lg px-3 py-1.5 text-xs font-bold transition-all",
                            activeTab === tab.key
                              ? "bg-white text-on-surface shadow-sm"
                              : "text-on-surface-variant hover:text-on-surface",
                          )}
                        >
                          {tab.label}
                        </button>
                      ))}
                    </div>
                  </div>
                </CardHeader>
                <CardContent>
                  {loading ? (
                    <TableSkeleton />
                  ) : filteredOrders.length === 0 ? (
                    <EmptyState message="Nenhuma encomenda encontrada com este filtro." />
                  ) : (
                    <div className="overflow-x-auto">
                      <table className="w-full text-left text-sm">
                        <thead>
                          <tr className="border-b border-outline-variant text-xs font-bold uppercase text-on-surface-variant">
                            <th className="pb-3 pr-4">Referência</th>
                            <th className="pb-3 pr-4">Cliente</th>
                            <th className="pb-3 pr-4">Valor</th>
                            <th className="pb-3 pr-4">Estado</th>
                            <th className="pb-3 pr-4">Data</th>
                          </tr>
                        </thead>
                        <tbody>
                          {filteredOrders.map((order) => (
                            <tr key={order.id} className="border-b border-outline-variant/50 last:border-0">
                              <td className="py-3 pr-4 font-bold text-on-surface">{order.reference}</td>
                              <td className="py-3 pr-4 text-on-surface-variant">{order.customerName}</td>
                              <td className="py-3 pr-4 font-bold">{formatCurrency(order.total)}</td>
                              <td className="py-3 pr-4"><StatusBadge status={order.status} /></td>
                              <td className="py-3 pr-4 text-on-surface-variant">{formatDate(order.createdAt)}</td>
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
                  <CardTitle>Produtos Mais Vendidos</CardTitle>
                </CardHeader>
                <CardContent>
                  <div className="space-y-4">
                    {dashboard.topProducts.map((product, i) => (
                      <div key={product.name} className="flex items-center gap-4">
                        <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-primary-fixed text-sm font-bold text-primary">
                          {i + 1}
                        </div>
                        <div className="min-w-0 flex-1">
                          <p className="truncate text-sm font-bold text-on-surface">{product.name}</p>
                          <p className="text-xs text-on-surface-variant">
                            {product.quantity} vendidos · {formatCurrency(product.revenue)}
                          </p>
                        </div>
                      </div>
                    ))}
                  </div>
                </CardContent>
              </Card>
            </section>

            <section className="grid grid-cols-1 gap-5 lg:grid-cols-3">
              <Card className="lg:col-span-2">
                <CardHeader>
                  <CardTitle>Lista de Vendedores</CardTitle>
                </CardHeader>
                <CardContent>
                  <div className="overflow-x-auto">
                    <table className="w-full text-left text-sm">
                      <thead>
                        <tr className="border-b border-outline-variant text-xs font-bold uppercase text-on-surface-variant">
                          <th className="pb-3 pr-4">Nome</th>
                          <th className="pb-3 pr-4">Categoria</th>
                          <th className="pb-3 pr-4">Estado</th>
                          <th className="pb-3">Accoes</th>
                        </tr>
                      </thead>
                      <tbody>
                        {vendorRecords.map((vendor) => (
                          <tr key={vendor.id} className="border-b border-outline-variant/50 last:border-0">
                            <td className="py-3 pr-4 font-bold text-on-surface">{vendor.nome}</td>
                            <td className="py-3 pr-4 text-on-surface-variant">{vendor.categoria}</td>
                            <td className="py-3 pr-4 text-on-surface-variant">{vendor.estado}</td>
                            <td className="py-3">
                              <Button
                                variant="outline"
                                size="sm"
                                onClick={() => {
                                  setVendorFormMode("edit");
                                  setVendorForm(vendor);
                                }}
                              >
                                Editar
                              </Button>
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                </CardContent>
              </Card>

              <Card>
                <CardHeader>
                  <CardTitle>{vendorFormMode === "create" ? "Criar Vendedor" : "Editar Vendedor"}</CardTitle>
                </CardHeader>
                <CardContent>
                  <form className="space-y-3" onSubmit={submitVendorForm}>
                    <Input
                      placeholder="Nome do vendedor"
                      value={vendorForm.nome}
                      onChange={(event) => setVendorForm((prev) => ({ ...prev, nome: event.target.value }))}
                      required
                    />
                    <Input
                      placeholder="Categoria"
                      value={vendorForm.categoria}
                      onChange={(event) => setVendorForm((prev) => ({ ...prev, categoria: event.target.value }))}
                    />
                    <Input
                      placeholder="Estado"
                      value={vendorForm.estado}
                      onChange={(event) => setVendorForm((prev) => ({ ...prev, estado: event.target.value }))}
                    />
                    <div className="flex gap-2">
                      <Button type="submit" className="flex-1">{vendorFormMode === "create" ? "Criar" : "Guardar"}</Button>
                      {vendorFormMode === "edit" && (
                        <Button type="button" variant="outline" onClick={resetVendorForm}>Cancelar</Button>
                      )}
                    </div>
                  </form>
                </CardContent>
              </Card>
            </section>
          </>
        ) : (
          <EmptyState
            title="Nenhum dado disponível"
            message="Não foi possível carregar os dados dos vendedores."
          />
        )}
      </main>
    </AppShell>
  );
}
