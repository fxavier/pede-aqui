"use client";

import { useEffect, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { AppShell } from "@/components/layout/app-shell";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { StatusBadge } from "@/components/ui/status-badge";
import { Badge } from "@/components/ui/badge";
import { ErrorState } from "@/components/ui/error-state";
import { EmptyState } from "@/components/ui/empty-state";
import { TableSkeleton } from "@/components/ui/loading-skeleton";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { vendorService, categoryService, catalogService, uploadService } from "@/lib/api/services";
import { cn, formatCurrency } from "@/lib/utils";
import type { Product } from "@/lib/api/types";
import { Plus, Tag, Settings, Pencil } from "lucide-react";
import Link from "next/link";
import { useAppSelector } from "@/store/hooks";
import { ProductEditDialog } from "@/components/catalogo/product-edit-dialog";
import { PriceModerationQueue } from "@/components/catalogo/price-moderation-queue";
import { extractErrorMessage, productPendingPrice } from "@/components/catalogo/catalog-view";

export default function CatalogoPage() {
  const queryClient = useQueryClient();
  const userRole = useAppSelector((state) => state.auth.user?.role) ?? "";
  const canModerate = userRole === "OPS" || userRole === "ADMIN";

  const [tab, setTab] = useState<"produtos" | "moderacao">("produtos");
  const [selectedVendorId, setSelectedVendorId] = useState("");
  const [selectedProduct, setSelectedProduct] = useState<Product | null>(null);
  const [editingProduct, setEditingProduct] = useState<Product | null>(null);
  const [mutationError, setMutationError] = useState<string | null>(null);

  const [showProductForm, setShowProductForm] = useState(false);
  const [productForm, setProductForm] = useState({ name: "", categoryId: "", description: "", imageFile: null as File | null, imagePreview: "" });
  const [imagePreviews, setImagePreviews] = useState<Record<string, string>>({});

  const [skuForm, setSkuForm] = useState({ skuCode: "", name: "", price: "", initialStock: "0" });

  const vendorsQuery = useQuery({
    queryKey: ["catalog", "vendors"],
    queryFn: () => vendorService.list(),
  });
  const categoriesQuery = useQuery({
    queryKey: ["catalog", "categories"],
    queryFn: () => categoryService.list(),
  });

  const vendors = vendorsQuery.data ?? [];
  const categories = categoriesQuery.data ?? [];

  useEffect(() => {
    if (!selectedVendorId && vendorsQuery.data && vendorsQuery.data.length > 0) {
      setSelectedVendorId(vendorsQuery.data[0].id);
    }
  }, [vendorsQuery.data, selectedVendorId]);

  const productsQuery = useQuery({
    queryKey: ["catalog", "products", selectedVendorId],
    queryFn: () => catalogService.listVendorProducts(selectedVendorId),
    enabled: !!selectedVendorId,
  });
  const products = productsQuery.data ?? [];

  // Keep the SKU sub-panel selection in sync as products refetch (e.g. after price moderation).
  useEffect(() => {
    if (!selectedProduct) return;
    const list = productsQuery.data ?? [];
    const fresh = list.find((p) => p.id === selectedProduct.id);
    if (fresh && fresh !== selectedProduct) setSelectedProduct(fresh);
    if (!fresh) setSelectedProduct(null);
  }, [productsQuery.data, selectedProduct]);

  const createProductMutation = useMutation({
    mutationFn: async () => {
      let primaryImageKey: string | undefined;
      if (productForm.imageFile) {
        const { uploadUrl, storageKey } = await uploadService.getPresignedUrl({
          purpose: "product_image",
          fileName: productForm.imageFile.name,
          contentType: productForm.imageFile.type,
        });
        await uploadService.uploadToS3(uploadUrl, productForm.imageFile);
        primaryImageKey = storageKey;
      }
      return catalogService.createProduct({
        vendorId: selectedVendorId,
        categoryId: productForm.categoryId,
        name: productForm.name,
        description: productForm.description || undefined,
        primaryImageKey,
      });
    },
    onSuccess: (created) => {
      if (productForm.imagePreview) {
        setImagePreviews((prev) => ({ ...prev, [created.id]: productForm.imagePreview }));
      }
      setProductForm({ name: "", categoryId: "", description: "", imageFile: null, imagePreview: "" });
      setShowProductForm(false);
      setMutationError(null);
      queryClient.invalidateQueries({ queryKey: ["catalog", "products", selectedVendorId] });
    },
    onError: (err) => setMutationError(extractErrorMessage(err, "Erro ao criar produto.")),
  });

  const createSkuMutation = useMutation({
    mutationFn: () =>
      catalogService.createSku({
        productId: selectedProduct!.id,
        vendorId: selectedVendorId,
        skuCode: skuForm.skuCode,
        name: skuForm.name,
        price: parseFloat(skuForm.price),
        initialStock: parseInt(skuForm.initialStock, 10) || 0,
      }),
    onSuccess: () => {
      setSkuForm({ skuCode: "", name: "", price: "", initialStock: "0" });
      setMutationError(null);
      queryClient.invalidateQueries({ queryKey: ["catalog", "products", selectedVendorId] });
    },
    onError: (err) => setMutationError(extractErrorMessage(err, "Erro ao criar SKU.")),
  });

  const approveProductMutation = useMutation({
    mutationFn: (productId: string) => catalogService.approveProduct(productId),
    onSuccess: () => {
      setMutationError(null);
      queryClient.invalidateQueries({ queryKey: ["catalog", "products", selectedVendorId] });
    },
    onError: (err) => setMutationError(extractErrorMessage(err, "Erro ao aprovar produto.")),
  });

  const rejectProductMutation = useMutation({
    mutationFn: (productId: string) => catalogService.rejectProduct(productId),
    onSuccess: () => {
      setMutationError(null);
      queryClient.invalidateQueries({ queryKey: ["catalog", "products", selectedVendorId] });
    },
    onError: (err) => setMutationError(extractErrorMessage(err, "Erro ao rejeitar produto.")),
  });

  const categoryName = (id: string) => categories.find((c) => c.id === id)?.name ?? "—";

  const initialLoading = vendorsQuery.isLoading || categoriesQuery.isLoading;
  const initialError = vendorsQuery.isError || categoriesQuery.isError;

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

        {mutationError && <ErrorState message={mutationError} onRetry={() => setMutationError(null)} />}

        {initialLoading ? (
          <TableSkeleton />
        ) : initialError ? (
          <ErrorState
            message="Erro ao carregar vendedores e categorias."
            onRetry={() => {
              vendorsQuery.refetch();
              categoriesQuery.refetch();
            }}
          />
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

            <Tabs value={tab} onValueChange={(v) => setTab(v as "produtos" | "moderacao")}>
              <TabsList>
                <TabsTrigger value="produtos">Produtos</TabsTrigger>
                {canModerate && <TabsTrigger value="moderacao">Moderação de Preços</TabsTrigger>}
              </TabsList>

              <TabsContent value="produtos">
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
                          onSubmit={(e) => {
                            e.preventDefault();
                            createProductMutation.mutate();
                          }}
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
                          <div>
                            <label className="mb-1.5 block text-xs font-semibold text-on-surface-variant">
                              Imagem do produto
                            </label>
                            {productForm.imagePreview && (
                              <img
                                src={productForm.imagePreview}
                                alt="preview"
                                className="mb-2 h-24 w-24 rounded-lg object-cover border border-outline-variant"
                              />
                            )}
                            <input
                              type="file"
                              accept="image/*"
                              className="block w-full text-sm text-on-surface-variant file:mr-3 file:cursor-pointer file:rounded-lg file:border-0 file:bg-surface-container file:px-3 file:py-1.5 file:text-xs file:font-semibold"
                              onChange={(e) => {
                                const file = e.target.files?.[0] ?? null;
                                const preview = file ? URL.createObjectURL(file) : "";
                                setProductForm((p) => ({ ...p, imageFile: file, imagePreview: preview }));
                              }}
                            />
                          </div>
                          <Button type="submit" size="sm" disabled={createProductMutation.isPending}>
                            {createProductMutation.isPending ? "A criar..." : "Criar Produto"}
                          </Button>
                        </form>
                      )}

                      {productsQuery.isLoading ? (
                        <TableSkeleton />
                      ) : productsQuery.isError ? (
                        <ErrorState message="Erro ao carregar produtos." onRetry={() => productsQuery.refetch()} />
                      ) : products.length === 0 ? (
                        <EmptyState message="Nenhum produto encontrado para este vendedor." />
                      ) : (
                        <div className="space-y-2">
                          {products.map((product) => {
                            const pending = productPendingPrice(product);
                            return (
                              <div
                                key={product.id}
                                className={cn(
                                  "flex w-full items-center justify-between rounded-xl border p-3 text-left transition-all",
                                  selectedProduct?.id === product.id
                                    ? "border-primary bg-primary-fixed"
                                    : "border-outline-variant hover:bg-surface-container",
                                )}
                              >
                                <button
                                  type="button"
                                  onClick={() => setSelectedProduct(product)}
                                  className="flex min-w-0 flex-1 items-center gap-3 text-left"
                                >
                                  {imagePreviews[product.id] ? (
                                    <img
                                      src={imagePreviews[product.id]}
                                      alt={product.name}
                                      className="h-10 w-10 shrink-0 rounded-lg object-cover border border-outline-variant"
                                    />
                                  ) : (
                                    <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-surface-container-high border border-outline-variant">
                                      <Tag className="h-4 w-4 text-on-surface-variant/40" />
                                    </div>
                                  )}
                                  <div className="min-w-0">
                                    <p className="truncate text-sm font-bold text-on-surface">{product.name}</p>
                                    <p className="text-xs text-on-surface-variant">{categoryName(product.categoryId)}</p>
                                    <div className="mt-1 flex flex-wrap items-center gap-1.5">
                                      <StatusBadge status={product.status || "PENDING"} />
                                      {pending && (
                                        <Badge variant="outline" className="border-yellow-300 bg-yellow-50 text-yellow-800">
                                          Preço pendente: {formatCurrency(pending.pendingPrice)}
                                        </Badge>
                                      )}
                                    </div>
                                  </div>
                                </button>
                                <div className="ml-3 flex shrink-0 items-center gap-2">
                                  <span className="flex items-center gap-1 rounded-full bg-surface-container-high px-2 py-0.5 text-xs font-bold text-on-surface-variant">
                                    <Tag className="h-3 w-3" />
                                    {product.skus.length}
                                  </span>
                                  <Button
                                    size="sm"
                                    variant="outline"
                                    onClick={() => setEditingProduct(product)}
                                    aria-label={`Editar ${product.name}`}
                                  >
                                    <Pencil className="h-3.5 w-3.5" />
                                  </Button>
                                </div>
                              </div>
                            );
                          })}
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
                              onClick={() => rejectProductMutation.mutate(selectedProduct.id)}
                              disabled={rejectProductMutation.isPending}
                              className="text-red-600 hover:text-red-700"
                            >
                              {rejectProductMutation.isPending ? "A rejeitar..." : "Rejeitar"}
                            </Button>
                            <Button
                              size="sm"
                              onClick={() => approveProductMutation.mutate(selectedProduct.id)}
                              disabled={approveProductMutation.isPending}
                            >
                              {approveProductMutation.isPending ? "A aprovar..." : "Aprovar"}
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
                            onSubmit={(e) => {
                              e.preventDefault();
                              createSkuMutation.mutate();
                            }}
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
                            <Button type="submit" size="sm" disabled={createSkuMutation.isPending}>
                              {createSkuMutation.isPending ? "A criar..." : "Criar SKU"}
                            </Button>
                          </form>
                        </>
                      )}
                    </CardContent>
                  </Card>
                </div>
              </TabsContent>

              {canModerate && (
                <TabsContent value="moderacao">
                  <PriceModerationQueue />
                </TabsContent>
              )}
            </Tabs>
          </>
        )}

        <ProductEditDialog
          product={editingProduct}
          categories={categories}
          open={editingProduct !== null}
          onOpenChange={(open) => { if (!open) setEditingProduct(null); }}
        />
      </main>
    </AppShell>
  );
}
