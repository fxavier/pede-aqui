"use client";

import { useEffect, useState } from "react";
import { AppShell } from "@/components/layout/app-shell";
import { Card, CardContent, CardHeader } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { StatusBadge } from "@/components/ui/status-badge";
import { ErrorState } from "@/components/ui/error-state";
import { EmptyState } from "@/components/ui/empty-state";
import { vendorService, categoryService, uploadService } from "@/lib/api/services";
import { cn } from "@/lib/utils";
import type {
  Vendor,
  VendorOpeningHour,
  VendorOpeningHourRequest,
  VendorDocument,
  Category,
} from "@/lib/api/types";
import { FileText, Image, Trash2, Upload } from "lucide-react";

type Tab = "perfil" | "horarios" | "documentos";

const DAY_LABELS: Record<number, string> = {
  1: "Segunda-feira",
  2: "Terça-feira",
  3: "Quarta-feira",
  4: "Quinta-feira",
  5: "Sexta-feira",
  6: "Sábado",
  7: "Domingo",
};

function defaultSchedule(): VendorOpeningHourRequest[] {
  return Array.from({ length: 7 }, (_, i) => ({
    dayOfWeek: i + 1,
    opensAt: "09:00:00",
    closesAt: "22:00:00",
    closed: false,
  }));
}

function hoursToSchedule(
  hours: VendorOpeningHour[],
  fallback: VendorOpeningHourRequest[],
): VendorOpeningHourRequest[] {
  return fallback.map((f) => {
    const match = hours.find((h) => h.dayOfWeek === f.dayOfWeek);
    return match
      ? { dayOfWeek: match.dayOfWeek, opensAt: match.opensAt, closesAt: match.closesAt, closed: match.closed }
      : f;
  });
}

