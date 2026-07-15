"use client";

import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { ErrorState } from "@/components/ui/error-state";
import { EmptyState } from "@/components/ui/empty-state";
import { TableSkeleton } from "@/components/ui/loading-skeleton";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { formatCurrency, formatNumber } from "@/lib/utils";
import type { DimensionRow, ProductDimensionRow } from "@/lib/api/types";
import { Download } from "lucide-react";

interface DimensionTableProps {
  title: string;
  emptyMessage: string;
  errorMessage: string;
  rows: DimensionRow[] | ProductDimensionRow[] | undefined;
  isLoading: boolean;
  isError: boolean;
  onRetry: () => void;
  onDownload: () => void;
  downloading: boolean;
  showQuantity?: boolean;
}

function hasQuantity(row: DimensionRow | ProductDimensionRow): row is ProductDimensionRow {
  return "quantitySold" in row;
}

// Generic breakdown table used for by-vendor, by-product, and by-category reports (AC-8.5).
export function DimensionTable({
  title,
  emptyMessage,
  errorMessage,
  rows,
  isLoading,
  isError,
  onRetry,
  onDownload,
  downloading,
  showQuantity,
}: DimensionTableProps) {
  return (
    <Card>
      <CardHeader className="flex flex-row items-center justify-between">
        <CardTitle>{title}</CardTitle>
        <Button
          variant="outline"
          size="sm"
          onClick={onDownload}
          disabled={downloading || isLoading || isError || (rows?.length ?? 0) === 0}
        >
          <Download className="mr-2 h-4 w-4" />
          {downloading ? "A exportar…" : "Exportar CSV"}
        </Button>
      </CardHeader>
      <CardContent className={rows && rows.length > 0 && !isLoading && !isError ? "p-0" : undefined}>
        {isError ? (
          <ErrorState message={errorMessage} onRetry={onRetry} />
        ) : isLoading ? (
          <TableSkeleton />
        ) : !rows || rows.length === 0 ? (
          <EmptyState message={emptyMessage} />
        ) : (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Nome</TableHead>
                {showQuantity && <TableHead className="text-right">Qtd. Vendida</TableHead>}
                <TableHead className="text-right">Bruto</TableHead>
                <TableHead className="text-right">Reembolsos</TableHead>
                <TableHead className="text-right">Líquido</TableHead>
                <TableHead className="text-right">Comissão</TableHead>
                <TableHead className="text-right">Quota</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {rows.map((row) => (
                <TableRow key={row.key}>
                  <TableCell className="font-medium">{row.label}</TableCell>
                  {showQuantity && (
                    <TableCell className="text-right">
                      {hasQuantity(row) ? formatNumber(row.quantitySold) : "—"}
                    </TableCell>
                  )}
                  <TableCell className="text-right">{formatCurrency(row.gross)}</TableCell>
                  <TableCell className="text-right">{formatCurrency(row.refunds)}</TableCell>
                  <TableCell className="text-right font-bold">{formatCurrency(row.net)}</TableCell>
                  <TableCell className="text-right">{formatCurrency(row.commission)}</TableCell>
                  <TableCell className="text-right">{row.sharePercent.toFixed(1)}%</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </CardContent>
    </Card>
  );
}
