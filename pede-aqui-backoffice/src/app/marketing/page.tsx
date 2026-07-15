"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { AppShell } from "@/components/layout/app-shell";
import { Card, CardContent, CardHeader } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Badge } from "@/components/ui/badge";
import { StatusBadge } from "@/components/ui/status-badge";
import { ErrorState } from "@/components/ui/error-state";
import { EmptyState } from "@/components/ui/empty-state";
import { TableSkeleton } from "@/components/ui/loading-skeleton";
import { PromotionStatusBadge } from "@/components/marketing/promotion-status-badge";
import { PromotionFormDialog } from "@/components/marketing/promotion-form-dialog";
import { parseApiError } from "@/components/marketing/promotion-errors";
import { marketingService, promotionService, vendorService, categoryService } from "@/lib/api/services";
import { useAppSelector } from "@/store/hooks";
import { cn, formatCurrency, formatDate } from "@/lib/utils";
import type { Coupon, PromotionResponse, PromotionStatus, Vendor, Category } from "@/lib/api/types";
import { Lock, Pencil } from "lucide-react";

type Tab = "cupoes" | "promocoes";

const DISCOUNT_TYPES = [
  { value: "PERCENTAGE", label: "Percentagem (%)" },
  { value: "FIXED_AMOUNT", label: "Valor fixo (MZN)" },
];

const STATUS_FILTERS: { value: PromotionStatus | "ALL"; label: string }[] = [
  { value: "ALL", label: "Todos os estados" },
  { value: "DRAFT", label: "Rascunho" },
  { value: "ACTIVE", label: "Activa" },
  { value: "PAUSED", label: "Em Pausa" },
  { value: "EXPIRED", label: "Expirada" },
];

function formatDiscount(type: string, value: number) {
  return type === "PERCENTAGE" ? `${value}%` : formatCurrency(value);
}

function formatPromotionValue(promo: PromotionResponse) {
  const base = formatDiscount(promo.type, promo.value);
  return promo.maxDiscountAmount != null ? `${base} (máx. ${formatCurrency(promo.maxDiscountAmount)})` : base;
}