export default function EmpresaPage() {
  const [tab, setTab] = useState<Tab>("perfil");
  const [vendor, setVendor] = useState<Vendor | null>(null);
  const [categories, setCategories] = useState<Category[]>([]);
  const [schedule, setSchedule] = useState<VendorOpeningHourRequest[]>(defaultSchedule());
  const [documents, setDocuments] = useState<VendorDocument[]>([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [logoUploading, setLogoUploading] = useState(false);
  const [documentUploading, setDocumentUploading] = useState(false);
  const [selectedDocType, setSelectedDocType] = useState("BUSINESS_LICENCE");

  const [profile, setProfile] = useState({
    name: "",
    categoryId: "",
    ownerName: "",
    nif: "",
    phone: "",
    address: "",
    description: "",
    logoStorageKey: "",
    logoPreview: "",
  });

  useEffect(() => {
    async function load() {
      setLoading(true);
      setError(null);
      try {
        const [vendors, cats] = await Promise.all([vendorService.list(), categoryService.list()]);
        setCategories(cats);
        const v = vendors[0] ?? null;
        setVendor(v);
        if (v) {
          setProfile({
            name: v.name ?? "",
            categoryId: v.categoryId ?? "",
            ownerName: v.ownerName ?? "",
            nif: v.nif ?? "",
            phone: v.phone ?? "",
            address: v.address ?? "",
            description: v.description ?? "",
            logoStorageKey: v.logoStorageKey ?? "",
            logoPreview: "",
          });
          const [hours, docs] = await Promise.all([
            vendorService.getOpeningHours(v.id),
            vendorService.getDocuments(v.id),
          ]);
          if (hours.length > 0) setSchedule(hoursToSchedule(hours, defaultSchedule()));
          setDocuments(docs);
        }
      } catch {
        setError("Erro ao carregar dados da empresa. Tente novamente.");
      } finally {
        setLoading(false);
      }
    }
    load();
  }, []);

  async function saveProfile(e: React.FormEvent) {
    e.preventDefault();
    if (!vendor) return;
    setSaving(true);
    setError(null);
    try {
      const updated = await vendorService.update(vendor.id, {
        name: profile.name,
        categoryId: profile.categoryId,
        ownerName: profile.ownerName || undefined,
        nif: profile.nif || undefined,
        phone: profile.phone || undefined,
        address: profile.address || undefined,
        description: profile.description || undefined,
        logoStorageKey: profile.logoStorageKey || undefined,
      });
      setVendor(updated);
    } catch {
      setError("Erro ao guardar perfil. Tente novamente.");
    } finally {
      setSaving(false);
    }
  }

  async function saveSchedule(e: React.FormEvent) {
    e.preventDefault();
    if (!vendor) return;
    setSaving(true);
    setError(null);
    try {
      const updated = await vendorService.updateOpeningHours(vendor.id, schedule);
      setSchedule(hoursToSchedule(updated, defaultSchedule()));
    } catch {
      setError("Erro ao guardar horários. Tente novamente.");
    } finally {
      setSaving(false);
    }
  }

  async function handleLogoUpload(file: File) {
    setLogoUploading(true);
    try {
      const { uploadUrl, storageKey } = await uploadService.getPresignedUrl({
        purpose: "vendor-logo",
        fileName: file.name,
        contentType: file.type,
      });
      await uploadService.uploadToS3(uploadUrl, file);
      setProfile((p) => ({ ...p, logoStorageKey: storageKey, logoPreview: URL.createObjectURL(file) }));
    } catch {
      setError("Erro ao enviar logotipo.");
    } finally {
      setLogoUploading(false);
    }
  }

  async function handleDocumentUpload(file: File, documentType: string) {
    if (!vendor) return;
    setDocumentUploading(true);
    try {
      const { uploadUrl, storageKey } = await uploadService.getDocumentPresignedUrl({
        purpose: "vendor-document",
        fileName: file.name,
        contentType: file.type,
      });
      await uploadService.uploadToS3(uploadUrl, file);
      const doc = await vendorService.uploadDocument(vendor.id, { documentType, storageKey });
      setDocuments((prev) => [...prev, doc]);
    } catch {
      setError("Erro ao enviar documento.");
    } finally {
      setDocumentUploading(false);
    }
  }

  const tabs: { key: Tab; label: string }[] = [
    { key: "perfil", label: "Perfil" },
    { key: "horarios", label: "Horários" },
    { key: "documentos", label: "Documentos" },
  ];

  return (
    <AppShell>
      <main className="space-y-6 p-4 md:p-8">
        <div className="flex items-start justify-between">
          <div>
            <h1 className="font-headline-lg text-headline-lg text-on-surface">A Minha Empresa</h1>
            <p className="mt-1 text-body-md text-on-surface-variant">
              Perfil, horários e documentos do seu estabelecimento.
            </p>
          </div>
          {vendor && <StatusBadge status={vendor.verificationStatus} />}
        </div>

        {error && <ErrorState message={error} onRetry={() => setError(null)} />}

        {loading ? (
          <Card>
            <CardContent className="flex h-48 items-center justify-center">
              <p className="text-sm text-on-surface-variant">A carregar...</p>
            </CardContent>
          </Card>
        ) : !vendor ? (
          <EmptyState
            title="Sem empresa registada"
            message="Não foi encontrado nenhum estabelecimento associado à sua conta."
          />
        ) : (
          <Card>
            <CardHeader>
              <div className="flex w-fit gap-1 rounded-xl bg-surface-container-low p-1">
                {tabs.map((t) => (
                  <button
                    key={t.key}
                    onClick={() => setTab(t.key)}
                    className={cn(
                      "rounded-lg px-4 py-1.5 text-sm font-bold transition-all",
                      tab === t.key
                        ? "bg-white text-on-surface shadow-sm"
                        : "text-on-surface-variant hover:text-on-surface",
                    )}
                  >
                    {t.label}
                  </button>
                ))}
              </div>
            </CardHeader>

            <CardContent>
              {tab === "perfil" && (
                <form className="max-w-lg space-y-4" onSubmit={saveProfile}>
                  <div className="space-y-2">
                    <label className="text-sm font-bold text-on-surface">Logotipo</label>
                    <div className="flex items-center gap-3">
                      {profile.logoPreview || profile.logoStorageKey ? (
                        <div className="relative">
                          {profile.logoPreview ? (
                            <img
                              src={profile.logoPreview}
                              alt="Logo"
                              className="h-16 w-16 rounded-lg border border-outline-variant object-cover"
                            />
                          ) : (
                            <div className="flex h-16 w-16 items-center justify-center rounded-lg border border-outline-variant bg-surface-container text-xs text-on-surface-variant">
                              Logo
                            </div>
                          )}
                          <button
                            type="button"
                            onClick={() => setProfile((p) => ({ ...p, logoStorageKey: "", logoPreview: "" }))}
                            className="absolute -right-2 -top-2 flex h-6 w-6 items-center justify-center rounded-full bg-error text-white"
                          >
                            <Trash2 className="h-3 w-3" />
                          </button>
                        </div>
                      ) : (
                        <div className="flex h-16 w-16 items-center justify-center rounded-lg border-2 border-dashed border-outline-variant">
                          <Image className="h-6 w-6 text-on-surface-variant" />
                        </div>
                      )}
                      <div>
                        <input
                          type="file"
                          accept="image/*"
                          id="logo-upload"
                          className="hidden"
                          onChange={(e) => { const f = e.target.files?.[0]; if (f) handleLogoUpload(f); }}
                        />
                        <Button
                          type="button"
                          variant="outline"
                          size="sm"
                          disabled={logoUploading}
                          onClick={() => document.getElementById("logo-upload")?.click()}
                        >
                          <Upload className="mr-2 h-4 w-4" />
                          {logoUploading ? "A enviar..." : "Escolher Logotipo"}
                        </Button>
                      </div>
                    </div>
                  </div>

                  <Input
                    placeholder="Nome do estabelecimento *"
                    value={profile.name}
                    onChange={(e) => setProfile((p) => ({ ...p, name: e.target.value }))}
                    required
                  />
                  <Input
                    placeholder="Nome do proprietário"
                    value={profile.ownerName}
                    onChange={(e) => setProfile((p) => ({ ...p, ownerName: e.target.value }))}
                  />
                  <Input
                    placeholder="NIF"
                    value={profile.nif}
                    onChange={(e) => setProfile((p) => ({ ...p, nif: e.target.value }))}
                  />
                  <Input
                    placeholder="Telefone"
                    value={profile.phone}
                    onChange={(e) => setProfile((p) => ({ ...p, phone: e.target.value }))}
                  />
                  <Input
                    placeholder="Endereço"
                    value={profile.address}
                    onChange={(e) => setProfile((p) => ({ ...p, address: e.target.value }))}
                  />
                  <textarea
                    placeholder="Descrição"
                    value={profile.description}
                    onChange={(e) => setProfile((p) => ({ ...p, description: e.target.value }))}
                    className="flex min-h-[80px] w-full resize-none rounded-xl border border-outline-variant bg-white px-4 py-2 text-sm text-on-surface shadow-sm placeholder:text-on-surface-variant focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
                  />
                  <div>
                    <label className="mb-1 block text-sm font-bold text-on-surface">Categoria</label>
                    <select
                      value={profile.categoryId}
                      onChange={(e) => setProfile((p) => ({ ...p, categoryId: e.target.value }))}
                      className="flex h-11 w-full rounded-xl border border-outline-variant bg-white px-4 py-2 text-sm text-on-surface shadow-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
                    >
                      <option value="">Selecione uma categoria</option>
                      {categories.map((c) => (
                        <option key={c.id} value={c.id}>{c.name}</option>
                      ))}
                    </select>
                  </div>

                  <Button type="submit" disabled={saving}>
                    {saving ? "A guardar..." : "Guardar Perfil"}
                  </Button>
                </form>
              )}

              {tab === "horarios" && (
                <form className="space-y-4" onSubmit={saveSchedule}>
                  <div className="overflow-x-auto">
                    <table className="w-full text-sm">
                      <thead>
                        <tr className="border-b border-outline-variant text-xs font-bold uppercase text-on-surface-variant">
                          <th className="pb-3 pr-6 text-left">Dia</th>
                          <th className="pb-3 pr-6 text-left">Fechado</th>
                          <th className="pb-3 pr-6 text-left">Abre às</th>
                          <th className="pb-3 text-left">Fecha às</th>
                        </tr>
                      </thead>
                      <tbody>
                        {schedule.map((row, idx) => (
                          <tr key={row.dayOfWeek} className="border-b border-outline-variant/50 last:border-0">
                            <td className="py-3 pr-6 font-bold text-on-surface">
                              {DAY_LABELS[row.dayOfWeek]}
                            </td>
                            <td className="py-3 pr-6">
                              <input
                                type="checkbox"
                                checked={row.closed}
                                onChange={(e) =>
                                  setSchedule((prev) =>
                                    prev.map((r, i) => i === idx ? { ...r, closed: e.target.checked } : r),
                                  )
                                }
                                className="h-4 w-4 rounded border-outline-variant accent-primary"
                              />
                            </td>
                            <td className="py-3 pr-6">
                              <input
                                type="time"
                                value={row.opensAt?.slice(0, 5) ?? ""}
                                disabled={row.closed}
                                onChange={(e) =>
                                  setSchedule((prev) =>
                                    prev.map((r, i) => i === idx ? { ...r, opensAt: `${e.target.value}:00` } : r),
                                  )
                                }
                                className="rounded-lg border border-outline-variant px-3 py-1.5 text-sm focus:outline-none focus:ring-2 focus:ring-primary disabled:opacity-40"
                              />
                            </td>
                            <td className="py-3">
                              <input
                                type="time"
                                value={row.closesAt?.slice(0, 5) ?? ""}
                                disabled={row.closed}
                                onChange={(e) =>
                                  setSchedule((prev) =>
                                    prev.map((r, i) => i === idx ? { ...r, closesAt: `${e.target.value}:00` } : r),
                                  )
                                }
                                className="rounded-lg border border-outline-variant px-3 py-1.5 text-sm focus:outline-none focus:ring-2 focus:ring-primary disabled:opacity-40"
                              />
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                  <Button type="submit" disabled={saving}>
                    {saving ? "A guardar..." : "Guardar Horários"}
                  </Button>
                </form>
              )}

              {tab === "documentos" && (
                <div className="max-w-lg space-y-4">
                  <div className="flex gap-3">
                    <select
                      value={selectedDocType}
                      onChange={(e) => setSelectedDocType(e.target.value)}
                      className="flex h-11 flex-1 rounded-xl border border-outline-variant bg-white px-4 py-2 text-sm text-on-surface shadow-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
                    >
                      <option value="BUSINESS_LICENCE">Licença Comercial</option>
                      <option value="TAX_CERTIFICATE">Certificado Fiscal</option>
                      <option value="HEALTH_PERMIT">Alvará Sanitário</option>
                      <option value="OTHER">Outro</option>
                    </select>
                    <input
                      type="file"
                      accept=".pdf,.jpeg,.jpg,.png,.webp"
                      id="doc-upload"
                      className="hidden"
                      onChange={(e) => { const f = e.target.files?.[0]; if (f) handleDocumentUpload(f, selectedDocType); }}
                    />
                    <Button
                      type="button"
                      variant="outline"
                      disabled={documentUploading}
                      onClick={() => document.getElementById("doc-upload")?.click()}
                    >
                      <FileText className="mr-2 h-4 w-4" />
                      {documentUploading ? "A enviar..." : "Adicionar"}
                    </Button>
                  </div>

                  {documents.length === 0 ? (
                    <EmptyState message="Nenhum documento submetido ainda." />
                  ) : (
                    <div className="space-y-2">
                      {documents.map((doc) => (
                        <div
                          key={doc.id}
                          className="flex items-center gap-3 rounded-xl border border-outline-variant p-3"
                        >
                          <FileText className="h-5 w-5 shrink-0 text-on-surface-variant" />
                          <div className="min-w-0 flex-1">
                            <p className="text-sm font-bold text-on-surface">{doc.documentType}</p>
                            <p className="mt-0.5 text-xs text-on-surface-variant">{doc.storageKey}</p>
                          </div>
                          <StatusBadge status={doc.status} />
                        </div>
                      ))}
                    </div>
                  )}
                </div>
              )}
            </CardContent>
          </Card>
        )}
      </main>
    </AppShell>
  );
}
