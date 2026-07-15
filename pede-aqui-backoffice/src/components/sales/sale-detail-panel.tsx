"use client";

import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { salesService } from "@/lib/api/services";
import type { SalesNotificationType } from "@/lib/api/types";
import { formatCurrency, formatDate } from "@/lib/utils";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { Input } from "@/components/ui/input";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { LoadingSkeleton } from "@/components/ui/loading-skeleton";
import { ErrorState } from "@/components/ui/error-state";
import {
  apiErrorMessage,
  CANCELLABLE_STATUSES,
  NOTIFICATION_TYPE_LABELS,
  orderStatusLabel,
  orderStatusVariant,
  paymentStatusLabel,
  paymentStatusVariant,
  refundStatusLabel,
} from "./status-maps";
import { Ban, Landmark, Send, X } from "lucide-react";

interface SaleDetailPanelProps {
  orderId: string;
  onClose: () => void;
  canCancel: boolean;
  canRefund: boolean;
  canResend: boolean;
}

/** Detail panel for a single sale: line items, totals reconciliation, payments, refunds,
 *  commission, applied promotion and the cancel/refund/resend operational actions (US-5/US-6). */
export function SaleDetailPanel({ orderId, onClose, canCancel, canRefund, canResend }: SaleDetailPanelProps) {
  const queryClient = useQueryClient();
  const [cancelOpen, setCancelOpen] = useState(false);
  const [refundOpen, setRefundOpen] = useState(false);
  const [resendOpen, setResendOpen] = useState(false);
  const [banner, setBanner] = useState<{ kind: "success" | "error"; message: string } | null>(null);

  const detailQuery = useQuery({
    queryKey: ["sales", "detail", orderId],
    queryFn: () => salesService.detail(orderId),
  });

  function invalidateAfterAction() {
    queryClient.invalidateQueries({ queryKey: ["sales", "detail", orderId] });
    queryClient.invalidateQueries({ queryKey: ["sales", "list"] });
  }

  if (detailQuery.isLoading) {
    return (
      <div className="w-full shrink-0 lg:w-[26rem]">
        <LoadingSkeleton />
      </div>
    );
  }

  if (detailQuery.isError || !detailQuery.data) {
    return (
      <div className="w-full shrink-0 lg:w-[26rem]">
        <ErrorState
          title="Erro ao carregar venda"
          message={apiErrorMessage(detailQuery.error, "Não foi possível carregar os detalhes desta venda.")}
          onRetry={() => detailQuery.refetch()}
        />
      </div>
    );
  }

  const sale = detailQuery.data;
  const totalsMismatch =
    Math.round((sale.subtotal + sale.fees + sale.taxes - sale.discountTotal) * 100) !== Math.round(sale.total * 100);
  const totalPaid = sale.payments.reduce((sum, p) => sum + p.amount, 0);
  const totalRefunded = sale.refunds
    .filter((r) => r.status !== "REJECTED")
    .reduce((sum, r) => sum + r.amount, 0);
  const maxRefundable = Math.max(0, totalPaid - totalRefunded);
  const cancelHint = CANCELLABLE_STATUSES.has(sale.orderStatus);

  return (
    <div className="w-full shrink-0 lg:w-[26rem]">
      <Card className="lg:sticky lg:top-6">
        <CardHeader className="pb-3">
          <div className="flex items-center justify-between">
            <CardTitle className="font-mono text-sm">{sale.reference}</CardTitle>
            <Button variant="ghost" size="icon" onClick={onClose} aria-label="Fechar detalhe">
              <X className="h-4 w-4" />
            </Button>
          </div>
          <div className="flex flex-wrap gap-2">
            <Badge variant={orderStatusVariant(sale.orderStatus)}>{orderStatusLabel(sale.orderStatus)}</Badge>
            <Badge variant={paymentStatusVariant(sale.paymentStatus)}>{paymentStatusLabel(sale.paymentStatus)}</Badge>
            {(sale.appliedPromotionId || sale.discountTotal > 0) && (
              <Badge variant="secondary" title={sale.appliedPromotionId ?? undefined}>
                Promoção aplicada
              </Badge>
            )}
          </div>
        </CardHeader>
        <CardContent className="space-y-5 text-sm">
          {banner && (
            <div
              className={
                banner.kind === "success"
                  ? "rounded-md border border-secondary/30 bg-secondary-container/40 p-2 text-xs text-on-secondary-container"
                  : "rounded-md border border-destructive/20 bg-destructive/10 p-2 text-xs text-destructive"
              }
            >
              {banner.message}
            </div>
          )}

          <div className="grid grid-cols-2 gap-y-1.5 text-muted-foreground">
            <span>Fornecedor</span>
            <span className="text-right font-medium text-foreground">{sale.vendorName}</span>
            <span>Cliente</span>
            <span className="text-right font-medium text-foreground">{sale.customerName ?? "—"}</span>
            <span>Data</span>
            <span className="text-right text-foreground">{formatDate(sale.createdAt)}</span>
            <span>Fornecedor de pagamento</span>
            <span className="text-right text-foreground">{sale.paymentProvider || "—"}</span>
            <span>Itens</span>
            <span className="text-right text-foreground">{sale.itemCount}</span>
          </div>

          {/* Line items */}
          <div>
            <p className="mb-2 font-medium">Artigos</p>
            {sale.items.length === 0 ? (
              <p className="text-xs text-muted-foreground">Sem artigos.</p>
            ) : (
              <div className="space-y-1">
                {sale.items.map((item, idx) => (
                  <div key={idx} className="flex justify-between gap-2 text-xs">
                    <span className="truncate">
                      {item.quantity}× {item.productNameSnapshot} @ {formatCurrency(item.unitPriceSnapshot)}
                    </span>
                    <span className="shrink-0 text-muted-foreground">{formatCurrency(item.lineTotal)}</span>
                  </div>
                ))}
              </div>
            )}
          </div>

          {/* Totals reconciliation */}
          <div className="space-y-1 border-t pt-3">
            <div className="flex justify-between text-xs text-muted-foreground">
              <span>Subtotal</span>
              <span>{formatCurrency(sale.subtotal)}</span>
            </div>
            <div className="flex justify-between text-xs text-muted-foreground">
              <span>Taxas de serviço</span>
              <span>{formatCurrency(sale.fees)}</span>
            </div>
            <div className="flex justify-between text-xs text-muted-foreground">
              <span>Impostos</span>
              <span>{formatCurrency(sale.taxes)}</span>
            </div>
            <div className="flex justify-between text-xs text-muted-foreground">
              <span>Desconto</span>
              <span>−{formatCurrency(sale.discountTotal)}</span>
            </div>
            <div className="flex justify-between font-bold">
              <span>Total</span>
              <span>{formatCurrency(sale.total)}</span>
            </div>
            <div className="flex justify-between text-xs text-muted-foreground">
              <span>Comissão da plataforma</span>
              <span>{formatCurrency(sale.commission)}</span>
            </div>
            {totalsMismatch && (
              <p className="text-xs text-destructive">
                Aviso: subtotal + taxas + impostos − desconto não corresponde ao total registado.
              </p>
            )}
          </div>

          {/* Payments */}
          <div className="border-t pt-3">
            <p className="mb-2 font-medium">Pagamentos</p>
            {sale.payments.length === 0 ? (
              <p className="text-xs text-muted-foreground">Sem pagamentos registados.</p>
            ) : (
              <div className="space-y-1">
                {sale.payments.map((p) => (
                  <div key={p.id} className="flex items-center justify-between text-xs">
                    <span className="text-muted-foreground">{p.provider}</span>
                    <Badge variant={paymentStatusVariant(p.status)} className="text-[10px]">
                      {paymentStatusLabel(p.status)}
                    </Badge>
                    <span>{formatCurrency(p.amount)}</span>
                  </div>
                ))}
              </div>
            )}
          </div>

          {/* Refunds */}
          <div className="border-t pt-3">
            <p className="mb-2 font-medium">Reembolsos</p>
            {sale.refunds.length === 0 ? (
              <p className="text-xs text-muted-foreground">Sem reembolsos registados.</p>
            ) : (
              <div className="space-y-1">
                {sale.refunds.map((r) => (
                  <div key={r.id} className="flex items-center justify-between text-xs">
                    <Badge variant="outline" className="text-[10px]">
                      {refundStatusLabel(r.status)}
                    </Badge>
                    <span>{formatCurrency(r.amount)}</span>
                  </div>
                ))}
              </div>
            )}
          </div>

          {/* Actions */}
          {(canCancel || canRefund || canResend) && (
            <div className="space-y-2 border-t pt-3">
              <p className="font-medium">Acções</p>
              <div className="flex flex-wrap gap-2">
                {canCancel && (
                  <Button
                    size="sm"
                    variant="destructive"
                    disabled={!cancelHint}
                    title={!cancelHint ? "Só é possível cancelar em estados anteriores à expedição" : undefined}
                    onClick={() => {
                      setBanner(null);
                      setCancelOpen(true);
                    }}
                  >
                    <Ban className="mr-1 h-4 w-4" /> Cancelar
                  </Button>
                )}
                {canRefund && (
                  <Button
                    size="sm"
                    variant="outline"
                    onClick={() => {
                      setBanner(null);
                      setRefundOpen(true);
                    }}
                  >
                    <Landmark className="mr-1 h-4 w-4" /> Reembolsar
                  </Button>
                )}
                {canResend && (
                  <Button
                    size="sm"
                    variant="outline"
                    onClick={() => {
                      setBanner(null);
                      setResendOpen(true);
                    }}
                  >
                    <Send className="mr-1 h-4 w-4" /> Reenviar notificação
                  </Button>
                )}
              </div>
            </div>
          )}
        </CardContent>
      </Card>

      {canCancel && (
        <CancelDialog
          open={cancelOpen}
          onOpenChange={setCancelOpen}
          orderId={orderId}
          onSuccess={(msg) => {
            setBanner({ kind: "success", message: msg });
            invalidateAfterAction();
          }}
          onError={(msg) => setBanner({ kind: "error", message: msg })}
        />
      )}
      {canRefund && (
        <RefundDialog
          open={refundOpen}
          onOpenChange={setRefundOpen}
          orderId={orderId}
          maxRefundable={maxRefundable}
          onSuccess={(msg) => {
            setBanner({ kind: "success", message: msg });
            invalidateAfterAction();
          }}
          onError={(msg) => setBanner({ kind: "error", message: msg })}
        />
      )}
      {canResend && (
        <ResendDialog
          open={resendOpen}
          onOpenChange={setResendOpen}
          orderId={orderId}
          onSuccess={(msg) => {
            setBanner({ kind: "success", message: msg });
            invalidateAfterAction();
          }}
          onError={(msg) => setBanner({ kind: "error", message: msg })}
        />
      )}
    </div>
  );
}