export default function MarketingPage() {
  const role = useAppSelector((state) => state.auth.user?.role) ?? "";
  const isVendorAdmin = role === "VENDOR_ADMIN";
  // Spec 002 §3: promotion CRUD is VENDOR_ADMIN (own vendor) / OPS / ADMIN only.
  const canManagePromotions = role === "ADMIN" || role === "OPS" || role === "VENDOR_ADMIN";
  // Tenant-wide (vendor_id = null) promotions are OPS/ADMIN-only (AC-7.2).
  const canSetTenantWide = role === "ADMIN" || role === "OPS";

  const [tab, setTab] = useState<Tab>("cupoes");

  // ─── Coupons (legacy marketingService — untouched by Spec 002) ───
  const [coupons, setCoupons] = useState<Coupon[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [showCouponForm, setShowCouponForm] = useState(false);
  const [couponForm, setCouponForm] = useState({
    code: "",
    discountType: "PERCENTAGE",
    discountValue: "",
    minOrderAmount: "",
    maxUses: "",
    validFrom: "",
    validUntil: "",
  });
  const [creatingCoupon, setCreatingCoupon] = useState(false);

  useEffect(() => {
    async function loadCoupons() {
      setLoading(true);
      setError(null);
      try {
        setCoupons(await marketingService.listCoupons());
      } catch {
        setError("Erro ao carregar cupões.");
      } finally {
        setLoading(false);
      }
    }
    loadCoupons();
  }, []);

  async function handleCreateCoupon(e: React.FormEvent) {
    e.preventDefault();
    setCreatingCoupon(true);
    setError(null);
    try {
      const created = await marketingService.createCoupon({
        code: couponForm.code,
        discountType: couponForm.discountType,
        discountValue: parseFloat(couponForm.discountValue),
        minOrderAmount: couponForm.minOrderAmount ? parseFloat(couponForm.minOrderAmount) : undefined,
        maxUses: couponForm.maxUses ? parseInt(couponForm.maxUses, 10) : undefined,
        validFrom: new Date(couponForm.validFrom).toISOString(),
        validUntil: couponForm.validUntil ? new Date(couponForm.validUntil).toISOString() : undefined,
      });
      setCoupons((prev) => [created, ...prev]);
      setCouponForm({ code: "", discountType: "PERCENTAGE", discountValue: "", minOrderAmount: "", maxUses: "", validFrom: "", validUntil: "" });
      setShowCouponForm(false);
    } catch {
      setError("Erro ao criar cupão.");
    } finally {
      setCreatingCoupon(false);
    }
  }

  async function handleDeactivateCoupon(id: string) {
    try {
      const updated = await marketingService.deactivateCoupon(id);
      setCoupons((prev) => prev.map((c) => (c.id === id ? updated : c)));
    } catch {
      setError("Erro ao desactivar cupão.");
    }
  }

  // ─── Promotions (Spec 002 US-7 — promotionService) ───
  const [vendors, setVendors] = useState<Vendor[]>([]);
  const [categories, setCategories] = useState<Category[]>([]);
  const [refDataError, setRefDataError] = useState<string | null>(null);

  const [promotions, setPromotions] = useState<PromotionResponse[]>([]);
  const [promotionsLoading, setPromotionsLoading] = useState(true);
  const [promotionsError, setPromotionsError] = useState<string | null>(null);
  const [statusFilter, setStatusFilter] = useState<PromotionStatus | "ALL">("ALL");

  const [dialogOpen, setDialogOpen] = useState(false);
  const [editingPromotion, setEditingPromotion] = useState<PromotionResponse | null>(null);

  const [rowActionId, setRowActionId] = useState<string | null>(null);
  const [rowActionError, setRowActionError] = useState<string | null>(null);

  const myVendorId = useMemo(
    () => (isVendorAdmin ? vendors[0]?.id ?? null : null),
    [isVendorAdmin, vendors],
  );

  const loadPromotions = useCallback(async () => {
    if (!canManagePromotions) return;
    setPromotionsLoading(true);
    setPromotionsError(null);
    try {
      const params = statusFilter === "ALL" ? {} : { status: statusFilter };
      setPromotions(await promotionService.list(params));
    } catch (err) {
      setPromotionsError(parseApiError(err, "Erro ao carregar promoções.").message);
    } finally {
      setPromotionsLoading(false);
    }
  }, [canManagePromotions, statusFilter]);

  useEffect(() => {
    loadPromotions();
  }, [loadPromotions]);

  useEffect(() => {
    if (!canManagePromotions) return;
    Promise.all([vendorService.list(), categoryService.list()])
      .then(([vs, cats]) => {
        setVendors(vs);
        setCategories(cats);
      })
      .catch(() => setRefDataError("Erro ao carregar vendedores/categorias para o formulário."));
  }, [canManagePromotions]);

  function targetLabel(promo: PromotionResponse): string {
    if (promo.scope === "ORDER") return "Encomenda (total)";
    if (promo.scope === "CATEGORY") {
      const cat = categories.find((c) => c.id === promo.targetCategoryId);
      return `Categoria: ${cat?.name ?? (promo.targetCategoryId ? `${promo.targetCategoryId.slice(0, 8)}…` : "—")}`;
    }
    return `Produto: ${promo.targetProductId ? `${promo.targetProductId.slice(0, 8)}…` : "—"}`;
  }

  function vendorLabel(promo: PromotionResponse): string {
    if (!promo.vendorId) return "Toda a plataforma";
    return vendors.find((v) => v.id === promo.vendorId)?.name ?? "—";
  }

  function openCreate() {
    setEditingPromotion(null);
    setDialogOpen(true);
  }

  function openEdit(promo: PromotionResponse) {
    setEditingPromotion(promo);
    setDialogOpen(true);
  }

  function handlePromotionSaved(saved: PromotionResponse) {
    setPromotions((prev) => {
      const exists = prev.some((p) => p.id === saved.id);
      return exists ? prev.map((p) => (p.id === saved.id ? saved : p)) : [saved, ...prev];
    });
  }

  async function handleActivate(promo: PromotionResponse) {
    if (!confirm(`Activar a promoção "${promo.name}"?`)) return;
    setRowActionId(promo.id);
    setRowActionError(null);
    try {
      const updated = await promotionService.activate(promo.id);
      setPromotions((prev) => prev.map((p) => (p.id === updated.id ? updated : p)));
    } catch (err) {
      setRowActionError(parseApiError(err, "Erro ao activar promoção.").message);
    } finally {
      setRowActionId(null);
    }
  }

  async function handlePause(promo: PromotionResponse) {
    if (!confirm(`Pausar a promoção "${promo.name}"?`)) return;
    setRowActionId(promo.id);
    setRowActionError(null);
    try {
      const updated = await promotionService.pause(promo.id);
      setPromotions((prev) => prev.map((p) => (p.id === updated.id ? updated : p)));
    } catch (err) {
      setRowActionError(parseApiError(err, "Erro ao pausar promoção.").message);
    } finally {
      setRowActionId(null);
    }
  }

  async function handleDelete(promo: PromotionResponse) {
    if (!confirm(`Eliminar definitivamente a promoção "${promo.name}"? Esta acção não pode ser revertida.`)) return;
    setRowActionId(promo.id);
    setRowActionError(null);
    try {
      await promotionService.delete(promo.id);
      setPromotions((prev) => prev.filter((p) => p.id !== promo.id));
    } catch (err) {
      setRowActionError(parseApiError(err, "Erro ao eliminar promoção.").message);
    } finally {
      setRowActionId(null);
    }
  }

  const tabs: { key: Tab; label: string }[] = [
    { key: "cupoes", label: "Cupões" },
    { key: "promocoes", label: "Promoções" },
  ];

  return (
    <AppShell>
      <main className="space-y-6 p-4 md:p-8">
        <div>
          <h1 className="font-headline-lg text-headline-lg text-on-surface">Marketing</h1>
          <p className="mt-1 text-body-md text-on-surface-variant">
            Gestão de cupões de desconto e promoções automáticas.
          </p>
        </div>

        {error && <ErrorState message={error} onRetry={() => setError(null)} />}

        <Card>
          <CardHeader>
            <div className="flex flex-wrap items-center justify-between gap-3">
              <div className="flex w-fit gap-1 rounded-xl bg-surface-container-low p-1">
                {tabs.map((t) => (
                  <button
                    key={t.key}
                    onClick={() => setTab(t.key)}
                    className={cn(
                      "rounded-lg px-4 py-1.5 text-sm font-bold transition-all",
                      tab === t.key
                        ? "bg-white text-on-surface shadow-sm"
                        : "text-on-surface-variant hover:text-on-surface",
                    )}
                  >
                    {t.label}
                  </button>
                ))}
              </div>

              {tab === "cupoes" && (
                <Button size="sm" onClick={() => setShowCouponForm((v) => !v)}>
                  {showCouponForm ? "Cancelar" : "+ Novo Cupão"}
                </Button>
              )}
              {tab === "promocoes" && canManagePromotions && (
                <Button size="sm" onClick={openCreate}>
                  + Nova Promoção
                </Button>
              )}
            </div>
          </CardHeader>

          <CardContent className="space-y-4">
            {/* ─── COUPONS TAB (legacy marketingService, unchanged) ─── */}
            {tab === "cupoes" && (
              <>
                {showCouponForm && (
                  <form
                    className="grid grid-cols-1 gap-3 rounded-xl border border-outline-variant p-4 sm:grid-cols-2 lg:grid-cols-3"
                    onSubmit={handleCreateCoupon}
                  >
                    <Input
                      placeholder="Código (ex. BOAS10) *"
                      value={couponForm.code}
                      onChange={(e) => setCouponForm((f) => ({ ...f, code: e.target.value.toUpperCase() }))}
                      required
                    />
                    <select
                      value={couponForm.discountType}
                      onChange={(e) => setCouponForm((f) => ({ ...f, discountType: e.target.value }))}
                      className="flex h-11 w-full rounded-xl border border-outline-variant bg-white px-4 py-2 text-sm text-on-surface shadow-sm"
                    >
                      {DISCOUNT_TYPES.map((dt) => (
                        <option key={dt.value} value={dt.value}>{dt.label}</option>
                      ))}
                    </select>
                    <Input
                      placeholder="Valor do desconto *"
                      type="number"
                      min="0.01"
                      step="0.01"
                      value={couponForm.discountValue}
                      onChange={(e) => setCouponForm((f) => ({ ...f, discountValue: e.target.value }))}
                      required
                    />
                    <Input
                      placeholder="Valor mínimo de encomenda"
                      type="number"
                      min="0"
                      step="0.01"
                      value={couponForm.minOrderAmount}
                      onChange={(e) => setCouponForm((f) => ({ ...f, minOrderAmount: e.target.value }))}
                    />
                    <Input
                      placeholder="Nº máximo de usos"
                      type="number"
                      min="1"
                      value={couponForm.maxUses}
                      onChange={(e) => setCouponForm((f) => ({ ...f, maxUses: e.target.value }))}
                    />
                    <div className="space-y-1">
                      <label className="text-xs font-bold text-on-surface-variant">Válido a partir de *</label>
                      <input
                        type="datetime-local"
                        value={couponForm.validFrom}
                        onChange={(e) => setCouponForm((f) => ({ ...f, validFrom: e.target.value }))}
                        className="flex h-11 w-full rounded-xl border border-outline-variant bg-white px-4 py-2 text-sm text-on-surface shadow-sm"
                        required
                      />
                    </div>
                    <div className="space-y-1">
                      <label className="text-xs font-bold text-on-surface-variant">Válido até</label>
                      <input
                        type="datetime-local"
                        value={couponForm.validUntil}
                        onChange={(e) => setCouponForm((f) => ({ ...f, validUntil: e.target.value }))}
                        className="flex h-11 w-full rounded-xl border border-outline-variant bg-white px-4 py-2 text-sm text-on-surface shadow-sm"
                      />
                    </div>
                    <div className="flex items-end">
                      <Button type="submit" size="sm" disabled={creatingCoupon}>
                        {creatingCoupon ? "A criar..." : "Criar Cupão"}
                      </Button>
                    </div>
                  </form>
                )}

                {loading ? (
                  <TableSkeleton />
                ) : coupons.length === 0 ? (
                  <EmptyState message="Nenhum cupão criado ainda." />
                ) : (
                  <div className="overflow-x-auto">
                    <table className="w-full text-sm">
                      <thead>
                        <tr className="border-b border-outline-variant text-xs font-bold uppercase text-on-surface-variant">
                          <th className="pb-3 pr-4 text-left">Código</th>
                          <th className="pb-3 pr-4 text-left">Desconto</th>
                          <th className="pb-3 pr-4 text-left">Usos</th>
                          <th className="pb-3 pr-4 text-left">Válido até</th>
                          <th className="pb-3 pr-4 text-left">Estado</th>
                          <th className="pb-3 text-left">Acções</th>
                        </tr>
                      </thead>
                      <tbody>
                        {coupons.map((c) => (
                          <tr key={c.id} className="border-b border-outline-variant/50 last:border-0">
                            <td className="py-3 pr-4 font-mono font-bold text-on-surface">{c.code}</td>
                            <td className="py-3 pr-4">{formatDiscount(c.discountType, c.discountValue)}</td>
                            <td className="py-3 pr-4 text-on-surface-variant">
                              {c.usesCount}{c.maxUses ? ` / ${c.maxUses}` : ""}
                            </td>
                            <td className="py-3 pr-4 text-on-surface-variant">
                              {c.validUntil ? formatDate(c.validUntil) : "—"}
                            </td>
                            <td className="py-3 pr-4">
                              <StatusBadge status={c.active ? "ACTIVE" : "INACTIVE"} />
                            </td>
                            <td className="py-3">
                              {c.active && (
                                <Button
                                  variant="outline"
                                  size="sm"
                                  onClick={() => handleDeactivateCoupon(c.id)}
                                >
                                  Desactivar
                                </Button>
                              )}
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                )}
              </>
            )}

            {/* ─── PROMOTIONS TAB (Spec 002 US-7 — promotionService) ─── */}
            {tab === "promocoes" && (
              <>
                {!canManagePromotions ? (
                  <div className="flex flex-col items-center justify-center rounded-2xl border border-outline-variant bg-white p-12 text-center">
                    <div className="mb-4 flex h-16 w-16 items-center justify-center rounded-full bg-error-container">
                      <Lock className="h-8 w-8 text-error" />
                    </div>
                    <h3 className="font-headline-sm text-headline-sm text-on-surface">Sem permissão</h3>
                    <p className="mt-2 max-w-md text-body-md text-on-surface-variant">
                      A gestão de promoções está disponível apenas para Administradores, Operações e Administradores de Vendedor.
                    </p>
                  </div>
                ) : (
                  <>
                    {isVendorAdmin && !promotionsLoading && !myVendorId && (
                      <p className="rounded-lg bg-error-container px-3 py-2 text-sm text-on-error-container">
                        Nenhum vendedor associado à sua conta — não é possível criar promoções.
                      </p>
                    )}
                    {refDataError && <p className="text-sm text-error">{refDataError}</p>}
                    {rowActionError && (
                      <p className="rounded-lg bg-error-container px-3 py-2 text-sm text-on-error-container">{rowActionError}</p>
                    )}

                    <div className="flex items-center gap-3">
                      <label className="text-xs font-bold uppercase text-on-surface-variant">Estado</label>
                      <select
                        value={statusFilter}
                        onChange={(e) => setStatusFilter(e.target.value as PromotionStatus | "ALL")}
                        className="h-10 rounded-xl border border-outline-variant bg-white px-3 py-1.5 text-sm text-on-surface shadow-sm"
                      >
                        {STATUS_FILTERS.map((s) => (
                          <option key={s.value} value={s.value}>{s.label}</option>
                        ))}
                      </select>
                    </div>

                    {promotionsLoading ? (
                      <TableSkeleton />
                    ) : promotionsError ? (
                      <ErrorState message={promotionsError} onRetry={loadPromotions} />
                    ) : promotions.length === 0 ? (
                      <EmptyState message="Nenhuma promoção encontrada para este filtro." />
                    ) : (
                      <div className="overflow-x-auto">
                        <table className="w-full text-sm">
                          <thead>
                            <tr className="border-b border-outline-variant text-xs font-bold uppercase text-on-surface-variant">
                              <th className="pb-3 pr-4 text-left">Nome</th>
                              <th className="pb-3 pr-4 text-left">Desconto</th>
                              <th className="pb-3 pr-4 text-left">Âmbito</th>
                              {canSetTenantWide && <th className="pb-3 pr-4 text-left">Vendedor</th>}
                              <th className="pb-3 pr-4 text-left">Período</th>
                              <th className="pb-3 pr-4 text-left">Usos</th>
                              <th className="pb-3 pr-4 text-left">Estado</th>
                              <th className="pb-3 text-left">Acções</th>
                            </tr>
                          </thead>
                          <tbody>
                            {promotions.map((p) => (
                              <tr key={p.id} className="border-b border-outline-variant/50 last:border-0">
                                <td className="py-3 pr-4">
                                  <p className="font-bold text-on-surface">{p.name}</p>
                                  {p.code ? (
                                    <Badge variant="outline" className="mt-1 font-mono">{p.code}</Badge>
                                  ) : (
                                    <Badge variant="secondary" className="mt-1">Automática</Badge>
                                  )}
                                </td>
                                <td className="py-3 pr-4 text-on-surface-variant">{formatPromotionValue(p)}</td>
                                <td className="py-3 pr-4 text-on-surface-variant">{targetLabel(p)}</td>
                                {canSetTenantWide && (
                                  <td className="py-3 pr-4 text-on-surface-variant">{vendorLabel(p)}</td>
                                )}
                                <td className="py-3 pr-4 text-on-surface-variant">
                                  {formatDate(p.startsAt)} → {formatDate(p.endsAt)}
                                </td>
                                <td className="py-3 pr-4 text-on-surface-variant">
                                  {p.usedCount}{p.usageLimit ? ` / ${p.usageLimit}` : ""}
                                </td>
                                <td className="py-3 pr-4">
                                  <PromotionStatusBadge status={p.status} />
                                </td>
                                <td className="py-3">
                                  <div className="flex flex-wrap gap-2">
                                    <Button variant="outline" size="sm" onClick={() => openEdit(p)}>
                                      <Pencil className="h-3.5 w-3.5" />
                                    </Button>
                                    {(p.status === "DRAFT" || p.status === "PAUSED") && (
                                      <Button
                                        variant="outline"
                                        size="sm"
                                        disabled={rowActionId === p.id}
                                        onClick={() => handleActivate(p)}
                                      >
                                        Activar
                                      </Button>
                                    )}
                                    {p.status === "ACTIVE" && (
                                      <Button
                                        variant="outline"
                                        size="sm"
                                        disabled={rowActionId === p.id}
                                        onClick={() => handlePause(p)}
                                      >
                                        Pausar
                                      </Button>
                                    )}
                                    {(p.status === "DRAFT" || p.status === "PAUSED") && (
                                      <Button
                                        variant="outline"
                                        size="sm"
                                        className="text-red-600 hover:text-red-700"
                                        disabled={rowActionId === p.id}
                                        onClick={() => handleDelete(p)}
                                      >
                                        Eliminar
                                      </Button>
                                    )}
                                  </div>
                                </td>
                              </tr>
                            ))}
                          </tbody>
                        </table>
                      </div>
                    )}
                  </>
                )}
              </>
            )}
          </CardContent>
        </Card>

        {canManagePromotions && (
          <PromotionFormDialog
            open={dialogOpen}
            onOpenChange={setDialogOpen}
            editing={editingPromotion}
            isVendorAdmin={isVendorAdmin}
            canSetTenantWide={canSetTenantWide}
            myVendorId={myVendorId}
            vendors={vendors}
            categories={categories}
            onSaved={handlePromotionSaved}
          />
        )}
      </main>
    </AppShell>
  );
}
