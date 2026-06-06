"use client";

import { useEffect, useState } from "react";
import { AppShell } from "@/components/layout/app-shell";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { StatusBadge } from "@/components/ui/status-badge";
import { ErrorState } from "@/components/ui/error-state";
import { EmptyState } from "@/components/ui/empty-state";
import { TableSkeleton } from "@/components/ui/loading-skeleton";
import { vendorService, categoryService, catalogService } from "@/lib/api/services";
import { cn, formatCurrency } from "@/lib/utils";
import type { Product, Vendor, Category } from "@/lib/api/types";
import { Plus, Tag, Settings } from "lucide-react";
import Link from "next/link";

export default function CatalogoPage() {
  const [vendors, setVendors] = useState<Vendor[]>([]);
  const [categories, setCategories] = useState<Category[]>([]);
  const [selectedVendorId, setSelectedVendorId] = useState("");
  const [products, setProducts] = useState<Product[]>([]);
  const [selectedProduct, setSelectedProduct] = useState<Product | null>(null);
  const [loading, setLoading] = useState(true);
  const [productsLoading, setProductsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const [showProductForm, setShowProductForm] = useState(false);
  const [productForm, setProductForm] = useState({ name: "", categoryId: "", description: "" });
  const [creatingProduct, setCreatingProduct] = useState(false);

  const [skuForm, setSkuForm] = useState({ skuCode: "", name: "", price: "", initialStock: "0" });
  const [creatingSku, setCreatingSku] = useState(false);

  const [approvingProduct, setApprovingProduct] = useState<string | null>(null);
  const [rejectingProduct, setRejectingProduct] = useState<string | null>(null);

  useEffect(() => {
    async function init() {
      setLoading(true);
      try {
        const [vs, cats] = await Promise.all([vendorService.list(), categoryService.list()]);
        setVendors(vs);
        setCategories(cats);
        if (vs.length > 0) setSelectedVendorId(vs[0].id);
      } catch {
        setError("Erro ao carregar dados.");
      } finally {
        setLoading(false);
      }
    }
    init();
  }, []);

  useEffect(() => {
    if (!selectedVendorId) {
      setProducts([]);
      setSelectedProduct(null);
      return;
    }
    setProductsLoading(true);
    setSelectedProduct(null);
    setProducts([]); // Clear products immediately when switching vendors
    setError(null); // Clear any previous errors
    catalogService
      .listVendorProducts(selectedVendorId)
      .then(setProducts)
      .catch(() => {
        setProducts([]); // Ensure products are cleared on error
        setError("Erro ao carregar produtos.");
      })
      .finally(() => setProductsLoading(false));
  }, [selectedVendorId]);

  async function handleCreateProduct(e: React.FormEvent) {
    e.preventDefault();
    if (!selectedVendorId) return;
    setCreatingProduct(true);
    setError(null);
    try {
      const created = await catalogService.createProduct({
        vendorId: selectedVendorId,
        categoryId: productForm.categoryId,
        name: productForm.name,
        description: productForm.description || undefined,
      });
      setProducts((prev) => [...prev, created]);
      setProductForm({ name: "", categoryId: "", description: "" });
      setShowProductForm(false);
      setError(null); // Clear error after successful creation
    } catch (err) {
      console.error("Failed to create product:", err);
      setError("Erro ao criar produto.");
    } finally {
      setCreatingProduct(false);
    }
  }

  async function handleCreateSku(e: React.FormEvent) {
    e.preventDefault();
    if (!selectedProduct || !selectedVendorId) return;
    setCreatingSku(true);
    setError(null);
    try {
      const sku = await catalogService.createSku({
        productId: selectedProduct.id,
        vendorId: selectedVendorId,
        skuCode: skuForm.skuCode,
        name: skuForm.name,
        price: parseFloat(skuForm.price),
        initialStock: parseInt(skuForm.initialStock, 10) || 0,
      });
      const updated = { ...selectedProduct, skus: [...selectedProduct.skus, sku] };
      setSelectedProduct(updated);
      setProducts((prev) => prev.map((p) => (p.id === updated.id ? updated : p)));
      setSkuForm({ skuCode: "", name: "", price: "", initialStock: "0" });
      setError(null); // Clear error after successful creation
    } catch (err) {
      console.error("Failed to create SKU:", err);
      setError("Erro ao criar SKU.");
    } finally {
      setCreatingSku(false);
    }
  }

  async function handleApproveProduct(productId: string) {
    setApprovingProduct(productId);
    setError(null);
    try {
      const updated = await catalogService.approveProduct(productId);
      setProducts((prev) => prev.map((p) => (p.id === productId ? updated : p)));
      if (selectedProduct?.id === productId) {
        setSelectedProduct(updated);
      }
    } catch (err) {
      console.error("Failed to approve product:", err);
      setError("Erro ao aprovar produto.");
    } finally {
      setApprovingProduct(null);
    }
  }

  async function handleRejectProduct(productId: string) {
    setRejectingProduct(productId);
    setError(null);
    try {
      const updated = await catalogService.rejectProduct(productId);
      setProducts((prev) => prev.map((p) => (p.id === productId ? updated : p)));
      if (selectedProduct?.id === productId) {
        setSelectedProduct(updated);
      }
    } catch (err) {
      console.error("Failed to reject product:", err);
      setError("Erro ao rejeitar produto.");
    } finally {
      setRejectingProduct(null);
    }
  }

  const categoryName = (id: string) => categories.find((c) => c.id === id)?.name ?? "—";

  return (
    <AppShell>
      <main className="space-y-6 p-4 md:p-8">
        <div className="flex justify-between items-start">
          <div>
            <h1 className="font-headline-lg text-headline-lg text-on-surface">Catálogo</h1>
            <p className="mt-1 text-body-md text-on-surface-variant">
              Gestão de produtos e variantes (SKUs) por vendedor.
            </p>
          </div>
          <div className="flex gap-3">
            <Button asChild variant="outline">
              <Link href="/catalogo/categorias" className="flex items-center gap-2">
                <Tag className="h-4 w-4" />
                Gerir Categorias
              </Link>
            </Button>
            <Button asChild variant="outline">
              <Link href="/catalogo/familias" className="flex items-center gap-2">
                <Settings className="h-4 w-4" />
                Gerir Famílias
              </Link>
            </Button>
          </div>
        </div>

        {error && <ErrorState message={error} onRetry={() => setError(null)} />}

        {loading ? (
          <TableSkeleton />
        ) : (
          <>
            {vendors.length > 1 && (
              <div className="flex items-center gap-3">
                <label className="text-sm font-bold text-on-surface">Vendedor</label>
                <select
                  value={selectedVendorId}
                  onChange={(e) => setSelectedVendorId(e.target.value)}
                  className="h-11 rounded-xl border border-outline-variant bg-white px-4 py-2 text-sm text-on-surface shadow-sm"
                >
                  {vendors.map((v) => (
                    <option key={v.id} value={v.id}>{v.name}</option>
                  ))}
                </select>
              </div>
            )}

            <div className="grid grid-cols-1 gap-5 lg:grid-cols-2">
              {/* Left panel — product list */}
              <Card>
                <CardHeader>
                  <div className="flex items-center justify-between">
                    <CardTitle>Produtos</CardTitle>
                    <Button
                      size="sm"
                      variant={showProductForm ? "outline" : "default"}
                      onClick={() => setShowProductForm((v) => !v)}
                      disabled={!selectedVendorId}
                    >
                      <Plus className="mr-1 h-4 w-4" />
                      {showProductForm ? "Cancelar" : "Novo Produto"}
                    </Button>
                  </div>
                </CardHeader>
                <CardContent className="space-y-4">
                  {showProductForm && (
                    <form
                      className="space-y-3 rounded-xl border border-outline-variant p-4"
                      onSubmit={handleCreateProduct}
                    >
                      <Input
                        placeholder="Nome do produto *"
                        value={productForm.name}
                        onChange={(e) => setProductForm((p) => ({ ...p, name: e.target.value }))}
                        required
                      />
                      <select
                        value={productForm.categoryId}
                        onChange={(e) => setProductForm((p) => ({ ...p, categoryId: e.target.value }))}
                        className="flex h-11 w-full rounded-xl border border-outline-variant bg-white px-4 py-2 text-sm text-on-surface shadow-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
                        required
                      >
                        <option value="">Selecione categoria *</option>
                        {categories.map((c) => (
                          <option key={c.id} value={c.id}>{c.name}</option>
                        ))}
                      </select>
                      <Input
                        placeholder="Descrição"
                        value={productForm.description}
                        onChange={(e) => setProductForm((p) => ({ ...p, description: e.target.value }))}
                      />
                      <Button type="submit" size="sm" disabled={creatingProduct}>
                        {creatingProduct ? "A criar..." : "Criar Produto"}
                      </Button>
                    </form>
                  )}

                  {productsLoading ? (
                    <TableSkeleton />
                  ) : products.length === 0 ? (
                    <EmptyState message="Nenhum produto encontrado para este vendedor." />
                  ) : (
                    <div className="space-y-2">
                      {products.map((product) => (
                        <button
                          key={product.id}
                          onClick={() => setSelectedProduct(product)}
                          className={cn(
                            "flex w-full items-center justify-between rounded-xl border p-3 text-left transition-all",
                            selectedProduct?.id === product.id
                              ? "border-primary bg-primary-fixed"
                              : "border-outline-variant hover:bg-surface-container",
                          )}
                        >
                          <div className="min-w-0">
                            <p className="truncate text-sm font-bold text-on-surface">{product.name}</p>
                            <p className="text-xs text-on-surface-variant">{categoryName(product.categoryId)}</p>
                            <StatusBadge status={product.status || "PENDING"} />
                          </div>
                          <span className="ml-3 flex shrink-0 items-center gap-1 rounded-full bg-surface-container-high px-2 py-0.5 text-xs font-bold text-on-surface-variant">
                            <Tag className="h-3 w-3" />
                            {product.skus.length}
                          </span>
                        </button>
                      ))}
                    </div>
                  )}
                </CardContent>
              </Card>

              {/* Right panel — SKU sub-panel */}
              <Card>
                <CardHeader>
                  <div className="flex items-center justify-between">
                    <CardTitle>
                      {selectedProduct ? `SKUs — ${selectedProduct.name}` : "SKUs"}
                    </CardTitle>
                    {selectedProduct?.status === "PENDING" && (
                      <div className="flex gap-2">
                        <Button
                          size="sm"
                          variant="outline"
                          onClick={() => handleRejectProduct(selectedProduct.id)}
                          disabled={rejectingProduct === selectedProduct.id}
                          className="text-red-600 hover:text-red-700"
                        >
                          {rejectingProduct === selectedProduct.id ? "A rejeitar..." : "Rejeitar"}
                        </Button>
                        <Button
                          size="sm"
                          onClick={() => handleApproveProduct(selectedProduct.id)}
                          disabled={approvingProduct === selectedProduct.id}
                        >
                          {approvingProduct === selectedProduct.id ? "A aprovar..." : "Aprovar"}
                        </Button>
                      </div>
                    )}
                  </div>
                </CardHeader>
                <CardContent className="space-y-4">
                  {!selectedProduct ? (
                    <EmptyState message="Selecione um produto para gerir os seus SKUs." />
                  ) : (
                    <>
                      {selectedProduct.skus.length === 0 ? (
                        <p className="text-sm text-on-surface-variant">Nenhum SKU criado ainda.</p>
                      ) : (
                        <div className="overflow-x-auto">
                          <table className="w-full text-sm">
                            <thead>
                              <tr className="border-b border-outline-variant text-xs font-bold uppercase text-on-surface-variant">
                                <th className="pb-2 pr-4 text-left">Código</th>
                                <th className="pb-2 pr-4 text-left">Nome</th>
                                <th className="pb-2 pr-4 text-right">Preço</th>
                                <th className="pb-2 text-left">Estado</th>
                              </tr>
                            </thead>
                            <tbody>
                              {selectedProduct.skus.map((sku) => (
                                <tr key={sku.id} className="border-b border-outline-variant/50 last:border-0">
                                  <td className="py-2 pr-4 font-mono text-xs text-on-surface-variant">
                                    {sku.skuCode}
                                  </td>
                                  <td className="py-2 pr-4 font-bold text-on-surface">{sku.name}</td>
                                  <td className="py-2 pr-4 text-right">{formatCurrency(sku.price)}</td>
                                  <td className="py-2">
                                    <StatusBadge status={sku.active ? "ACTIVE" : "INACTIVE"} />
                                  </td>
                                </tr>
                              ))}
                            </tbody>
                          </table>
                        </div>
                      )}

                      <form
                        className="space-y-3 rounded-xl border border-outline-variant p-4"
                        onSubmit={handleCreateSku}
                      >
                        <p className="text-sm font-bold text-on-surface">Novo SKU</p>
                        <div className="grid grid-cols-2 gap-3">
                          <Input
                            placeholder="Código SKU *"
                            value={skuForm.skuCode}
                            onChange={(e) => setSkuForm((s) => ({ ...s, skuCode: e.target.value }))}
                            required
                          />
                          <Input
                            placeholder="Nome *"
                            value={skuForm.name}
                            onChange={(e) => setSkuForm((s) => ({ ...s, name: e.target.value }))}
                            required
                          />
                          <Input
                            placeholder="Preço (MZN) *"
                            type="number"
                            min="0"
                            step="0.01"
                            value={skuForm.price}
                            onChange={(e) => setSkuForm((s) => ({ ...s, price: e.target.value }))}
                            required
                          />
                          <Input
                            placeholder="Stock inicial"
                            type="number"
                            min="0"
                            value={skuForm.initialStock}
                            onChange={(e) => setSkuForm((s) => ({ ...s, initialStock: e.target.value }))}
                          />
                        </div>
                        <Button type="submit" size="sm" disabled={creatingSku}>
                          {creatingSku ? "A criar..." : "Criar SKU"}
                        </Button>
                      </form>
                    </>
                  )}
                </CardContent>
              </Card>
            </div>
          </>
        )}
      </main>
    </AppShell>
  );
}
