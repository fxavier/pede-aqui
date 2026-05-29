"use client";

import { useEffect, useState } from "react";
import { AppShell } from "@/components/layout/app-shell";
import { Card, CardContent, CardHeader } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { StatusBadge } from "@/components/ui/status-badge";
import { ErrorState } from "@/components/ui/error-state";
import { EmptyState } from "@/components/ui/empty-state";
import { TableSkeleton } from "@/components/ui/loading-skeleton";
import { marketingService } from "@/lib/api/services";
import { cn, formatCurrency, formatDate } from "@/lib/utils";
import type { Coupon, Promotion } from "@/lib/api/types";

type Tab = "cupoes" | "promocoes";

const DISCOUNT_TYPES = [
  { value: "PERCENTAGE", label: "Percentagem (%)" },
  { value: "FIXED_AMOUNT", label: "Valor fixo (MZN)" },
];

const APPLIES_TO_OPTIONS = [
  { value: "ALL_ORDERS", label: "Todas as encomendas" },
  { value: "FIRST_ORDER", label: "Primeira encomenda" },
  { value: "MIN_AMOUNT", label: "Encomenda mínima" },
];

function formatDiscount(type: string, value: number) {
  return type === "PERCENTAGE" ? `${value}%` : formatCurrency(value);
}

