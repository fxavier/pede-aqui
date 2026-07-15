"use client";

import { useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { AppShell } from "@/components/layout/app-shell";
import { salesService, vendorService, catalogService } from "@/lib/api/services";
import type { SalesFilter } from "@/lib/api/types";
import { useAppSelector } from "@/store/hooks";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { SalesFilters, EMPTY_SALES_FILTERS, type SalesFilterState } from "@/components/sales/sales-filters";
import { SalesTable } from "@/components/sales/sales-table";
import { SaleDetailPanel } from "@/components/sales/sale-detail-panel";
import { apiErrorMessage, isUuid } from "@/components/sales/status-maps";
import { ChevronLeft, ChevronRight } from "lucide-react";

const PAGE_SIZE = 20;

// RBAC — visibility only; the backend @PreAuthorize boundary is the real enforcement (spec.md §3).
const CANCEL_ROLES = new Set(["ADMIN", "OPS", "SUPPORT", "VENDOR_ADMIN"]);
const REFUND_ROLES = new Set(["ADMIN", "OPS", "FINANCE"]);
const RESEND_ROLES = new Set(["ADMIN", "OPS", "SUPPORT", "VENDOR_ADMIN"]);
// GET /vendors is VENDOR_ADMIN/VENDOR_STAFF/ADMIN-only server-side; other roles get a free-text
// vendor-id filter instead of a dropdown (see SalesFilters).
const CAN_LIST_VENDORS_ROLES = new Set(["ADMIN", "VENDOR_ADMIN"]);

/** Converts a plain `YYYY-MM-DD` date-input value into a UTC day-bounded ISO date-time,
 *  matching the `from`/`to` `date-time` query params in openapi.yaml. */
function toRangeIso(value: string, boundary: "start" | "end"): string | undefined {
  if (!value) return undefined;
  return boundary === "start" ? `${value}T00:00:00.000Z` : `${value}T23:59:59.999Z`;
}

export default function SalesPage() {
  const user = useAppSelector((state) => state.auth.user);
  const role = user?.role ?? "";

  const [filters, setFilters] = useState<SalesFilterState>(EMPTY_SALES_FILTERS);
  const [page, setPage] = useState(0);
  const [selectedOrderId, setSelectedOrderId] = useState<string | null>(null);

  function updateFilters(next: SalesFilterState) {
    setFilters(next);
    setPage(0);
  }

  const apiFilter: SalesFilter = useMemo(
    () => ({
      from: toRangeIso(filters.from, "start"),
      to: toRangeIso(filters.to, "end"),
      status: filters.status || undefined,
      vendorId: filters.vendorId || undefined,
      productId: filters.productId || undefined,
      paymentProvider: filters.paymentProvider || undefined,
      q: filters.q || undefined,
      page,
      size: PAGE_SIZE,
    }),
    [filters, page],
  );

  const salesQuery = useQuery({
    queryKey: ["sales", "list", apiFilter],
    queryFn: () => salesService.list(apiFilter),
  });

  const canListVendors = CAN_LIST_VENDORS_ROLES.has(role);
  const vendorsQuery = useQuery({
    queryKey: ["sales", "vendors", role],
    queryFn: () => vendorService.list(),
    enabled: canListVendors,
    staleTime: 5 * 60 * 1000,
  });

  // VENDOR_ADMIN never picks a vendor filter (it's forced server-side) but still needs a vendor
  // id to resolve their own product list for the product filter.
  const ownVendorId = role === "VENDOR_ADMIN" ? vendorsQuery.data?.[0]?.id ?? "" : "";
  const productListVendorId = filters.vendorId || ownVendorId;

  // GET /catalog/vendors/{id}/products is an open browse endpoint, so it's safe to call for any
  // role once a vendor id is known (either picked from the dropdown, typed as free text, or the
  // VENDOR_ADMIN's own vendor).
  const productsQuery = useQuery({
    queryKey: ["sales", "vendor-products", productListVendorId],
    queryFn: () => catalogService.listVendorProducts(productListVendorId),
    enabled: isUuid(productListVendorId),
    staleTime: 60 * 1000,
  });

  const vendorOptions = canListVendors && vendorsQuery.data
    ? vendorsQuery.data.map((v) => ({ id: v.id, name: v.name }))
    : null;
  const productOptions = isUuid(productListVendorId) && productsQuery.data
    ? productsQuery.data.map((p) => ({ id: p.id, name: p.name }))
    : null;

  const page_ = salesQuery.data;
  const totalPages = page_ ? Math.max(1, Math.ceil(page_.totalElements / page_.size)) : 1;

  if (!user) {
    return (
      <AppShell>
        <main className="p-4 md:p-8">
          <div className="animate-pulse text-sm text-muted-foreground">A carregar sessão…</div>
        </main>
      </AppShell>
    );
  }

  return (
    <AppShell>
      <main className="space-y-6 p-4 md:p-8">
        <div>
          <h1 className="text-2xl font-bold">Vendas</h1>
          <p className="text-muted-foreground">
            Vista comercial das encomendas: filtre, consulte detalhes e execute acções operacionais.
          </p>
        </div>

        <Card>
          <CardHeader className="pb-3">
            <CardTitle className="text-base">Filtros</CardTitle>
          </CardHeader>
          <CardContent>
            <SalesFilters
              value={filters}
              onChange={updateFilters}
              showVendorFilter={role !== "VENDOR_ADMIN"}
              vendorOptions={vendorOptions}
              vendorOptionsLoading={vendorsQuery.isLoading}
              productOptions={productOptions}
              productOptionsLoading={productsQuery.isLoading}
            />
          </CardContent>
        </Card>

        <div className="flex flex-col gap-4 lg:flex-row">
          <div className="min-w-0 flex-1 space-y-3">
            <Card>
              <CardContent className="p-0">
                <SalesTable
                  rows={page_?.content ?? []}
                  isLoading={salesQuery.isLoading}
                  isError={salesQuery.isError}
                  errorMessage={apiErrorMessage(salesQuery.error, "Não foi possível carregar a lista de vendas.")}
                  onRetry={() => salesQuery.refetch()}
                  selectedId={selectedOrderId}
                  onSelect={setSelectedOrderId}
                />
              </CardContent>
            </Card>

            {!salesQuery.isLoading && !salesQuery.isError && page_ && page_.totalElements > 0 && (
              <div className="flex items-center justify-between text-sm text-muted-foreground">
                <span>
                  {page_.totalElements} venda{page_.totalElements === 1 ? "" : "s"} · página {page + 1} de {totalPages}
                </span>
                <div className="flex gap-2">
                  <Button
                    variant="outline"
                    size="sm"
                    disabled={page === 0}
                    onClick={() => setPage((p) => Math.max(0, p - 1))}
                  >
                    <ChevronLeft className="h-4 w-4" /> Anterior
                  </Button>
                  <Button
                    variant="outline"
                    size="sm"
                    disabled={page + 1 >= totalPages}
                    onClick={() => setPage((p) => p + 1)}
                  >
                    Seguinte <ChevronRight className="h-4 w-4" />
                  </Button>
                </div>
              </div>
            )}
          </div>

          {selectedOrderId && (
            <SaleDetailPanel
              orderId={selectedOrderId}
              onClose={() => setSelectedOrderId(null)}
              canCancel={CANCEL_ROLES.has(role)}
              canRefund={REFUND_ROLES.has(role)}
              canResend={RESEND_ROLES.has(role)}
            />
          )}
        </div>

        {/* Status-override (US-6/AC-6.4) is deliberately not built here: it is config-gated off
            by default (app.sales.status-override.enabled=false), ADMIN/OPS-only, and out of
            scope for F3 per tasks.md. Use cancel/refund/resend for the supported flows. */}
      </main>
    </AppShell>
  );
}
