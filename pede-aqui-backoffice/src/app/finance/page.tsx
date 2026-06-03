"use client";

import { useEffect, useState } from "react";
import { AppShell } from "@/components/layout/app-shell";
import { financeService } from "@/lib/api/services";
import type { Transaction, Commission, Refund, CashReconciliation, FinanceSummary } from "@/lib/api/types";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { DollarSign, TrendingUp, RefreshCw, AlertCircle } from "lucide-react";

function fmt(v: number) {
  return new Intl.NumberFormat("pt-MZ", { style: "currency", currency: "MZN" }).format(v);
}

function fmtDate(iso: string) {
  return new Date(iso).toLocaleString("pt-MZ", { dateStyle: "short", timeStyle: "short" });
}

const REFUND_STATUS_LABELS: Record<string, string> = {
  REQUESTED: "Solicitado",
  APPROVED: "Aprovado",
  REJECTED: "Rejeitado",
  REFUNDED: "Reembolsado",
};

const TX_STATUS_LABELS: Record<string, string> = {
  PENDING: "Pendente",
  CONFIRMED: "Confirmado",
  FAILED: "Falhou",
  REFUNDED: "Reembolsado",
  REFUND_PENDING: "Reemb. Pendente",
};

function shortId(id: string) {
  return id.substring(0, 8) + "…";
}