export default function MarketingPage() {
  const [tab, setTab] = useState<Tab>("cupoes");
  const [coupons, setCoupons] = useState<Coupon[]>([]);
  const [promotions, setPromotions] = useState<Promotion[]>([]);
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

  const [showPromotionForm, setShowPromotionForm] = useState(false);
  const [promotionForm, setPromotionForm] = useState({
    name: "",
    description: "",
    discountType: "PERCENTAGE",
    discountValue: "",
    appliesTo: "ALL_ORDERS",
    startsAt: "",
    endsAt: "",
  });
  const [creatingPromotion, setCreatingPromotion] = useState(false);

  useEffect(() => {
    async function load() {
      setLoading(true);
      setError(null);
      try {
        const [cs, ps] = await Promise.all([
          marketingService.listCoupons(),
          marketingService.listPromotions(),
        ]);
        setCoupons(cs);
        setPromotions(ps);
      } catch {
        setError("Erro ao carregar dados de marketing.");
      } finally {
        setLoading(false);
      }
    }
    load();
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

  async function handleCreatePromotion(e: React.FormEvent) {
    e.preventDefault();
    setCreatingPromotion(true);
    setError(null);
    try {
      const created = await marketingService.createPromotion({
        name: promotionForm.name,
        description: promotionForm.description || undefined,
        discountType: promotionForm.discountType,
        discountValue: parseFloat(promotionForm.discountValue),
        appliesTo: promotionForm.appliesTo,
        startsAt: new Date(promotionForm.startsAt).toISOString(),
        endsAt: promotionForm.endsAt ? new Date(promotionForm.endsAt).toISOString() : undefined,
      });
      setPromotions((prev) => [created, ...prev]);
      setPromotionForm({ name: "", description: "", discountType: "PERCENTAGE", discountValue: "", appliesTo: "ALL_ORDERS", startsAt: "", endsAt: "" });
      setShowPromotionForm(false);
    } catch {
      setError("Erro ao criar promoção.");
    } finally {
      setCreatingPromotion(false);
    }
  }

  async function handleDeactivatePromotion(id: string) {
    try {
      const updated = await marketingService.deactivatePromotion(id);
      setPromotions((prev) => prev.map((p) => (p.id === id ? updated : p)));
    } catch {
      setError("Erro ao desactivar promoção.");
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
            <div className="flex items-center justify-between">
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
              <Button
                size="sm"
                onClick={() => {
                  if (tab === "cupoes") setShowCouponForm((v) => !v);
                  else setShowPromotionForm((v) => !v);
                }}
              >
                {tab === "cupoes"
                  ? showCouponForm ? "Cancelar" : "+ Novo Cupão"
                  : showPromotionForm ? "Cancelar" : "+ Nova Promoção"}
              </Button>
            </div>
          </CardHeader>

          <CardContent className="space-y-4">
            {/* ─── COUPONS TAB ─── */}
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

            {/* ─── PROMOTIONS TAB ─── */}
            {tab === "promocoes" && (
              <>
                {showPromotionForm && (
                  <form
                    className="grid grid-cols-1 gap-3 rounded-xl border border-outline-variant p-4 sm:grid-cols-2 lg:grid-cols-3"
                    onSubmit={handleCreatePromotion}
                  >
                    <Input
                      placeholder="Nome da promoção *"
                      value={promotionForm.name}
                      onChange={(e) => setPromotionForm((f) => ({ ...f, name: e.target.value }))}
                      required
                    />
                    <Input
                      placeholder="Descrição"
                      value={promotionForm.description}
                      onChange={(e) => setPromotionForm((f) => ({ ...f, description: e.target.value }))}
                    />
                    <select
                      value={promotionForm.discountType}
                      onChange={(e) => setPromotionForm((f) => ({ ...f, discountType: e.target.value }))}
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
                      value={promotionForm.discountValue}
                      onChange={(e) => setPromotionForm((f) => ({ ...f, discountValue: e.target.value }))}
                      required
                    />
                    <select
                      value={promotionForm.appliesTo}
                      onChange={(e) => setPromotionForm((f) => ({ ...f, appliesTo: e.target.value }))}
                      className="flex h-11 w-full rounded-xl border border-outline-variant bg-white px-4 py-2 text-sm text-on-surface shadow-sm"
                    >
                      {APPLIES_TO_OPTIONS.map((opt) => (
                        <option key={opt.value} value={opt.value}>{opt.label}</option>
                      ))}
                    </select>
                    <div className="space-y-1">
                      <label className="text-xs font-bold text-on-surface-variant">Início *</label>
                      <input
                        type="datetime-local"
                        value={promotionForm.startsAt}
                        onChange={(e) => setPromotionForm((f) => ({ ...f, startsAt: e.target.value }))}
                        className="flex h-11 w-full rounded-xl border border-outline-variant bg-white px-4 py-2 text-sm text-on-surface shadow-sm"
                        required
                      />
                    </div>
                    <div className="space-y-1">
                      <label className="text-xs font-bold text-on-surface-variant">Fim</label>
                      <input
                        type="datetime-local"
                        value={promotionForm.endsAt}
                        onChange={(e) => setPromotionForm((f) => ({ ...f, endsAt: e.target.value }))}
                        className="flex h-11 w-full rounded-xl border border-outline-variant bg-white px-4 py-2 text-sm text-on-surface shadow-sm"
                      />
                    </div>
                    <div className="flex items-end">
                      <Button type="submit" size="sm" disabled={creatingPromotion}>
                        {creatingPromotion ? "A criar..." : "Criar Promoção"}
                      </Button>
                    </div>
                  </form>
                )}

                {loading ? (
                  <TableSkeleton />
                ) : promotions.length === 0 ? (
                  <EmptyState message="Nenhuma promoção criada ainda." />
                ) : (
                  <div className="overflow-x-auto">
                    <table className="w-full text-sm">
                      <thead>
                        <tr className="border-b border-outline-variant text-xs font-bold uppercase text-on-surface-variant">
                          <th className="pb-3 pr-4 text-left">Nome</th>
                          <th className="pb-3 pr-4 text-left">Desconto</th>
                          <th className="pb-3 pr-4 text-left">Aplica-se a</th>
                          <th className="pb-3 pr-4 text-left">Período</th>
                          <th className="pb-3 pr-4 text-left">Estado</th>
                          <th className="pb-3 text-left">Acções</th>
                        </tr>
                      </thead>
                      <tbody>
                        {promotions.map((p) => (
                          <tr key={p.id} className="border-b border-outline-variant/50 last:border-0">
                            <td className="py-3 pr-4 font-bold text-on-surface">{p.name}</td>
                            <td className="py-3 pr-4">{formatDiscount(p.discountType, p.discountValue)}</td>
                            <td className="py-3 pr-4 text-on-surface-variant">
                              {APPLIES_TO_OPTIONS.find((o) => o.value === p.appliesTo)?.label ?? p.appliesTo}
                            </td>
                            <td className="py-3 pr-4 text-on-surface-variant">
                              {formatDate(p.startsAt)}
                              {p.endsAt ? ` → ${formatDate(p.endsAt)}` : " →  ∞"}
                            </td>
                            <td className="py-3 pr-4">
                              <StatusBadge status={p.active ? "ACTIVE" : "INACTIVE"} />
                            </td>
                            <td className="py-3">
                              {p.active && (
                                <Button
                                  variant="outline"
                                  size="sm"
                                  onClick={() => handleDeactivatePromotion(p.id)}
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
          </CardContent>
        </Card>
      </main>
    </AppShell>
  );
}
