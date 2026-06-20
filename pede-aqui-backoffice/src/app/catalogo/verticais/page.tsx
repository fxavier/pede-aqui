"use client";

import { useEffect, useState } from "react";
import { AppShell } from "@/components/layout/app-shell";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import {
  Dialog, DialogContent, DialogHeader, DialogTitle, DialogDescription,
} from "@/components/ui/dialog";
import { Switch } from "@/components/ui/switch";
import { TableSkeleton } from "@/components/ui/loading-skeleton";
import { EmptyState } from "@/components/ui/empty-state";
import { ErrorState } from "@/components/ui/error-state";
import { verticalService } from "@/lib/api/services";
import type { Vertical } from "@/lib/api/types";
import { Plus, Edit, Trash2, Layers } from "lucide-react";

type FormData = { label: string; active: boolean };
const EMPTY_FORM: FormData = { label: "", active: true };

function VerticalForm({
  formData,
  setFormData,
  onSubmit,
  submitLabel,
  showActive,
}: {
  formData: FormData;
  setFormData: React.Dispatch<React.SetStateAction<FormData>>;
  onSubmit: (e: React.FormEvent) => void;
  submitLabel: string;
  showActive: boolean;
}) {
  return (
    <form onSubmit={onSubmit} className="space-y-4">
      <div>
        <Label htmlFor="vertical-label">Nome da Vertical</Label>
        <Input
          id="vertical-label"
          value={formData.label}
          onChange={(e) => setFormData((p) => ({ ...p, label: e.target.value }))}
          placeholder="ex: Alimentação"
          required
          autoFocus
        />
        <p className="mt-1 text-xs text-on-surface-variant">
          O identificador (slug) é gerado automaticamente a partir do nome.
        </p>
      </div>
      {showActive && (
        <div className="flex items-center gap-3">
          <Switch
            id="vertical-active"
            checked={formData.active}
            onCheckedChange={(v) => setFormData((p) => ({ ...p, active: v }))}
          />
          <Label htmlFor="vertical-active">Vertical ativa</Label>
        </div>
      )}
      <div className="flex justify-end pt-2">
        <Button type="submit">{submitLabel}</Button>
      </div>
    </form>
  );
}