export default function FinancePage() {
  const [summary, setSummary] = useState<FinanceSummary | null>(null);
  const [transactions, setTransactions] = useState<Transaction[]>([]);
  const [commissions, setCommissions] = useState<Commission[]>([]);
  const [refunds, setRefunds] = useState<Refund[]>([]);
  const [reconciliation, setReconciliation] = useState<CashReconciliation[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [actionLoading, setActionLoading] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);

  useEffect(() => {
    setLoading(true);
    Promise.all([
      financeService.getSummary(),
      financeService.getTransactions(),
      financeService.getCommissions(),
      financeService.getRefunds(),
      financeService.getCashReconciliation(),
    ])
      .then(([s, tx, comm, ref, rec]) => {
        setSummary(s);
        setTransactions(tx);
        setCommissions(comm);
        setRefunds(ref);
        setReconciliation(rec);
      })
      .catch(() => setError("Erro ao carregar dados financeiros"))
      .finally(() => setLoading(false));
  }, []);

  async function handleRefundAction(id: string, action: "approve" | "reject") {
    setActionLoading(id + action);
    try {
      const updated = action === "approve"
        ? await financeService.approveRefund(id)
        : await financeService.rejectRefund(id);
      setRefunds(prev => prev.map(r => r.id === updated.id ? updated : r));
    } catch {
      setActionError("Erro ao processar reembolso. Tente novamente.");
    } finally {
      setActionLoading(null);
    }
  }

  return (
    <AppShell>
      <main className="space-y-6 p-4 md:p-8">
        <div>
          <h1 className="text-2xl font-bold">Finanças</h1>
          <p className="text-muted-foreground">Transacções, comissões, reembolsos e reconciliação de caixa.</p>
        </div>

        {loading && (
          <div className="text-muted-foreground">A carregar dados financeiros…</div>
        )}

        {error && (
          <div className="text-destructive">{error}</div>
        )}

        {!loading && !error && (
          <>
            {/* KPI row */}
            {summary && (
        <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
          <Card>
            <CardHeader className="pb-2 flex flex-row items-center justify-between">
              <CardTitle className="text-sm font-medium text-muted-foreground">Pagamentos Confirmados</CardTitle>
              <DollarSign className="h-4 w-4 text-muted-foreground" />
            </CardHeader>
            <CardContent><div className="text-2xl font-bold">{fmt(summary.confirmedPaymentsTotal)}</div></CardContent>
          </Card>
          <Card>
            <CardHeader className="pb-2 flex flex-row items-center justify-between">
              <CardTitle className="text-sm font-medium text-muted-foreground">Comissões</CardTitle>
              <TrendingUp className="h-4 w-4 text-muted-foreground" />
            </CardHeader>
            <CardContent><div className="text-2xl font-bold">{fmt(summary.commissionTotal)}</div></CardContent>
          </Card>
          <Card>
            <CardHeader className="pb-2 flex flex-row items-center justify-between">
              <CardTitle className="text-sm font-medium text-muted-foreground">Reembolsos</CardTitle>
              <RefreshCw className="h-4 w-4 text-muted-foreground" />
            </CardHeader>
            <CardContent><div className="text-2xl font-bold">{fmt(summary.refundsTotal)}</div></CardContent>
          </Card>
          <Card>
            <CardHeader className="pb-2 flex flex-row items-center justify-between">
              <CardTitle className="text-sm font-medium text-muted-foreground">Caixa Não Reconciliado</CardTitle>
              <AlertCircle className="h-4 w-4 text-muted-foreground" />
            </CardHeader>
            <CardContent><div className="text-2xl font-bold">{fmt(summary.unreconciledCashTotal)}</div></CardContent>
          </Card>
        </div>
      )}

      {/* Tabs */}
      <Tabs defaultValue="transactions">
        <TabsList>
          <TabsTrigger value="transactions">Transacções ({transactions.length})</TabsTrigger>
          <TabsTrigger value="commissions">Comissões ({commissions.length})</TabsTrigger>
          <TabsTrigger value="refunds">Reembolsos ({refunds.length})</TabsTrigger>
          <TabsTrigger value="reconciliation">Reconciliação ({reconciliation.length})</TabsTrigger>
        </TabsList>

        {/* Transacções */}
        <TabsContent value="transactions">
          <Card>
            <CardContent className="p-0">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>ID</TableHead>
                    <TableHead>Encomenda</TableHead>
                    <TableHead className="text-right">Valor</TableHead>
                    <TableHead>Estado</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {transactions.length === 0 && (
                    <TableRow><TableCell colSpan={4} className="text-center text-muted-foreground py-8">Sem transacções</TableCell></TableRow>
                  )}
                  {transactions.map(tx => (
                    <TableRow key={tx.id}>
                      <TableCell className="font-mono text-xs">{shortId(tx.id)}</TableCell>
                      <TableCell className="font-mono text-xs">{shortId(tx.orderId)}</TableCell>
                      <TableCell className="text-right font-medium">{fmt(tx.amount)}</TableCell>
                      <TableCell>
                        <Badge variant="outline">{TX_STATUS_LABELS[tx.status] ?? tx.status}</Badge>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </CardContent>
          </Card>
        </TabsContent>

        {/* Comissões */}
        <TabsContent value="commissions">
          <Card>
            <CardContent className="p-0">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Encomenda</TableHead>
                    <TableHead>Vendedor</TableHead>
                    <TableHead className="text-right">Comissão</TableHead>
                    <TableHead>Estado</TableHead>
                    <TableHead>Data</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {commissions.length === 0 && (
                    <TableRow><TableCell colSpan={5} className="text-center text-muted-foreground py-8">Sem comissões</TableCell></TableRow>
                  )}
                  {commissions.map(c => (
                    <TableRow key={c.id}>
                      <TableCell className="font-mono text-xs">{shortId(c.orderId)}</TableCell>
                      <TableCell className="font-mono text-xs">{shortId(c.vendorId)}</TableCell>
                      <TableCell className="text-right font-medium">{fmt(c.commissionAmount)}</TableCell>
                      <TableCell><Badge variant="outline">{c.status}</Badge></TableCell>
                      <TableCell className="text-muted-foreground text-sm">{fmtDate(c.createdAt)}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </CardContent>
          </Card>
        </TabsContent>

        {/* Reembolsos */}
        <TabsContent value="refunds">
          <Card>
            {actionError && (
              <CardContent className="pt-6 pb-0">
                <div className="flex items-center justify-between bg-destructive/10 text-destructive border border-destructive/20 rounded-md p-3">
                  <span className="text-sm">{actionError}</span>
                  <Button size="sm" variant="ghost" onClick={() => setActionError(null)}>✕</Button>
                </div>
              </CardContent>
            )}
            <CardContent className="p-0">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Encomenda</TableHead>
                    <TableHead className="text-right">Valor</TableHead>
                    <TableHead>Motivo</TableHead>
                    <TableHead>Estado</TableHead>
                    <TableHead>Ações</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {refunds.length === 0 && (
                    <TableRow><TableCell colSpan={5} className="text-center text-muted-foreground py-8">Sem reembolsos</TableCell></TableRow>
                  )}
                  {refunds.map(r => (
                    <TableRow key={r.id}>
                      <TableCell className="font-mono text-xs">{shortId(r.orderId)}</TableCell>
                      <TableCell className="text-right font-medium">{fmt(r.amount)}</TableCell>
                      <TableCell className="text-sm max-w-48 truncate">{r.reason}</TableCell>
                      <TableCell>
                        <Badge variant={r.status === "REJECTED" ? "destructive" : r.status === "REFUNDED" ? "success" : "outline"}>
                          {REFUND_STATUS_LABELS[r.status] ?? r.status}
                        </Badge>
                      </TableCell>
                      <TableCell>
                        {r.status === "REQUESTED" && (
                          <div className="flex gap-2">
                            <Button
                              size="sm"
                              variant="outline"
                              disabled={actionLoading !== null}
                              onClick={() => handleRefundAction(r.id, "approve")}
                            >
                              {actionLoading === r.id + "approve" ? "…" : "Aprovar"}
                            </Button>
                            <Button
                              size="sm"
                              variant="outline"
                              disabled={actionLoading !== null}
                              onClick={() => handleRefundAction(r.id, "reject")}
                              className="text-destructive"
                            >
                              {actionLoading === r.id + "reject" ? "…" : "Rejeitar"}
                            </Button>
                          </div>
                        )}
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </CardContent>
          </Card>
        </TabsContent>

        {/* Reconciliação */}
        <TabsContent value="reconciliation">
          <Card>
            <CardContent className="p-0">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Encomenda</TableHead>
                    <TableHead>Estafeta</TableHead>
                    <TableHead className="text-right">Valor</TableHead>
                    <TableHead>Estado</TableHead>
                    <TableHead>Registado em</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {reconciliation.length === 0 && (
                    <TableRow><TableCell colSpan={5} className="text-center text-muted-foreground py-8">Sem registos de reconciliação</TableCell></TableRow>
                  )}
                  {reconciliation.map(rec => (
                    <TableRow key={rec.id}>
                      <TableCell className="font-mono text-xs">{shortId(rec.orderId)}</TableCell>
                      <TableCell className="font-mono text-xs">{shortId(rec.courierId)}</TableCell>
                      <TableCell className="text-right font-medium">{fmt(rec.amount)}</TableCell>
                      <TableCell><Badge variant="outline">{rec.status}</Badge></TableCell>
                      <TableCell className="text-muted-foreground text-sm">{fmtDate(rec.recordedAt)}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </CardContent>
          </Card>
        </TabsContent>
          </Tabs>
          </>
        )}
      </main>
    </AppShell>
  );
}
