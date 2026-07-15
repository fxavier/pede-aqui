"use client";

import { useEffect, useState } from "react";
import {
  Dialog, DialogContent, DialogHeader, DialogTitle, DialogDescription, DialogFooter,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { promotionService, catalogService } from "@/lib/api/services";
import type {
  PromotionResponse,
  PromotionUpsertRequest,
  PromotionType,
  PromotionScope,
  Vendor,
  Category,
  Product,
} from "@/lib/api/types";
import { parseApiError } from "./promotion-errors";

const TYPE_OPTIONS: { value: PromotionType; label: string }[] = [
  { value: "PERCENTAGE", label: "Percentagem (%)" },
  { value: "FIXED_AMOUNT", label: "Valor fixo (MZN)" },
];

const SCOPE_OPTIONS: { value: PromotionScope; label: string }[] = [
  { value: "ORDER", label: "Encomenda (total)" },
  { value: "CATEGORY", label: "Categoria" },
  { value: "PRODUCT", label: "Produto" },
];

interface FormState {
  name: string;
  code: string;
  type: PromotionType;
  value: string;
  scope: PromotionScope;
  targetCategoryId: string;
  targetProductId: string;
  minOrderTotal: string;
  maxDiscountAmount: string;
  startsAt: string;
  endsAt: string;
  usageLimit: string;
  perCustomerLimit: string;
  vendorId: string; // "" = tenant-wide (OPS/ADMIN only)
}

const EMPTY_FORM: FormState = {
  name: "",
  code: "",
  type: "PERCENTAGE",
  value: "",
  scope: "ORDER",
  targetCategoryId: "",
  targetProductId: "",
  minOrderTotal: "",
  maxDiscountAmount: "",
  startsAt: "",
  endsAt: "",
  usageLimit: "",
  perCustomerLimit: "",
  vendorId: "",
};

/** ISO 8601 -> value accepted by <input type="datetime-local">. */
function toDatetimeLocal(iso: string): string {
  if (!iso) return "";
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return "";
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

const inputSelectClass =
  "flex h-11 w-full rounded-xl border border-outline-variant bg-white px-4 py-2 text-sm text-on-surface shadow-sm disabled:cursor-not-allowed disabled:opacity-60";

interface PromotionFormDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  editing: PromotionResponse | null;
  isVendorAdmin: boolean;
  canSetTenantWide: boolean;
  myVendorId: string | null;
  vendors: Vendor[];
  categories: Category[];
  onSaved: (promotion: PromotionResponse) => void;
}

/**
 * Spec 002 (US-7) create/edit form for `promotionService`. Client-side validation
 * mirrors AC-7.4; backend 400 `ProblemDetail.errors` are surfaced per-field.
 */
export function PromotionFormDialog({
  open,
  onOpenChange,
  editing,
  isVendorAdmin,
  canSetTenantWide,
  myVendorId,
  vendors,
  categories,
  onSaved,
}: PromotionFormDialogProps) {
  const [form, setForm] = useState<FormState>(EMPTY_FORM);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  // Products are fetched per-vendor (no tenant-wide product listing endpoint exists),
  // so the target-product select browses a chosen vendor's catalogue independently
  // of the promotion's own (possibly tenant-wide) vendorId.
  const [browseVendorId, setBrowseVendorId] = useState("");
  const [products, setProducts] = useState<Product[]>([]);
  const [productsLoading, setProductsLoading] = useState(false);

  useEffect(() => {
    if (!open) return;
    setFieldErrors({});
    setFormError(null);
    if (editing) {
      setForm({
        name: editing.name,
        code: editing.code ?? "",
        type: editing.type,
        value: String(editing.value),
        scope: editing.scope,
        targetCategoryId: editing.targetCategoryId ?? "",
        targetProductId: editing.targetProductId ?? "",
        minOrderTotal: editing.minOrderTotal != null ? String(editing.minOrderTotal) : "",
        maxDiscountAmount: editing.maxDiscountAmount != null ? String(editing.maxDiscountAmount) : "",
        startsAt: toDatetimeLocal(editing.startsAt),
        endsAt: toDatetimeLocal(editing.endsAt),
        usageLimit: editing.usageLimit != null ? String(editing.usageLimit) : "",
        perCustomerLimit: editing.perCustomerLimit != null ? String(editing.perCustomerLimit) : "",
        vendorId: editing.vendorId ?? "",
      });
      setBrowseVendorId(editing.vendorId ?? vendors[0]?.id ?? "");
    } else {
      const defaultVendorId = isVendorAdmin ? (myVendorId ?? "") : "";
      setForm({ ...EMPTY_FORM, vendorId: defaultVendorId });
      setBrowseVendorId(defaultVendorId || vendors[0]?.id || "");
    }
  }, [open, editing, isVendorAdmin, myVendorId, vendors]);

  useEffect(() => {
    if (!open || form.scope !== "PRODUCT" || !browseVendorId) {
      setProducts([]);
      return;
    }
    setProductsLoading(true);
    catalogService
      .listVendorProducts(browseVendorId)
      .then(setProducts)
      .catch(() => setProducts([]))
      .finally(() => setProductsLoading(false));
  }, [open, form.scope, browseVendorId]);

  function validate(): Record<string, string> {
    const errors: Record<string, string> = {};
    if (!form.name.trim()) errors.name = "Nome é obrigatório.";
    const value = parseFloat(form.value);
    if (Number.isNaN(value)) {
      errors.value = "Valor é obrigatório.";
    } else if (form.type === "PERCENTAGE" && (value <= 0 || value > 100)) {
      errors.value = "Percentagem deve estar entre 0 (exclusivo) e 100.";
    } else if (form.type === "FIXED_AMOUNT" && value <= 0) {
      errors.value = "Valor fixo deve ser superior a 0.";
    }
    if (form.scope === "CATEGORY" && !form.targetCategoryId) {
      errors.targetCategoryId = "Selecione a categoria alvo.";
    }
    if (form.scope === "PRODUCT" && !form.targetProductId) {
      errors.targetProductId = "Selecione o produto alvo.";
    }
    if (!form.startsAt) errors.startsAt = "Data de início é obrigatória.";
    if (!form.endsAt) errors.endsAt = "Data de fim é obrigatória.";
    if (form.startsAt && form.endsAt && new Date(form.startsAt) >= new Date(form.endsAt)) {
      errors.endsAt = "A data de fim deve ser posterior à data de início.";
    }
    if (isVendorAdmin && !myVendorId) {
      errors.vendorId = "Nenhum vendedor associado à sua conta.";
    }
    return errors;
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    const errors = validate();
    setFieldErrors(errors);
    if (Object.keys(errors).length > 0) return;

    setSaving(true);
    setFormError(null);
    try {
      const payload: PromotionUpsertRequest = {
        name: form.name.trim(),
        code: form.code.trim() ? form.code.trim().toUpperCase() : null,
        type: form.type,
        value: parseFloat(form.value),
        scope: form.scope,
        targetCategoryId: form.scope === "CATEGORY" ? form.targetCategoryId : null,
        targetProductId: form.scope === "PRODUCT" ? form.targetProductId : null,
        minOrderTotal: form.minOrderTotal ? parseFloat(form.minOrderTotal) : null,
        // maxDiscountAmount only applies to PERCENTAGE discounts.
        maxDiscountAmount: form.type === "PERCENTAGE" && form.maxDiscountAmount ? parseFloat(form.maxDiscountAmount) : null,
        startsAt: new Date(form.startsAt).toISOString(),
        endsAt: new Date(form.endsAt).toISOString(),
        usageLimit: form.usageLimit ? parseInt(form.usageLimit, 10) : null,
        perCustomerLimit: form.perCustomerLimit ? parseInt(form.perCustomerLimit, 10) : null,
        vendorId: isVendorAdmin ? myVendorId : (form.vendorId || null),
      };
      const saved = editing
        ? await promotionService.update(editing.id, payload)
        : await promotionService.create(payload);
      onSaved(saved);
      onOpenChange(false);
    } catch (err) {
      const { message, fields } = parseApiError(err, editing ? "Erro ao actualizar promoção." : "Erro ao criar promoção.");
      setFormError(message);
      if (fields) setFieldErrors((prev) => ({ ...prev, ...fields }));
    } finally {
      setSaving(false);
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-h-[90vh] max-w-2xl overflow-y-auto">
        <DialogHeader>
          <DialogTitle>{editing ? "Editar Promoção" : "Nova Promoção"}</DialogTitle>
          <DialogDescription>
            Descontos aplicados automaticamente ou por cupão no checkout.
          </DialogDescription>
        </DialogHeader>

        <form className="space-y-4" onSubmit={handleSubmit}>
          {formError && (
            <p className="rounded-lg bg-error-container px-3 py-2 text-sm text-on-error-container">{formError}</p>
          )}

          <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
            <div>
              <Label htmlFor="promo-name">Nome *</Label>
              <Input
                id="promo-name"
                value={form.name}
                onChange={(e) => setForm((f) => ({ ...f, name: e.target.value }))}
                required
              />
              {fieldErrors.name && <p className="mt-1 text-xs text-error">{fieldErrors.name}</p>}
            </div>
            <div>
              <Label htmlFor="promo-code">Código do cupão (opcional)</Label>
              <Input
                id="promo-code"
                placeholder="Vazio = promoção automática"
                value={form.code}
                onChange={(e) => setForm((f) => ({ ...f, code: e.target.value.toUpperCase() }))}
              />
            </div>

            <div>
              <Label htmlFor="promo-type">Tipo de desconto *</Label>
              <select
                id="promo-type"
                value={form.type}
                onChange={(e) => setForm((f) => ({ ...f, type: e.target.value as PromotionType }))}
                className={inputSelectClass}
              >
                {TYPE_OPTIONS.map((t) => (
                  <option key={t.value} value={t.value}>{t.label}</option>
                ))}
              </select>
            </div>
            <div>
              <Label htmlFor="promo-value">
                Valor * {form.type === "PERCENTAGE" ? "(0–100%)" : "(MZN)"}
              </Label>
              <Input
                id="promo-value"
                type="number"
                min="0.01"
                step="0.01"
                max={form.type === "PERCENTAGE" ? 100 : undefined}
                value={form.value}
                onChange={(e) => setForm((f) => ({ ...f, value: e.target.value }))}
                required
              />
              {fieldErrors.value && <p className="mt-1 text-xs text-error">{fieldErrors.value}</p>}
            </div>

            {form.type === "PERCENTAGE" && (
              <div>
                <Label htmlFor="promo-max-discount">Desconto máximo (MZN, opcional)</Label>
                <Input
                  id="promo-max-discount"
                  type="number"
                  min="0"
                  step="0.01"
                  value={form.maxDiscountAmount}
                  onChange={(e) => setForm((f) => ({ ...f, maxDiscountAmount: e.target.value }))}
                />
              </div>
            )}
            <div>
              <Label htmlFor="promo-min-order">Valor mínimo de encomenda (opcional)</Label>
              <Input
                id="promo-min-order"
                type="number"
                min="0"
                step="0.01"
                value={form.minOrderTotal}
                onChange={(e) => setForm((f) => ({ ...f, minOrderTotal: e.target.value }))}
              />
            </div>

            <div>
              <Label htmlFor="promo-scope">Âmbito *</Label>
              <select
                id="promo-scope"
                value={form.scope}
                onChange={(e) => setForm((f) => ({ ...f, scope: e.target.value as PromotionScope }))}
                className={inputSelectClass}
              >
                {SCOPE_OPTIONS.map((s) => (
                  <option key={s.value} value={s.value}>{s.label}</option>
                ))}
              </select>
            </div>

            {form.scope === "CATEGORY" && (
              <div>
                <Label htmlFor="promo-target-category">Categoria alvo *</Label>
                <select
                  id="promo-target-category"
                  value={form.targetCategoryId}
                  onChange={(e) => setForm((f) => ({ ...f, targetCategoryId: e.target.value }))}
                  className={inputSelectClass}
                >
                  <option value="">Selecione a categoria</option>
                  {categories.map((c) => (
                    <option key={c.id} value={c.id}>{c.name}</option>
                  ))}
                </select>
                {fieldErrors.targetCategoryId && <p className="mt-1 text-xs text-error">{fieldErrors.targetCategoryId}</p>}
              </div>
            )}

            {form.scope === "PRODUCT" && (
              <>
                <div>
                  <Label htmlFor="promo-target-vendor">Vendedor do produto</Label>
                  <select
                    id="promo-target-vendor"
                    value={browseVendorId}
                    onChange={(e) => {
                      setBrowseVendorId(e.target.value);
                      setForm((f) => ({ ...f, targetProductId: "" }));
                    }}
                    className={inputSelectClass}
                  >
                    <option value="">Selecione o vendedor</option>
                    {vendors.map((v) => (
                      <option key={v.id} value={v.id}>{v.name}</option>
                    ))}
                  </select>
                </div>
                <div>
                  <Label htmlFor="promo-target-product">Produto alvo *</Label>
                  <select
                    id="promo-target-product"
                    value={form.targetProductId}
                    onChange={(e) => setForm((f) => ({ ...f, targetProductId: e.target.value }))}
                    className={inputSelectClass}
                    disabled={!browseVendorId || productsLoading}
                  >
                    <option value="">{productsLoading ? "A carregar produtos…" : "Selecione o produto"}</option>
                    {products.map((p) => (
                      <option key={p.id} value={p.id}>{p.name}</option>
                    ))}
                  </select>
                  {fieldErrors.targetProductId && <p className="mt-1 text-xs text-error">{fieldErrors.targetProductId}</p>}
                </div>
              </>
            )}

            <div>
              <Label htmlFor="promo-starts">Início *</Label>
              <input
                id="promo-starts"
                type="datetime-local"
                value={form.startsAt}
                onChange={(e) => setForm((f) => ({ ...f, startsAt: e.target.value }))}
                className={inputSelectClass}
                required
              />
              {fieldErrors.startsAt && <p className="mt-1 text-xs text-error">{fieldErrors.startsAt}</p>}
            </div>
            <div>
              <Label htmlFor="promo-ends">Fim *</Label>
              <input
                id="promo-ends"
                type="datetime-local"
                value={form.endsAt}
                onChange={(e) => setForm((f) => ({ ...f, endsAt: e.target.value }))}
                className={inputSelectClass}
                required
              />
              {fieldErrors.endsAt && <p className="mt-1 text-xs text-error">{fieldErrors.endsAt}</p>}
            </div>

            <div>
              <Label htmlFor="promo-usage-limit">Limite total de usos (opcional)</Label>
              <Input
                id="promo-usage-limit"
                type="number"
                min="1"
                value={form.usageLimit}
                onChange={(e) => setForm((f) => ({ ...f, usageLimit: e.target.value }))}
              />
            </div>
            <div>
              <Label htmlFor="promo-per-customer-limit">Limite por cliente (opcional)</Label>
              <Input
                id="promo-per-customer-limit"
                type="number"
                min="1"
                value={form.perCustomerLimit}
                onChange={(e) => setForm((f) => ({ ...f, perCustomerLimit: e.target.value }))}
              />
            </div>

            {canSetTenantWide && (
              <div className="sm:col-span-2">
                <Label htmlFor="promo-vendor">Vendedor</Label>
                <select
                  id="promo-vendor"
                  value={form.vendorId}
                  onChange={(e) => setForm((f) => ({ ...f, vendorId: e.target.value }))}
                  className={inputSelectClass}
                >
                  <option value="">Toda a plataforma (promoção tenant-wide)</option>
                  {vendors.map((v) => (
                    <option key={v.id} value={v.id}>{v.name}</option>
                  ))}
                </select>
                <p className="mt-1 text-xs text-on-surface-variant">
                  Deixe vazio para uma promoção aplicável a todos os vendedores do tenant.
                </p>
              </div>
            )}
            {isVendorAdmin && (
              <div className="sm:col-span-2">
                {fieldErrors.vendorId && <p className="text-xs text-error">{fieldErrors.vendorId}</p>}
              </div>
            )}
          </div>

          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
              Cancelar
            </Button>
            <Button type="submit" disabled={saving}>
              {saving ? "A guardar..." : editing ? "Guardar Alterações" : "Criar Promoção"}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