export default function VerticaisPage() {
  const [verticals, setVerticals] = useState<Vertical[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [createOpen, setCreateOpen] = useState(false);
  const [editOpen, setEditOpen] = useState(false);
  const [editing, setEditing] = useState<Vertical | null>(null);
  const [formData, setFormData] = useState<FormData>(EMPTY_FORM);
  const [saving, setSaving] = useState(false);
  const [deleting, setDeleting] = useState<string | null>(null);

  const load = async () => {
    setLoading(true);
    setError(null);
    try {
      setVerticals(await verticalService.list());
    } catch {
      setError("Erro ao carregar verticais.");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { load(); }, []);

  function openCreate() {
    setFormData(EMPTY_FORM);
    setCreateOpen(true);
  }

  function openEdit(v: Vertical) {
    setEditing(v);
    setFormData({ label: v.label, active: v.active });
    setEditOpen(true);
  }

  async function handleCreate(e: React.FormEvent) {
    e.preventDefault();
    setSaving(true);
    try {
      const created = await verticalService.create({ label: formData.label });
      setVerticals((prev) => [...prev, created]);
      setCreateOpen(false);
    } catch {
      setError("Erro ao criar vertical.");
    } finally {
      setSaving(false);
    }
  }

  async function handleEdit(e: React.FormEvent) {
    e.preventDefault();
    if (!editing) return;
    setSaving(true);
    try {
      const updated = await verticalService.update(editing.id, { label: formData.label, active: formData.active });
      setVerticals((prev) => prev.map((v) => (v.id === updated.id ? updated : v)));
      setEditOpen(false);
      setEditing(null);
    } catch {
      setError("Erro ao actualizar vertical.");
    } finally {
      setSaving(false);
    }
  }

  async function handleDelete(id: string) {
    if (!confirm("Tem a certeza que deseja eliminar esta vertical? As categorias associadas perderão a referência.")) return;
    setDeleting(id);
    try {
      await verticalService.delete(id);
      setVerticals((prev) => prev.filter((v) => v.id !== id));
    } catch {
      setError("Erro ao eliminar vertical. Verifique se não tem categorias associadas.");
    } finally {
      setDeleting(null);
    }
  }

  return (
    <AppShell>
      <main className="space-y-6 p-4 md:p-8">
        <div className="flex items-start justify-between">
          <div>
            <h1 className="font-headline-lg text-headline-lg text-on-surface">Verticais</h1>
            <p className="mt-1 text-body-md text-on-surface-variant">
              Gestão das verticais de negócio utilizadas para classificar categorias.
            </p>
          </div>
          <Button onClick={openCreate}>
            <Plus className="mr-2 h-4 w-4" />
            Nova Vertical
          </Button>
        </div>

        {error && <ErrorState message={error} onRetry={load} />}

        <Card>
          <CardHeader>
            <CardTitle>Lista de Verticais</CardTitle>
          </CardHeader>
          <CardContent>
            {loading ? (
              <TableSkeleton />
            ) : verticals.length === 0 ? (
              <EmptyState message="Nenhuma vertical criada ainda." />
            ) : (
              <div className="overflow-x-auto">
                <table className="w-full text-sm">
                  <thead>
                    <tr className="border-b border-outline-variant text-xs font-bold uppercase tracking-wider text-on-surface-variant">
                      <th className="pb-3 pr-4 text-left">Nome</th>
                      <th className="pb-3 pr-4 text-left">Slug</th>
                      <th className="pb-3 pr-4 text-left">Estado</th>
                      <th className="pb-3 text-right">Acções</th>
                    </tr>
                  </thead>
                  <tbody>
                    {verticals.map((v) => (
                      <tr key={v.id} className="border-b border-outline-variant/50 last:border-0">
                        <td className="py-3 pr-4 font-bold text-on-surface">
                          <div className="flex items-center gap-2">
                            <Layers className="h-4 w-4 shrink-0 text-on-surface-variant" />
                            {v.label}
                          </div>
                        </td>
                        <td className="py-3 pr-4 font-mono text-xs text-on-surface-variant">{v.slug}</td>
                        <td className="py-3 pr-4">
                          {v.active ? (
                            <Badge className="bg-green-100 text-green-800 border-green-200">Ativa</Badge>
                          ) : (
                            <Badge variant="secondary">Inativa</Badge>
                          )}
                        </td>
                        <td className="py-3 text-right">
                          <div className="flex justify-end gap-2">
                            <Button variant="outline" size="sm" onClick={() => openEdit(v)}>
                              <Edit className="h-4 w-4" />
                            </Button>
                            <Button
                              variant="outline"
                              size="sm"
                              className="text-red-600 hover:text-red-700"
                              onClick={() => handleDelete(v.id)}
                              disabled={deleting === v.id}
                            >
                              <Trash2 className="h-4 w-4" />
                            </Button>
                          </div>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </CardContent>
        </Card>

        {/* Create dialog */}
        <Dialog open={createOpen} onOpenChange={setCreateOpen}>
          <DialogContent>
            <DialogHeader>
              <DialogTitle>Nova Vertical</DialogTitle>
              <DialogDescription>
                Crie uma nova vertical de negócio para classificar categorias de produtos.
              </DialogDescription>
            </DialogHeader>
            <VerticalForm
              formData={formData}
              setFormData={setFormData}
              onSubmit={handleCreate}
              submitLabel={saving ? "A criar..." : "Criar Vertical"}
              showActive={false}
            />
          </DialogContent>
        </Dialog>

        {/* Edit dialog */}
        <Dialog open={editOpen} onOpenChange={setEditOpen}>
          <DialogContent>
            <DialogHeader>
              <DialogTitle>Editar Vertical</DialogTitle>
              <DialogDescription>
                Altere o nome ou estado da vertical. O slug é recalculado automaticamente.
              </DialogDescription>
            </DialogHeader>
            <VerticalForm
              formData={formData}
              setFormData={setFormData}
              onSubmit={handleEdit}
              submitLabel={saving ? "A guardar..." : "Guardar Alterações"}
              showActive
            />
          </DialogContent>
        </Dialog>
      </main>
    </AppShell>
  );
}
