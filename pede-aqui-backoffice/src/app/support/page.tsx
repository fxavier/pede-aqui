"use client";

import { useEffect, useState } from "react";
import { AppShell } from "@/components/layout/app-shell";
import { supportService } from "@/lib/api/services";
import type { SupportTicket } from "@/lib/api/types";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Plus, MessageSquare } from "lucide-react";

const STATUS_LABELS: Record<string, string> = {
  OPEN: "Aberto",
  IN_PROGRESS: "Em Curso",
  RESOLVED: "Resolvido",
  CANCELLED: "Cancelado",
};

const STATUS_VARIANT: Record<string, "default" | "secondary" | "outline" | "success"> = {
  OPEN: "default",
  IN_PROGRESS: "secondary",
  RESOLVED: "success",
  CANCELLED: "outline",
};

const CLASSIFICATION_LABELS: Record<string, string> = {
  GENERAL: "Geral",
  PAYMENT: "Pagamento",
  DELIVERY: "Entrega",
  VENDOR: "Vendedor",
  COURIER: "Estafeta",
  FRAUD: "Fraude",
  CRITICAL: "Crítico",
};

const CLASSIFICATIONS = ["GENERAL", "PAYMENT", "DELIVERY", "VENDOR", "COURIER", "FRAUD", "CRITICAL"];
const STATUSES = ["OPEN", "IN_PROGRESS", "RESOLVED", "CANCELLED"];

function fmtDate(iso: string) {
  return new Date(iso).toLocaleString("pt-MZ", { dateStyle: "short", timeStyle: "short" });
}

