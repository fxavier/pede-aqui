"use client";

import { useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { AppShell } from "@/components/layout/app-shell";
import { reportService, vendorService } from "@/lib/api/services";
import type { ReportInterval, SalesReportName } from "@/lib/api/types";
import { useAppSelector } from "@/store/hooks";
import { ReportFilters } from "@/components/reports/report-filters";
import { KpiGrid } from "@/components/reports/kpi-grid";
import { SalesTimeseriesChart } from "@/components/reports/sales-timeseries-chart";
import { DimensionTable } from "@/components/reports/dimension-table";
import { defaultDateRange, startOfDayIso, endOfDayIso } from "@/components/reports/date-utils";

const VENDOR_FILTER_ROLES = new Set(["ADMIN", "OPS", "FINANCE"]);

export default function ReportsPage() {
  const role = useAppSelector((state) => state.auth.user?.role) ?? "";
  const canFilterVendor = VENDOR_FILTER_ROLES.has(role);

  const initialRange = useMemo(defaultDateRange, []);
  const [fromDate, setFromDate] = useState(initialRange.from);
  const [toDate, setToDate] = useState(initialRange.to);
  const [vendorId, setVendorId] = useState<string | undefined>(undefined);
  const [interval, setReportInterval] = useState<ReportInterval>("day");
  const [downloadingReport, setDownloadingReport] = useState<SalesReportName | null>(null);
  const [downloadError, setDownloadError] = useState<string | null>(null);

  const from = startOfDayIso(fromDate);
  const to = endOfDayIso(toDate);
  const rangeError = fromDate > toDate ? "A data inicial deve ser anterior à data final." : null;
  const effectiveVendorId = canFilterVendor ? vendorId : undefined;

  const vendorsQuery = useQuery({
    queryKey: ["reports", "vendors"],
    queryFn: () => vendorService.list(),
    enabled: canFilterVendor,
  });

  // Endpoints that accept a vendor filter per the OpenAPI contract (summary, timeseries,
  // by-product, by-category). `by-vendor` intentionally excludes it — see contracts/openapi.yaml.
  const rangeParams = { from, to, vendorId: effectiveVendorId };
  const queriesEnabled = !rangeError;

  const summaryQuery = useQuery({
    queryKey: ["reports", "summary", rangeParams],
    queryFn: () => reportService.summary(rangeParams),
    enabled: queriesEnabled,
  });

  const timeseriesQuery = useQuery({
    queryKey: ["reports", "timeseries", rangeParams, interval],
    queryFn: () => reportService.timeseries({ ...rangeParams, interval }),
    enabled: queriesEnabled,
  });

  const byVendorQuery = useQuery({
    queryKey: ["reports", "by-vendor", from, to],
    queryFn: () => reportService.byVendor({ from, to }),
    enabled: queriesEnabled,
  });

  const byProductQuery = useQuery({
    queryKey: ["reports", "by-product", rangeParams],
    queryFn: () => reportService.byProduct(rangeParams),
    enabled: queriesEnabled,
  });

  const byCategoryQuery = useQuery({
    queryKey: ["reports", "by-category", rangeParams],
    queryFn: () => reportService.byCategory(rangeParams),
    enabled: queriesEnabled,
  });

  async function handleDownload(report: SalesReportName, includeVendorFilter: boolean) {
    setDownloadError(null);
    setDownloadingReport(report);
    try {
      const blob = await reportService.downloadExport(report, {
        from,
        to,
        vendorId: includeVendorFilter ? effectiveVendorId : undefined,
        ...(report === "timeseries" ? { interval } : {}),
      });
      const url = URL.createObjectURL(blob);
      const link = document.createElement("a");
      link.href = url;
      link.download = `relatorio-${report}-${from.slice(0, 10)}-${to.slice(0, 10)}.csv`;
      document.body.appendChild(link);
      link.click();
      link.remove();
      URL.revokeObjectURL(url);
    } catch {
      setDownloadError("Erro ao exportar o relatório em CSV. Tente novamente.");
    } finally {
      setDownloadingReport(null);
    }
  }

  return (
    <AppShell>
      <main className="space-y-6 p-4 md:p-8">
        <div>
          <h1 className="font-headline-lg text-headline-lg text-on-surface">Relatórios de Vendas</h1>
          <p className="mt-1 text-body-md text-on-surface-variant">
            Resumo, série temporal e detalhe por vendedor, produto e categoria.
          </p>
        </div>

        <ReportFilters
          fromDate={fromDate}
          toDate={toDate}
          onFromDateChange={setFromDate}
          onToDateChange={setToDate}
          interval={interval}
          onIntervalChange={setReportInterval}
          canFilterVendor={canFilterVendor}
          vendorId={vendorId}
          onVendorIdChange={setVendorId}
          vendors={vendorsQuery.data ?? []}
          vendorsLoading={vendorsQuery.isLoading}
          rangeError={rangeError}
        />

        {downloadError && (
          <div className="flex items-center justify-between rounded-xl border border-error-container bg-error-container/40 p-3 text-sm text-on-error-container">
            <span>{downloadError}</span>
            <button className="font-bold" onClick={() => setDownloadError(null)}>✕</button>
          </div>
        )}

        <KpiGrid
          summary={summaryQuery.data}
          isLoading={summaryQuery.isLoading}
          isError={summaryQuery.isError}
          onRetry={() => summaryQuery.refetch()}
        />

        <SalesTimeseriesChart
          buckets={timeseriesQuery.data}
          interval={interval}
          isLoading={timeseriesQuery.isLoading}
          isError={timeseriesQuery.isError}
          onRetry={() => timeseriesQuery.refetch()}
          onDownload={() => handleDownload("timeseries", true)}
          downloading={downloadingReport === "timeseries"}
        />

        <div className="grid grid-cols-1 gap-6 xl:grid-cols-2">
          <DimensionTable
            title="Vendas por Vendedor"
            emptyMessage="Sem vendas por vendedor no período seleccionado."
            errorMessage="Erro ao carregar vendas por vendedor."
            rows={byVendorQuery.data}
            isLoading={byVendorQuery.isLoading}
            isError={byVendorQuery.isError}
            onRetry={() => byVendorQuery.refetch()}
            onDownload={() => handleDownload("by-vendor", false)}
            downloading={downloadingReport === "by-vendor"}
          />

          <DimensionTable
            title="Vendas por Categoria"
            emptyMessage="Sem vendas por categoria no período seleccionado."
            errorMessage="Erro ao carregar vendas por categoria."
            rows={byCategoryQuery.data}
            isLoading={byCategoryQuery.isLoading}
            isError={byCategoryQuery.isError}
            onRetry={() => byCategoryQuery.refetch()}
            onDownload={() => handleDownload("by-category", true)}
            downloading={downloadingReport === "by-category"}
          />
        </div>

        <DimensionTable
          title="Vendas por Produto"
          emptyMessage="Sem vendas por produto no período seleccionado."
          errorMessage="Erro ao carregar vendas por produto."
          rows={byProductQuery.data}
          isLoading={byProductQuery.isLoading}
          isError={byProductQuery.isError}
          onRetry={() => byProductQuery.refetch()}
          onDownload={() => handleDownload("by-product", true)}
          downloading={downloadingReport === "by-product"}
          showQuantity
        />
      </main>
    </AppShell>
  );
}
