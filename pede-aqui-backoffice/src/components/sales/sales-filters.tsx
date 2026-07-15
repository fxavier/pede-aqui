"use client";

import { useEffect, useState } from "react";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Button } from "@/components/ui/button";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { ORDER_STATUS_LABELS } from "./status-maps";
import { RotateCcw } from "lucide-react";

export interface SalesFilterState {
  from: string;
  to: string;
  status: string;
  vendorId: string;
  productId: string;
  paymentProvider: string;
  q: string;
}

export const EMPTY_SALES_FILTERS: SalesFilterState = {
  from: "",
  to: "",
  status: "",
  vendorId: "",
  productId: "",
  paymentProvider: "",
  q: "",
};

interface VendorOption {
  id: string;
  name: string;
}

interface ProductOption {
  id: string;
  name: string;
}

interface SalesFiltersProps {
  value: SalesFilterState;
  onChange: (next: SalesFilterState) => void;
  /** Hidden entirely for VENDOR_ADMIN — results are always forced to their own vendor server-side. */
  showVendorFilter: boolean;
  /** `null` when the role can't list vendors (GET /vendors is VENDOR_ADMIN/VENDOR_STAFF/ADMIN-only)
   *  — falls back to a free-text vendor-id input for OPS/FINANCE/SUPPORT. */
  vendorOptions: VendorOption[] | null;
  vendorOptionsLoading: boolean;
  productOptions: ProductOption[] | null;
  productOptionsLoading: boolean;
}

/** Debounces a free-text field so filter queries don't fire on every keystroke. */
function useDebouncedField(initial: string, delay: number, onCommit: (value: string) => void) {
  const [draft, setDraft] = useState(initial);

  useEffect(() => {
    setDraft(initial);
  }, [initial]);

  useEffect(() => {
    const timer = setTimeout(() => {
      if (draft !== initial) onCommit(draft);
    }, delay);
    return () => clearTimeout(timer);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [draft]);

  return [draft, setDraft] as const;
}

export function SalesFilters({
  value,
  onChange,
  showVendorFilter,
  vendorOptions,
  vendorOptionsLoading,
  productOptions,
  productOptionsLoading,
}: SalesFiltersProps) {
  const [qDraft, setQDraft] = useDebouncedField(value.q, 400, (q) => onChange({ ...value, q }));
  const [vendorDraft, setVendorDraft] = useDebouncedField(value.vendorId, 500, (vendorId) =>
    onChange({ ...value, vendorId, productId: "" }),
  );
  const [productDraft, setProductDraft] = useDebouncedField(value.productId, 500, (productId) =>
    onChange({ ...value, productId }),
  );

  const hasActiveFilters = Object.values(value).some((v) => v !== "");

  return (
    <div className="grid grid-cols-2 gap-3 md:grid-cols-3 lg:grid-cols-4">
      <div className="space-y-1">
        <Label htmlFor="sales-from">De</Label>
        <Input
          id="sales-from"
          type="date"
          value={value.from}
          onChange={(e) => onChange({ ...value, from: e.target.value })}
        />
      </div>
      <div className="space-y-1">
        <Label htmlFor="sales-to">Até</Label>
        <Input
          id="sales-to"
          type="date"
          value={value.to}
          onChange={(e) => onChange({ ...value, to: e.target.value })}
        />
      </div>
      <div className="space-y-1">
        <Label htmlFor="sales-status">Estado</Label>
        <Select
          value={value.status || "__all__"}
          onValueChange={(v) => onChange({ ...value, status: v === "__all__" ? "" : v })}
        >
          <SelectTrigger id="sales-status">
            <SelectValue placeholder="Todos os estados" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="__all__">Todos os estados</SelectItem>
            {Object.entries(ORDER_STATUS_LABELS).map(([status, label]) => (
              <SelectItem key={status} value={status}>
                {label}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>

      {showVendorFilter &&
        (vendorOptions ? (
          <div className="space-y-1">
            <Label htmlFor="sales-vendor">Fornecedor</Label>
            <Select
              value={value.vendorId || "__all__"}
              onValueChange={(v) => onChange({ ...value, vendorId: v === "__all__" ? "" : v, productId: "" })}
            >
              <SelectTrigger id="sales-vendor">
                <SelectValue placeholder={vendorOptionsLoading ? "A carregar…" : "Todos os fornecedores"} />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="__all__">Todos os fornecedores</SelectItem>
                {vendorOptions.map((v) => (
                  <SelectItem key={v.id} value={v.id}>
                    {v.name}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
        ) : (
          <div className="space-y-1">
            <Label htmlFor="sales-vendor-id">Fornecedor (ID)</Label>
            <Input
              id="sales-vendor-id"
              placeholder="UUID do fornecedor"
              value={vendorDraft}
              onChange={(e) => setVendorDraft(e.target.value)}
            />
          </div>
        ))}

      <div className="space-y-1">
        <Label htmlFor="sales-product">Produto</Label>
        {productOptions ? (
          <Select
            value={value.productId || "__all__"}
            onValueChange={(v) => onChange({ ...value, productId: v === "__all__" ? "" : v })}
          >
            <SelectTrigger id="sales-product">
              <SelectValue placeholder={productOptionsLoading ? "A carregar…" : "Todos os produtos"} />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="__all__">Todos os produtos</SelectItem>
              {productOptions.map((p) => (
                <SelectItem key={p.id} value={p.id}>
                  {p.name}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        ) : (
          <Input
            id="sales-product"
            placeholder={value.vendorId ? "UUID do produto" : "Seleccione um fornecedor primeiro"}
            value={productDraft}
            onChange={(e) => setProductDraft(e.target.value)}
          />
        )}
      </div>

      <div className="space-y-1">
        <Label htmlFor="sales-provider">Fornecedor de pagamento</Label>
        <Input
          id="sales-provider"
          placeholder="Ex.: LOCAL_MOCK"
          value={value.paymentProvider}
          onChange={(e) => onChange({ ...value, paymentProvider: e.target.value })}
        />
      </div>

      <div className="col-span-2 space-y-1 md:col-span-2 lg:col-span-2">
        <Label htmlFor="sales-q">Pesquisa (referência ou cliente)</Label>
        <Input
          id="sales-q"
          placeholder="Pesquisar…"
          value={qDraft}
          onChange={(e) => setQDraft(e.target.value)}
        />
      </div>

      <div className="flex items-end">
        <Button
          type="button"
          variant="outline"
          disabled={!hasActiveFilters}
          onClick={() => {
            setQDraft("");
            setVendorDraft("");
            setProductDraft("");
            onChange(EMPTY_SALES_FILTERS);
          }}
        >
          <RotateCcw className="mr-2 h-4 w-4" />
          Limpar filtros
        </Button>
      </div>
    </div>
  );
}
