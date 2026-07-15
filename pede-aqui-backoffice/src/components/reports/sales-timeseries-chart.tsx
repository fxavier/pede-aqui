"use client";

import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { ErrorState } from "@/components/ui/error-state";
import { EmptyState } from "@/components/ui/empty-state";
import { TableSkeleton } from "@/components/ui/loading-skeleton";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { formatCurrency } from "@/lib/utils";
import type { ReportInterval, SalesBucket } from "@/lib/api/types";
import { formatBucketLabel } from "./date-utils";
import { Download } from "lucide-react";

interface SalesTimeseriesChartProps {
  buckets: SalesBucket[] | undefined;
  interval: ReportInterval;
  isLoading: boolean;
  isError: boolean;
  onRetry: () => void;
  onDownload: () => void;
  downloading: boolean;
}

// Time-series chart for gross/net sales over the selected interval (AC-8.4).
// No chart library exists in this app yet, so this renders a lightweight CSS bar
// chart for a quick visual read, backed by an always-visible, screen-reader
// accessible data table with the exact figures.
export function SalesTimeseriesChart({
  buckets,
  interval,
  isLoading,
  isError,
  onRetry,
  onDownload,
  downloading,
}: SalesTimeseriesChartProps) {
  const maxValue = Math.max(1, ...(buckets ?? []).map((b) => Math.max(b.gross, b.net)));

  return (
    <Card>
      <CardHeader className="flex flex-row items-center justify-between">
        <CardTitle>Vendas ao Longo do Tempo</CardTitle>
        <Button
          variant="outline"
          size="sm"
          onClick={onDownload}
          disabled={downloading || isLoading || isError || (buckets?.length ?? 0) === 0}
        >
          <Download className="mr-2 h-4 w-4" />
          {downloading ? "A exportar…" : "Exportar CSV"}
        </Button>
      </CardHeader>
      <CardContent className="space-y-6">
        {isError ? (
          <ErrorState message="Erro ao carregar a série temporal." onRetry={onRetry} />
        ) : isLoading ? (
          <TableSkeleton />
        ) : !buckets || buckets.length === 0 ? (
          <EmptyState message="Sem vendas no período seleccionado." />
        ) : (
          <>
            <div className="flex items-end gap-2">
              <div className="flex items-center gap-1.5 text-xs font-bold text-on-surface-variant">
                <span className="h-2.5 w-2.5 rounded-full bg-primary" /> Bruto
              </div>
              <div className="flex items-center gap-1.5 text-xs font-bold text-on-surface-variant">
                <span className="h-2.5 w-2.5 rounded-full bg-secondary" /> Líquido
              </div>
            </div>
            <div
              className="flex h-48 items-end gap-3 overflow-x-auto pb-2"
              role="img"
              aria-label="Gráfico de barras de vendas brutas e líquidas por período"
            >
              {buckets.map((bucket) => (
                <div key={bucket.bucket} className="flex min-w-[3.5rem] flex-1 flex-col items-center gap-1">
                  <div className="flex h-40 w-full items-end justify-center gap-1">
                    <div
                      className="w-3 rounded-t bg-primary transition-all"
                      style={{ height: `${Math.max(2, (bucket.gross / maxValue) * 100)}%` }}
                      title={`Bruto: ${formatCurrency(bucket.gross)}`}
                    />
                    <div
                      className="w-3 rounded-t bg-secondary transition-all"
                      style={{ height: `${Math.max(2, (bucket.net / maxValue) * 100)}%` }}
                      title={`Líquido: ${formatCurrency(bucket.net)}`}
                    />
                  </div>
                  <span className="text-xs text-on-surface-variant">{formatBucketLabel(bucket.bucket, interval)}</span>
                </div>
              ))}
            </div>

            <div className="max-h-72 overflow-y-auto">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Período</TableHead>
                    <TableHead className="text-right">Encomendas</TableHead>
                    <TableHead className="text-right">Bruto</TableHead>
                    <TableHead className="text-right">Descontos</TableHead>
                    <TableHead className="text-right">Reembolsos</TableHead>
                    <TableHead className="text-right">Líquido</TableHead>
                    <TableHead className="text-right">Comissão</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {buckets.map((bucket) => (
                    <TableRow key={bucket.bucket}>
                      <TableCell className="font-medium">{formatBucketLabel(bucket.bucket, interval)}</TableCell>
                      <TableCell className="text-right">{bucket.orderCount}</TableCell>
                      <TableCell className="text-right">{formatCurrency(bucket.gross)}</TableCell>
                      <TableCell className="text-right">{formatCurrency(bucket.discountTotal)}</TableCell>
                      <TableCell className="text-right">{formatCurrency(bucket.refunds)}</TableCell>
                      <TableCell className="text-right font-bold">{formatCurrency(bucket.net)}</TableCell>
                      <TableCell className="text-right">{formatCurrency(bucket.commission)}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </div>
          </>
        )}
      </CardContent>
    </Card>
  );
}
