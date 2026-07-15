"use client";

import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { TableSkeleton } from "@/components/ui/loading-skeleton";
import { EmptyState } from "@/components/ui/empty-state";
import { ErrorState } from "@/components/ui/error-state";
import { Badge } from "@/components/ui/badge";
import { cn, formatCurrency, formatDate } from "@/lib/utils";
import type { SalesRow } from "@/lib/api/types";
import { orderStatusLabel, orderStatusVariant, paymentStatusLabel, paymentStatusVariant } from "./status-maps";
import { ShoppingBag } from "lucide-react";

interface SalesTableProps {
  rows: SalesRow[];
  isLoading: boolean;
  isError: boolean;
  errorMessage?: string;
  onRetry: () => void;
  selectedId: string | null;
  onSelect: (orderId: string) => void;
}

export function SalesTable({ rows, isLoading, isError, errorMessage, onRetry, selectedId, onSelect }: SalesTableProps) {
  if (isLoading) {
    return <TableSkeleton rows={8} />;
  }

  if (isError) {
    return (
      <ErrorState
        title="Erro ao carregar vendas"
        message={errorMessage ?? "Não foi possível carregar a lista de vendas. Tente novamente."}
        onRetry={onRetry}
      />
    );
  }

  if (rows.length === 0) {
    return (
      <EmptyState
        icon={<ShoppingBag className="h-8 w-8 text-on-surface-variant" />}
        title="Nenhuma venda encontrada"
        message="Ajuste os filtros ou o intervalo de datas para ver resultados."
      />
    );
  }

  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>Referência</TableHead>
          <TableHead>Data</TableHead>
          <TableHead>Fornecedor</TableHead>
          <TableHead className="text-right">Itens</TableHead>
          <TableHead className="text-right">Subtotal</TableHead>
          <TableHead className="text-right">Taxas</TableHead>
          <TableHead className="text-right">Impostos</TableHead>
          <TableHead className="text-right">Desconto</TableHead>
          <TableHead className="text-right">Total</TableHead>
          <TableHead>Estado</TableHead>
          <TableHead>Pagamento</TableHead>
          <TableHead>Fornecedor pgto.</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {rows.map((row) => (
          <TableRow
            key={row.orderId}
            className={cn("cursor-pointer", selectedId === row.orderId && "bg-surface-container-highest/70")}
            onClick={() => onSelect(row.orderId)}
          >
            <TableCell className="font-mono text-xs">{row.reference}</TableCell>
            <TableCell className="whitespace-nowrap text-xs text-muted-foreground">{formatDate(row.createdAt)}</TableCell>
            <TableCell className="max-w-[10rem] truncate">{row.vendorName}</TableCell>
            <TableCell className="text-right">{row.itemCount}</TableCell>
            <TableCell className="text-right">{formatCurrency(row.subtotal)}</TableCell>
            <TableCell className="text-right">{formatCurrency(row.fees)}</TableCell>
            <TableCell className="text-right">{formatCurrency(row.taxes)}</TableCell>
            <TableCell className="text-right">{formatCurrency(row.discountTotal)}</TableCell>
            <TableCell className="text-right font-bold">{formatCurrency(row.total)}</TableCell>
            <TableCell>
              <Badge variant={orderStatusVariant(row.orderStatus)}>{orderStatusLabel(row.orderStatus)}</Badge>
            </TableCell>
            <TableCell>
              <Badge variant={paymentStatusVariant(row.paymentStatus)}>{paymentStatusLabel(row.paymentStatus)}</Badge>
            </TableCell>
            <TableCell className="text-xs text-muted-foreground">{row.paymentProvider || "—"}</TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}