function CancelDialog({
  open,
  onOpenChange,
  orderId,
  onSuccess,
  onError,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  orderId: string;
  onSuccess: (message: string) => void;
  onError: (message: string) => void;
}) {
  const [reason, setReason] = useState("");

  const mutation = useMutation({
    mutationFn: () => salesService.cancel(orderId, reason.trim()),
    onSuccess: () => {
      onSuccess("Venda cancelada com sucesso.");
      onOpenChange(false);
      setReason("");
    },
    onError: (err) => {
      onError(apiErrorMessage(err, "Não foi possível cancelar esta venda."));
    },
  });

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Cancelar venda</DialogTitle>
          <DialogDescription>
            Esta acção só é permitida em estados anteriores à expedição e é registada em auditoria.
            Indique o motivo do cancelamento.
          </DialogDescription>
        </DialogHeader>
        <div className="space-y-2">
          <Label htmlFor="cancel-reason">Motivo</Label>
          <Textarea
            id="cancel-reason"
            value={reason}
            maxLength={500}
            onChange={(e) => setReason(e.target.value)}
            placeholder="Ex.: pedido do cliente, ruptura de stock…"
          />
        </div>
        {mutation.isError && (
          <p className="text-sm text-destructive">{apiErrorMessage(mutation.error, "Erro ao cancelar.")}</p>
        )}
        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>
            Fechar
          </Button>
          <Button
            variant="destructive"
            disabled={!reason.trim() || mutation.isPending}
            onClick={() => mutation.mutate()}
          >
            {mutation.isPending ? "A cancelar…" : "Confirmar cancelamento"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

function RefundDialog({
  open,
  onOpenChange,
  orderId,
  maxRefundable,
  onSuccess,
  onError,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  orderId: string;
  maxRefundable: number;
  onSuccess: (message: string) => void;
  onError: (message: string) => void;
}) {
  const [reason, setReason] = useState("");
  const [partial, setPartial] = useState(false);
  const [amount, setAmount] = useState("");
  const [idempotencyKey] = useState(() =>
    typeof crypto !== "undefined" && "randomUUID" in crypto ? crypto.randomUUID() : `${orderId}-${Date.now()}`
  );

  const mutation = useMutation({
    mutationFn: () =>
      salesService.refund(
        orderId,
        {
          reason: reason.trim(),
          amount: partial && amount ? Number(amount) : undefined,
        },
        idempotencyKey
      ),
    onSuccess: () => {
      onSuccess(partial ? "Reembolso parcial criado com sucesso." : "Reembolso total criado com sucesso.");
      onOpenChange(false);
      setReason("");
      setAmount("");
      setPartial(false);
    },
    onError: (err) => {
      onError(apiErrorMessage(err, "Não foi possível criar o reembolso."));
    },
  });

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Reembolsar venda</DialogTitle>
          <DialogDescription>
            Máximo reembolsável: {formatCurrency(maxRefundable)}. Deixe em branco para reembolso total.
          </DialogDescription>
        </DialogHeader>
        <div className="space-y-3">
          <div className="flex items-center gap-2">
            <input
              id="partial-refund"
              type="checkbox"
              checked={partial}
              onChange={(e) => setPartial(e.target.checked)}
              className="h-4 w-4"
            />
            <Label htmlFor="partial-refund">Reembolso parcial</Label>
          </div>
          {partial && (
            <div className="space-y-1">
              <Label htmlFor="refund-amount">Montante (MZN)</Label>
              <Input
                id="refund-amount"
                type="number"
                min={0}
                max={maxRefundable}
                step="0.01"
                value={amount}
                onChange={(e) => setAmount(e.target.value)}
                placeholder={maxRefundable.toFixed(2)}
              />
            </div>
          )}
          <div className="space-y-1">
            <Label htmlFor="refund-reason">Motivo</Label>
            <Textarea
              id="refund-reason"
              value={reason}
              maxLength={500}
              onChange={(e) => setReason(e.target.value)}
              placeholder="Ex.: artigo em falta, reclamação do cliente…"
            />
          </div>
        </div>
        {mutation.isError && (
          <p className="text-sm text-destructive">{apiErrorMessage(mutation.error, "Erro ao reembolsar.")}</p>
        )}
        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>
            Fechar
          </Button>
          <Button
            disabled={!reason.trim() || (partial && !amount) || mutation.isPending}
            onClick={() => mutation.mutate()}
          >
            {mutation.isPending ? "A processar…" : "Confirmar reembolso"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

function ResendDialog({
  open,
  onOpenChange,
  orderId,
  onSuccess,
  onError,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  orderId: string;
  onSuccess: (message: string) => void;
  onError: (message: string) => void;
}) {
  const [type, setType] = useState<SalesNotificationType>("CONFIRMATION");

  const mutation = useMutation({
    mutationFn: () => salesService.resendNotification(orderId, type),
    onSuccess: () => {
      onSuccess("Notificação reenviada.");
      onOpenChange(false);
    },
    onError: (err) => {
      onError(apiErrorMessage(err, "Não foi possível reenviar a notificação."));
    },
  });

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Reenviar notificação</DialogTitle>
          <DialogDescription>
            Seleccione o tipo de notificação a reenviar ao cliente. O código de entrega nunca é
            devolvido nem registado.
          </DialogDescription>
        </DialogHeader>
        <div className="space-y-1">
          <Label>Tipo</Label>
          <Select value={type} onValueChange={(v) => setType(v as SalesNotificationType)}>
            <SelectTrigger>
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {Object.entries(NOTIFICATION_TYPE_LABELS).map(([value, label]) => (
                <SelectItem key={value} value={value}>
                  {label}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
        {mutation.isError && (
          <p className="text-sm text-destructive">{apiErrorMessage(mutation.error, "Erro ao reenviar.")}</p>
        )}
        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>
            Fechar
          </Button>
          <Button disabled={mutation.isPending} onClick={() => mutation.mutate()}>
            {mutation.isPending ? "A enviar…" : "Reenviar"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
