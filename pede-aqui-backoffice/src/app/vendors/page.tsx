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
import { dashboardService, orderService, vendorService, categoryService, uploadService } from "@/lib/api/services";
import { formatCurrency, formatDate, cn } from "@/lib/utils";
import type { VendorDashboard, Order, VendorDocument, Category } from "@/lib/api/types";
import { DollarSign, ShoppingBag, TrendingUp, XCircle, Upload, FileText, Image, Trash2 } from "lucide-react";


type OrderTab = "all" | "pending" | "active" | "delivered" | "cancelled";

type VendorRecord = {
  id: string;
  nome: string;
  categoryId: string;
  categoryName?: string;
  estado: string;
};

type VendorFormData = {
  id: string;
  name: string;
  ownerName: string;
  nif: string;
  phone: string;
  address: string;
  description: string;
  categoryId: string;
  logoStorageKey?: string;
  logoPreview?: string;
  documents: VendorDocument[];
};

// Status mapping between backend enum and Portuguese display
const statusMapping = {
  'PENDING': 'Em validação',
  'APPROVED': 'Aprovado', 
  'REJECTED': 'Rejeitado',
  'ACTIVE': 'Ativo',
  'INACTIVE': 'Inativo'
} as const;


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
  const [categories, setCategories] = useState<Category[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [activeTab, setActiveTab] = useState<OrderTab>("all");
  const [vendorFormMode, setVendorFormMode] = useState<"create" | "edit">("create");
  const [vendorForm, setVendorForm] = useState<VendorFormData>({ 
    id: "", 
    name: "", 
    ownerName: "", 
    nif: "", 
    phone: "", 
    address: "", 
    description: "", 
    categoryId: "", 
      documents: []
  });
  const [vendorRecords, setVendorRecords] = useState<VendorRecord[]>([]);
  const [logoUploading, setLogoUploading] = useState(false);
  const [documentUploading, setDocumentUploading] = useState(false);
  const [selectedDocType, setSelectedDocType] = useState("BUSINESS_LICENCE");

  const fetchData = async () => {
    setLoading(true);
    setError(null);
    try {
      // Fetch categories first as they're needed for vendor mapping
      const categoriesData = await categoryService.list();
      setCategories(categoriesData);

      const [dashData, ordersData, vendorsData] = await Promise.all([
        dashboardService.getVendor(),
        orderService.list(),
        vendorService.list(),
      ]);
      setDashboard(dashData);
      setOrders(ordersData);
      setVendorRecords(vendorsData.map((vendor) => {
        const category = categoriesData.find(cat => cat.id === vendor.categoryId);
        return {
          id: vendor.id,
          nome: vendor.name,
          categoryId: vendor.categoryId,
          categoryName: category?.name || 'Categoria não encontrada',
          estado: statusMapping[vendor.verificationStatus as keyof typeof statusMapping] || vendor.verificationStatus,
        };
      }));
    } catch (error) {
      console.error("Failed to fetch data:", error);
      setError("Falha ao carregar dados. Tente novamente.");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { fetchData(); }, []);

  const filteredOrders = filterOrders(orders, activeTab);

  function resetVendorForm() {
    setVendorFormMode("create");
    setVendorForm({ 
      id: "", 
      name: "", 
      ownerName: "", 
      nif: "", 
      phone: "", 
      address: "", 
      description: "", 
      categoryId: "", 
      documents: []
    });
  }

  async function handleLogoUpload(file: File) {
    try {
      setLogoUploading(true);
      const { uploadUrl, storageKey } = await uploadService.getPresignedUrl({
        purpose: "vendor-logo",
        fileName: file.name,
        contentType: file.type,
      });
      
      await uploadService.uploadToS3(uploadUrl, file);
      
      const logoPreview = URL.createObjectURL(file);
      setVendorForm(prev => ({ ...prev, logoStorageKey: storageKey, logoPreview }));
    } catch (error) {
      console.error("Logo upload failed:", error);
    } finally {
      setLogoUploading(false);
    }
  }

  async function handleDocumentUpload(file: File, documentType: string) {
    try {
      setDocumentUploading(true);
      const { uploadUrl, storageKey } = await uploadService.getDocumentPresignedUrl({
        purpose: "vendor-document",
        fileName: file.name,
        contentType: file.type,
      });
      
      await uploadService.uploadToS3(uploadUrl, file);
      
      const newDocument: VendorDocument = {
        id: crypto.randomUUID(),
        vendorId: vendorForm.id,
        documentType,
        storageKey,
        status: "PENDING",
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
      };
      
      setVendorForm(prev => ({ 
        ...prev, 
        documents: [...prev.documents, newDocument]
      }));
    } catch (error) {
      console.error("Document upload failed:", error);
    } finally {
      setDocumentUploading(false);
    }
  }

  function removeDocument(documentId: string) {
    setVendorForm(prev => ({
      ...prev,
      documents: prev.documents.filter(doc => doc.id !== documentId)
    }));
  }

  async function submitVendorForm(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!vendorForm.name.trim() || !vendorForm.categoryId.trim() || !vendorForm.phone.trim()) return;
    
    // Block submission if no valid categories are loaded
    if (categories.length === 0) {
      setError("Não é possível criar vendedor: categorias não disponíveis. Tente recarregar a página.");
      return;
    }

    if (vendorFormMode === "create") {
      try {
        const created = await vendorService.create({
          name: vendorForm.name,
          ownerName: vendorForm.ownerName,
          nif: vendorForm.nif,
          phone: vendorForm.phone,
          address: vendorForm.address,
          description: vendorForm.description,
          logoStorageKey: vendorForm.logoStorageKey,
          categoryId: vendorForm.categoryId,
        });
        
        // Upload documents if any
        for (const doc of vendorForm.documents) {
          await vendorService.uploadDocument(created.id, {
            documentType: doc.documentType,
            storageKey: doc.storageKey,
          });
        }
        
        const category = categories.find(cat => cat.id === created.categoryId);
        setVendorRecords((prev) => [{
          id: created.id,
          nome: created.name,
          categoryId: created.categoryId,
          categoryName: category?.name || 'Categoria não encontrada',
          estado: statusMapping[created.verificationStatus as keyof typeof statusMapping] || created.verificationStatus,
        }, ...prev]);
        setError(null);
      } catch (error) {
        console.error("Failed to create vendor:", error);
        setError("Falha ao criar vendedor. Tente novamente.");
        return;
      }
    } else {
      try {
        const updated = await vendorService.update(vendorForm.id, {
          name: vendorForm.name,
          ownerName: vendorForm.ownerName,
          nif: vendorForm.nif,
          phone: vendorForm.phone,
          address: vendorForm.address,
          description: vendorForm.description,
          logoStorageKey: vendorForm.logoStorageKey,
          categoryId: vendorForm.categoryId,
        });
        const category = categories.find(cat => cat.id === updated.categoryId);
        setVendorRecords((prev) => prev.map((vendor) => (
          vendor.id === vendorForm.id
            ? { 
                id: updated.id, 
                nome: updated.name, 
                categoryId: updated.categoryId,
                categoryName: category?.name || 'Categoria não encontrada',
                estado: statusMapping[updated.verificationStatus as keyof typeof statusMapping] || updated.verificationStatus
              }
            : vendor
        )));
        setError(null);
      } catch (error) {
        console.error("Failed to update vendor:", error);
        setError("Falha ao atualizar vendedor. Tente novamente.");
        return;
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
                title="Receita Total (Entregas)"
                value={formatCurrency(dashboard.salesTotal)}
                icon={<DollarSign className="h-6 w-6" />}
              />
              <KpiCard
                title="Encomendas Aceites"
                value={dashboard.ordersByStatusCount.toLocaleString("pt-PT")}
                icon={<ShoppingBag className="h-6 w-6" />}
              />
              <KpiCard
                title="Ticket Médio"
                value={dashboard.ordersByStatusCount > 0
                  ? formatCurrency(dashboard.salesTotal / dashboard.ordersByStatusCount)
                  : "—"}
                icon={<TrendingUp className="h-6 w-6" />}
              />
              <KpiCard
                title="Encomendas Rejeitadas"
                value={dashboard.rejectedOrdersCount.toLocaleString("pt-PT")}
                icon={<XCircle className="h-6 w-6" />}
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
                  <CardTitle>Resumo</CardTitle>
                </CardHeader>
                <CardContent>
                  <div className="space-y-4">
                    <div className="flex items-center justify-between rounded-xl bg-surface-container-low p-4">
                      <span className="text-sm text-on-surface-variant">Encomendas aceites</span>
                      <span className="text-sm font-bold text-on-surface">{dashboard.ordersByStatusCount}</span>
                    </div>
                    <div className="flex items-center justify-between rounded-xl bg-surface-container-low p-4">
                      <span className="text-sm text-on-surface-variant">Encomendas rejeitadas</span>
                      <span className="text-sm font-bold text-error">{dashboard.rejectedOrdersCount}</span>
                    </div>
                    <div className="flex items-center justify-between rounded-xl bg-surface-container-low p-4">
                      <span className="text-sm text-on-surface-variant">Receita total</span>
                      <span className="text-sm font-bold text-on-surface">{formatCurrency(dashboard.salesTotal)}</span>
                    </div>
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
                  {loading ? (
                    <TableSkeleton />
                  ) : vendorRecords.length === 0 ? (
                    <EmptyState message="Nenhum vendedor encontrado." />
                  ) : (
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
                              <td className="py-3 pr-4 text-on-surface-variant">{vendor.categoryName}</td>
                              <td className="py-3 pr-4 text-on-surface-variant">{vendor.estado}</td>
                              <td className="py-3">
                                <Button
                                  variant="outline"
                                  size="sm"
                                  onClick={async () => {
                                    try {
                                      setVendorFormMode("edit");
                                      const vendorData = await vendorService.getById(vendor.id);
                                      // Load vendor documents as well
                                      const vendorDocuments = await vendorService.getDocuments(vendor.id);
                                      setVendorForm({
                                        id: vendorData.id,
                                        name: vendorData.name,
                                        ownerName: vendorData.ownerName || "",
                                        nif: vendorData.nif || "",
                                        phone: vendorData.phone || "",
                                        address: vendorData.address || "",
                                        description: vendorData.description || "",
                                        categoryId: vendorData.categoryId,
                                        logoPreview: vendorData.logoStorageKey ? `/api/vendor-logo/${vendorData.logoStorageKey}` : undefined,
                                        logoStorageKey: vendorData.logoStorageKey,
                                        documents: vendorDocuments
                                      });
                                    } catch (error) {
                                      console.error("Failed to load vendor data:", error);
                                      setError("Falha ao carregar dados do vendedor. Tente novamente.");
                                    }
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
                  )}
                </CardContent>
              </Card>

              <Card>
                <CardHeader>
                  <CardTitle>{vendorFormMode === "create" ? "Criar Vendedor" : "Editar Vendedor"}</CardTitle>
                </CardHeader>
                <CardContent>
                  <form className="space-y-4" onSubmit={submitVendorForm}>
                    {/* Logo Upload */}
                    <div className="space-y-2">
                      <label className="text-sm font-bold text-on-surface">Logotipo</label>
                      <div className="flex items-center gap-3">
                        {vendorForm.logoPreview ? (
                          <div className="relative">
                            <img 
                              src={vendorForm.logoPreview} 
                              alt="Logo preview" 
                              className="h-16 w-16 rounded-lg object-cover border border-outline-variant"
                            />
                            <button
                              type="button"
                              onClick={() => setVendorForm(prev => ({ ...prev, logoStorageKey: undefined, logoPreview: undefined }))}
                              className="absolute -top-2 -right-2 h-6 w-6 rounded-full bg-error text-white flex items-center justify-center"
                            >
                              <Trash2 className="h-3 w-3" />
                            </button>
                          </div>
                        ) : (
                          <div className="h-16 w-16 rounded-lg border-2 border-dashed border-outline-variant flex items-center justify-center">
                            <Image className="h-6 w-6 text-on-surface-variant" />
                          </div>
                        )}
                        <div className="flex-1">
                          <input
                            type="file"
                            accept="image/*"
                            id="logo-upload"
                            className="hidden"
                            onChange={(e) => {
                              const file = e.target.files?.[0];
                              if (file) handleLogoUpload(file);
                            }}
                          />
                          <Button
                            type="button"
                            variant="outline"
                            size="sm"
                            onClick={() => document.getElementById('logo-upload')?.click()}
                            disabled={logoUploading}
                          >
                            {logoUploading ? (
                              <>Enviando...</>
                            ) : (
                              <>
                                <Upload className="h-4 w-4 mr-2" />
                                Escolher Logotipo
                              </>
                            )}
                          </Button>
                        </div>
                      </div>
                    </div>

                    {/* Basic Information */}
                    <div className="grid grid-cols-1 gap-3">
                      <Input
                        placeholder="Nome do vendedor *"
                        value={vendorForm.name}
                        onChange={(event) => setVendorForm((prev) => ({ ...prev, name: event.target.value }))}
                        required
                      />
                      <Input
                        placeholder="Nome do proprietário"
                        value={vendorForm.ownerName}
                        onChange={(event) => setVendorForm((prev) => ({ ...prev, ownerName: event.target.value }))}
                      />
                      <Input
                        placeholder="NUIT"
                        value={vendorForm.nif}
                        onChange={(event) => setVendorForm((prev) => ({ ...prev, nif: event.target.value }))}
                      />
                      <Input
                        placeholder="Telefone *"
                        value={vendorForm.phone}
                        onChange={(event) => setVendorForm((prev) => ({ ...prev, phone: event.target.value }))}
                        required
                      />
                      <Input
                        placeholder="Endereço"
                        value={vendorForm.address}
                        onChange={(event) => setVendorForm((prev) => ({ ...prev, address: event.target.value }))}
                      />
                      <textarea
                        placeholder="Descrição"
                        value={vendorForm.description}
                        onChange={(event) => setVendorForm((prev) => ({ ...prev, description: event.target.value }))}
                        className="flex min-h-[80px] w-full rounded-xl border border-outline-variant bg-white px-4 py-2 text-sm text-on-surface shadow-sm transition-colors placeholder:text-on-surface-variant focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary disabled:cursor-not-allowed disabled:opacity-50 resize-none"
                      />
                      <div>
                        <label className="text-sm font-bold text-on-surface mb-1 block">Categoria *</label>
                        <select
                          value={vendorForm.categoryId}
                          onChange={(event) => setVendorForm((prev) => ({ ...prev, categoryId: event.target.value }))}
                          className="flex h-11 w-full rounded-xl border border-outline-variant bg-white px-4 py-2 text-sm text-on-surface shadow-sm transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary disabled:cursor-not-allowed disabled:opacity-50"
                          disabled={categories.length === 0}
                          required
                        >
                          <option value="">
                            {categories.length === 0 ? "A carregar categorias..." : "Selecione uma categoria"}
                          </option>
                          {categories.map((category) => (
                            <option key={category.id} value={category.id}>
                              {category.name}
                            </option>
                          ))}
                        </select>
                      </div>
                    </div>

                    {/* Document Upload */}
                    <div className="space-y-3">
                      <label className="text-sm font-bold text-on-surface">Documentos</label>
                      
                      {/* Document Upload Controls */}
                      <div className="space-y-3">
                        <select
                          value={selectedDocType}
                          onChange={(e) => setSelectedDocType(e.target.value)}
                          className="flex h-11 w-full rounded-xl border border-outline-variant bg-white px-4 py-2 text-sm text-on-surface shadow-sm transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary disabled:cursor-not-allowed disabled:opacity-50"
                        >
                          <option value="BUSINESS_LICENCE">Licença Comercial</option>
                          <option value="TAX_CERTIFICATE">Certificado Fiscal</option>
                          <option value="HEALTH_PERMIT">Alvará Sanitário</option>
                          <option value="OTHER">Outro</option>
                        </select>
                        
                        <div className="flex gap-2">
                          <input
                            type="file"
                            accept=".pdf,.jpeg,.jpg,.png,.webp"
                            id="document-upload"
                            className="hidden"
                            onChange={(e) => {
                              const file = e.target.files?.[0];
                              if (file) handleDocumentUpload(file, selectedDocType);
                            }}
                          />
                          <Button
                            type="button"
                            variant="outline"
                            size="sm"
                            onClick={() => document.getElementById('document-upload')?.click()}
                            disabled={documentUploading}
                          >
                            {documentUploading ? (
                              <>Enviando...</>
                            ) : (
                              <>
                                <FileText className="h-4 w-4 mr-2" />
                                Adicionar Documento
                              </>
                            )}
                          </Button>
                        </div>
                      </div>

                      {/* Document List */}
                      {vendorForm.documents.length > 0 && (
                        <div className="space-y-2">
                          {vendorForm.documents.map((doc) => (
                            <div key={doc.id} className="flex items-center justify-between p-3 border border-outline-variant rounded-lg">
                              <div className="flex items-center gap-2">
                                <FileText className="h-4 w-4 text-on-surface-variant" />
                                <span className="text-sm text-on-surface">{doc.documentType}</span>
                              </div>
                              <Button
                                type="button"
                                variant="ghost"
                                size="sm"
                                onClick={() => removeDocument(doc.id)}
                              >
                                <Trash2 className="h-4 w-4" />
                              </Button>
                            </div>
                          ))}
                        </div>
                      )}
                    </div>

                    <div className="flex gap-2">
                      <Button 
                        type="submit" 
                        className="flex-1"
                        disabled={categories.length === 0}
                      >
                        {vendorFormMode === "create" ? "Criar" : "Guardar"}
                      </Button>
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