export default function SupportPage() {
  const [tickets, setTickets] = useState<SupportTicket[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [selected, setSelected] = useState<SupportTicket | null>(null);
  const [actionLoading, setActionLoading] = useState(false);

  // Note form state
  const [noteText, setNoteText] = useState("");

  // Create form state
  const [showCreate, setShowCreate] = useState(false);
  const [createSubject, setCreateSubject] = useState("");
  const [createDescription, setCreateDescription] = useState("");
  const [createOrderId, setCreateOrderId] = useState("");
  const [creating, setCreating] = useState(false);

  useEffect(() => {
    supportService.list()
      .then(setTickets)
      .catch(() => setError("Erro ao carregar tickets"))
      .finally(() => setLoading(false));
  }, []);

  function updateTicket(updated: SupportTicket) {
    setTickets(prev => prev.map(t => t.id === updated.id ? updated : t));
    setSelected(updated);
  }

  async function runAction(fn: () => Promise<SupportTicket>) {
    setActionLoading(true);
    try { updateTicket(await fn()); } finally { setActionLoading(false); }
  }

  async function handleCreate(e: React.FormEvent) {
    e.preventDefault();
    if (!createSubject.trim() || !createDescription.trim()) return;
    setCreating(true);
    try {
      const ticket = await supportService.create({
        subject: createSubject,
        description: createDescription,
        orderId: createOrderId.trim() || undefined,
      });
      setTickets(prev => [ticket, ...prev]);
      setCreateSubject("");
      setCreateDescription("");
      setCreateOrderId("");
      setShowCreate(false);
    } finally {
      setCreating(false);
    }
  }

  async function handleAddNote() {
    if (!selected || !noteText.trim()) return;
    await runAction(() => supportService.addInternalNote(selected.id, noteText));
    setNoteText("");
  }

  if (loading) return <div className="p-8 text-muted-foreground">A carregar tickets…</div>;
  if (error) return <div className="p-8 text-destructive">{error}</div>;

  const open = tickets.filter(t => t.status === "OPEN").length;
  const inProgress = tickets.filter(t => t.status === "IN_PROGRESS").length;

  return (
    <AppShell>
      <main className="space-y-6 p-4 md:p-8">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold">Apoio ao Cliente</h1>
          <p className="text-muted-foreground">
            {open} abertos · {inProgress} em curso · {tickets.length} total
          </p>
        </div>
        <Button onClick={() => { setShowCreate(v => !v); setSelected(null); }}>
          <Plus className="h-4 w-4 mr-1" /> Novo Ticket
        </Button>
      </div>

      {/* Create form */}
      {showCreate && (
        <Card>
          <CardHeader><CardTitle className="text-base">Criar Ticket</CardTitle></CardHeader>
          <CardContent>
            <form onSubmit={handleCreate} className="space-y-3">
              <Input
                placeholder="Assunto"
                value={createSubject}
                onChange={e => setCreateSubject(e.target.value)}
                required
              />
              <textarea
                className="w-full border border-input rounded-md px-3 py-2 text-sm resize-none h-20 focus:outline-none focus:ring-2 focus:ring-ring"
                placeholder="Descrição"
                value={createDescription}
                onChange={e => setCreateDescription(e.target.value)}
                required
              />
              <Input
                placeholder="ID da Encomenda (opcional)"
                value={createOrderId}
                onChange={e => setCreateOrderId(e.target.value)}
              />
              <div className="flex gap-2">
                <Button type="submit" disabled={creating} size="sm">
                  {creating ? "A criar…" : "Criar"}
                </Button>
                <Button type="button" variant="outline" size="sm" onClick={() => setShowCreate(false)}>
                  Cancelar
                </Button>
              </div>
            </form>
          </CardContent>
        </Card>
      )}

      <div className="flex gap-4">
        {/* Ticket list */}
        <div className="flex-1 min-w-0 space-y-2">
          {tickets.length === 0 && (
            <div className="text-center text-muted-foreground py-12">Nenhum ticket de suporte</div>
          )}
          {tickets.map(ticket => (
            <Card
              key={ticket.id}
              className={`cursor-pointer transition-colors hover:bg-muted/50 ${selected?.id === ticket.id ? "ring-2 ring-primary" : ""}`}
              onClick={() => { setSelected(ticket); setShowCreate(false); setNoteText(""); }}
            >
              <CardContent className="py-3 px-4">
                <div className="flex items-start justify-between gap-2">
                  <div className="min-w-0">
                    <p className="font-medium truncate">{ticket.subject}</p>
                    <p className="text-xs text-muted-foreground truncate mt-0.5">{ticket.description}</p>
                  </div>
                  <div className="flex flex-col items-end gap-1 shrink-0">
                    <Badge variant={STATUS_VARIANT[ticket.status] ?? "outline"}>
                      {STATUS_LABELS[ticket.status] ?? ticket.status}
                    </Badge>
                    {ticket.classification && (
                      <Badge variant="secondary" className="text-xs">
                        {CLASSIFICATION_LABELS[ticket.classification] ?? ticket.classification}
                      </Badge>
                    )}
                  </div>
                </div>
                <p className="text-xs text-muted-foreground mt-1">{fmtDate(ticket.createdAt)}</p>
              </CardContent>
            </Card>
          ))}
        </div>

        {/* Detail panel */}
        {selected && (
          <div className="w-80 shrink-0">
            <Card className="sticky top-6">
              <CardHeader className="pb-3">
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    <MessageSquare className="h-4 w-4 text-muted-foreground" />
                    <CardTitle className="text-sm">Detalhe</CardTitle>
                  </div>
                  <Button variant="ghost" size="sm" onClick={() => setSelected(null)}>✕</Button>
                </div>
              </CardHeader>
              <CardContent className="space-y-4 text-sm">
                <div>
                  <p className="font-semibold">{selected.subject}</p>
                  <p className="text-muted-foreground text-xs mt-1">{fmtDate(selected.createdAt)}</p>
                </div>

                <p className="text-sm">{selected.description}</p>

                {selected.orderId && (
                  <div className="text-xs text-muted-foreground">
                    Encomenda: <span className="font-mono">{selected.orderId.substring(0, 8)}…</span>
                  </div>
                )}

                <div className="flex gap-2 flex-wrap">
                  <Badge variant={STATUS_VARIANT[selected.status] ?? "outline"}>
                    {STATUS_LABELS[selected.status] ?? selected.status}
                  </Badge>
                  {selected.classification && (
                    <Badge variant="secondary">
                      {CLASSIFICATION_LABELS[selected.classification] ?? selected.classification}
                    </Badge>
                  )}
                </div>

                {selected.internalNote && (
                  <div className="bg-muted rounded p-2 text-xs">
                    <p className="font-medium mb-1">Nota interna</p>
                    <p>{selected.internalNote}</p>
                  </div>
                )}

                <div className="space-y-2 border-t pt-3">
                  <p className="text-xs font-medium text-muted-foreground uppercase tracking-wide">Classificar</p>
                  <div className="flex flex-wrap gap-1">
                    {CLASSIFICATIONS.map(c => (
                      <button
                        key={c}
                        disabled={actionLoading || selected.classification === c}
                        onClick={() => runAction(() => supportService.classify(selected.id, c))}
                        className={`text-xs px-2 py-1 rounded border transition-colors ${
                          selected.classification === c
                            ? "bg-primary text-primary-foreground border-primary"
                            : "border-outline-variant hover:bg-muted"
                        }`}
                      >
                        {CLASSIFICATION_LABELS[c]}
                      </button>
                    ))}
                  </div>
                </div>

                <div className="space-y-2 border-t pt-3">
                  <p className="text-xs font-medium text-muted-foreground uppercase tracking-wide">Estado</p>
                  <div className="flex flex-wrap gap-1">
                    {STATUSES.map(s => (
                      <button
                        key={s}
                        disabled={actionLoading || selected.status === s}
                        onClick={() => runAction(() => supportService.updateStatus(selected.id, s))}
                        className={`text-xs px-2 py-1 rounded border transition-colors ${
                          selected.status === s
                            ? "bg-primary text-primary-foreground border-primary"
                            : "border-outline-variant hover:bg-muted"
                        }`}
                      >
                        {STATUS_LABELS[s]}
                      </button>
                    ))}
                  </div>
                </div>

                {selected.status !== "RESOLVED" && selected.status !== "CANCELLED" && (
                  <Button
                    size="sm"
                    className="w-full"
                    disabled={actionLoading}
                    onClick={() => runAction(() => supportService.resolve(selected.id))}
                  >
                    Resolver Ticket
                  </Button>
                )}

                <div className="space-y-2 border-t pt-3">
                  <p className="text-xs font-medium text-muted-foreground uppercase tracking-wide">Nota Interna</p>
                  <textarea
                    className="w-full border border-input rounded px-2 py-1 text-xs resize-none h-16 focus:outline-none focus:ring-1 focus:ring-ring"
                    placeholder="Adicionar nota interna…"
                    value={noteText}
                    onChange={e => setNoteText(e.target.value)}
                  />
                  <Button
                    size="sm"
                    variant="outline"
                    className="w-full"
                    disabled={actionLoading || !noteText.trim()}
                    onClick={handleAddNote}
                  >
                    Guardar Nota
                  </Button>
                </div>
              </CardContent>
            </Card>
          </div>
        )}
      </div>
      </main>
    </AppShell>
  );
}
