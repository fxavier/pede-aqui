"use client";

import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { EmptyState } from "@/components/ui/empty-state";
import { ErrorState } from "@/components/ui/error-state";
import { TableSkeleton } from "@/components/ui/loading-skeleton";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Textarea } from "@/components/ui/textarea";
import { catalogService } from "@/lib/api/services";
import type { PendingPriceChange } from "@/lib/api/types";
import { formatCurrency, formatDate } from "@/lib/utils";
import { extractErrorMessage } from "./catalog-view";
import { CheckCircle2, XCircle } from "lucide-react";

/** OPS/ADMIN-only price-change moderation queue (US-4). Caller must hide this for VENDOR_ADMIN. */
export function PriceModerationQueue() {
  const queryClient = useQueryClient();
  const [approveTarget, setApproveTarget] = useState<PendingPriceChange | null>(null);
  const [rejectTarget, setRejectTarget] = useState<PendingPriceChange | null>(null);
  const [rejectReason, setRejectReason] = useState("");
  const [actionError, setActionError] = useState<string | null>(null);

  const { data, isLoading, isError, refetch } = useQuery({
    queryKey: ["catalog", "pending-price-changes"],
    queryFn: () => catalogService.listPendingPriceChanges(),
  });

  function invalidate(vendorId: string) {
    queryClient.invalidateQueries({ queryKey: ["catalog", "pending-price-changes"] });
    queryClient.invalidateQueries({ queryKey: ["catalog", "products", vendorId] });
  }

  const approveMutation = useMutation({
    mutationFn: (skuId: string) => catalogService.approvePriceChange(skuId),
    onSuccess: () => {
      setActionError(null);
      if (approveTarget) invalidate(approveTarget.vendorId);
      setApproveTarget(null);
    },
    onError: (err) => setActionError(extractErrorMessage(err, "Erro ao aprovar a alteração de preço.")),
  });

  const rejectMutation = useMutation({
    mutationFn: ({ skuId, reason }: { skuId: string; reason: string }) =>
      catalogService.rejectPriceChange(skuId, reason),
    onSuccess: () => {
      setActionError(null);
      if (rejectTarget) invalidate(rejectTarget.vendorId);
      setRejectTarget(null);
      setRejectReason("");
    },
    onError: (err) => setActionError(extractErrorMessage(err, "Erro ao rejeitar a alteração de preço.")),
  });

  if (isLoading) {
    return (
      <Card>
        <CardHeader>
          <CardTitle>Moderação de Preços</CardTitle>
        </CardHeader>
        <CardContent>
          <TableSkeleton />
        </CardContent>
      </Card>
    );
  }

  if (isError) {
    return (
      <Card>
        <CardHeader>
          <CardTitle>Moderação de Preços</CardTitle>
        </CardHeader>
        <CardContent>
          <ErrorState message="Erro ao carregar alterações de preço pendentes." onRetry={() => refetch()} />
        </CardContent>
      </Card>
    );
  }

  const rows = data ?? [];

  return (
    <Card>
      <CardHeader>
        <CardTitle>Moderação de Preços</CardTitle>
      </CardHeader>
      <CardContent className="space-y-4">
        {actionError && (
          <div className="rounded-lg bg-error-container p-3 text-xs text-on-error-container">{actionError}</div>
        )}
        {rows.length === 0 ? (
          <EmptyState message="Não há alterações de preço pendentes de aprovação." />
        ) : (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Produto</TableHead>
                <TableHead className="text-right">Preço Atual</TableHead>
                <TableHead className="text-right">Preço Proposto</TableHead>
                <TableHead className="text-right">Δ%</TableHead>
                <TableHead>Submetido Por</TableHead>
                <TableHead>Data</TableHead>
                <TableHead className="text-right">Ações</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {rows.map((pc) => (
                <TableRow key={pc.skuId}>
                  <TableCell className="font-bold text-on-surface">{pc.productName}</TableCell>
                  <TableCell className="text-right">{formatCurrency(pc.currentPrice)}</TableCell>
                  <TableCell className="text-right">{formatCurrency(pc.pendingPrice)}</TableCell>
                  <TableCell className="text-right">
                    <Badge variant={pc.deltaPercent >= 0 ? "destructive" : "success"}>
                      {pc.deltaPercent > 0 ? "+" : ""}
                      {pc.deltaPercent.toFixed(1)}%
                    </Badge>
                  </TableCell>
                  <TableCell className="text-xs text-on-surface-variant">{pc.submittedBy}</TableCell>
                  <TableCell className="text-xs text-on-surface-variant">{formatDate(pc.submittedAt)}</TableCell>
                  <TableCell className="text-right">
                    <div className="flex justify-end gap-2">
                      <Button
                        size="sm"
                        variant="outline"
                        onClick={() => setApproveTarget(pc)}
                        disabled={approveMutation.isPending || rejectMutation.isPending}
                      >
                        <CheckCircle2 className="mr-1 h-4 w-4" />
                        Aprovar
                      </Button>
                      <Button
                        size="sm"
                        variant="outline"
                        className="text-red-600 hover:text-red-700"
                        onClick={() => {
                          setRejectTarget(pc);
                          setRejectReason("");
                        }}
                        disabled={approveMutation.isPending || rejectMutation.isPending}
                      >
                        <XCircle className="mr-1 h-4 w-4" />
                        Rejeitar
                      </Button>
                    </div>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </CardContent>

      {/* Approve confirm dialog */}
      <Dialog open={approveTarget !== null} onOpenChange={(o) => { if (!o) setApproveTarget(null); }}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Aprovar alteração de preço</DialogTitle>
            <DialogDescription>
              {approveTarget && (
                <>
                  Confirma a aprovação do novo preço de{" "}
                  <strong>{formatCurrency(approveTarget.pendingPrice)}</strong> para &quot;{approveTarget.productName}&quot;?
                  O preço atual ({formatCurrency(approveTarget.currentPrice)}) será substituído.
                </>
              )}
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" onClick={() => setApproveTarget(null)}>Cancelar</Button>
            <Button
              disabled={approveMutation.isPending}
              onClick={() => approveTarget && approveMutation.mutate(approveTarget.skuId)}
            >
              {approveMutation.isPending ? "A aprovar..." : "Confirmar Aprovação"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Reject confirm dialog (reason required) */}
      <Dialog
        open={rejectTarget !== null}
        onOpenChange={(o) => {
          if (!o) {
            setRejectTarget(null);
            setRejectReason("");
          }
        }}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Rejeitar alteração de preço</DialogTitle>
            <DialogDescription>
              {rejectTarget && (
                <>
                  Indique o motivo da rejeição para &quot;{rejectTarget.productName}&quot;. O preço mantém-se em{" "}
                  {formatCurrency(rejectTarget.currentPrice)}.
                </>
              )}
            </DialogDescription>
          </DialogHeader>
          <Textarea
            placeholder="Motivo da rejeição *"
            value={rejectReason}
            onChange={(e) => setRejectReason(e.target.value)}
            required
          />
          <DialogFooter>
            <Button
              variant="outline"
              onClick={() => {
                setRejectTarget(null);
                setRejectReason("");
              }}
            >
              Cancelar
            </Button>
            <Button
              variant="destructive"
              disabled={rejectMutation.isPending || !rejectReason.trim()}
              onClick={() =>
                rejectTarget && rejectMutation.mutate({ skuId: rejectTarget.skuId, reason: rejectReason.trim() })
              }
            >
              {rejectMutation.isPending ? "A rejeitar..." : "Confirmar Rejeição"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </Card>
  );
}
