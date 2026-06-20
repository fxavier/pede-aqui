"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { useAppDispatch, useAppSelector } from "@/store/hooks";
import { enterTenant, logout } from "@/store/slices/auth-slice";
import { platformService, tenantService } from "@/lib/api/services";
import type { PlatformStats, Tenant } from "@/lib/api/types";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import {
  Dialog, DialogContent, DialogHeader, DialogTitle, DialogDescription,
} from "@/components/ui/dialog";
import {
  Building2, Users, Truck, Store, LogOut, Plus,
  ShieldCheck, ArrowRight, ToggleLeft, ToggleRight, Globe,
} from "lucide-react";
import { formatDate } from "@/lib/utils";

type CreateForm = { name: string; slug: string; defaultCurrency: string };
const EMPTY_FORM: CreateForm = { name: "", slug: "", defaultCurrency: "MZN" };

function slugify(value: string) {
  return value.toLowerCase().trim().replace(/[^a-z0-9]+/g, "-").replace(/(^-|-$)/g, "");
}

function StatCard({ icon: Icon, label, value, sub }: {
  icon: React.ElementType; label: string; value: number; sub?: string;
}) {
  return (
    <Card>
      <CardContent className="flex items-center gap-3 p-4 sm:gap-4 sm:p-5">
        <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-2xl bg-primary/10 sm:h-12 sm:w-12">
          <Icon className="h-5 w-5 text-primary sm:h-6 sm:w-6" />
        </div>
        <div className="min-w-0">
          <p className="truncate text-xs font-bold uppercase tracking-wider text-on-surface-variant">{label}</p>
          <p className="text-2xl font-bold text-on-surface sm:text-3xl">{value}</p>
          {sub && <p className="truncate text-xs text-on-surface-variant">{sub}</p>}
        </div>
      </CardContent>
    </Card>
  );
}

export default function PlatformPage() {
  const router = useRouter();
  const dispatch = useAppDispatch();
  const user = useAppSelector((s) => s.auth.user);

  const [stats, setStats] = useState<PlatformStats | null>(null);
  const [tenants, setTenants] = useState<Tenant[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [createOpen, setCreateOpen] = useState(false);
  const [form, setForm] = useState<CreateForm>(EMPTY_FORM);
  const [creating, setCreating] = useState(false);
  const [togglingId, setTogglingId] = useState<string | null>(null);

  const load = async () => {
    setLoading(true);
    setError(null);
    try {
      const [s, t] = await Promise.all([
        platformService.getStats(),
        tenantService.list(),
      ]);
      setStats(s);
      setTenants(t);
    } catch {
      setError("Erro ao carregar dados da plataforma.");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { load(); }, []);

  async function handleCreate(e: React.FormEvent) {
    e.preventDefault();
    setCreating(true);
    try {
      const created = await tenantService.create({
        name: form.name,
        slug: form.slug || slugify(form.name),
        defaultCurrency: form.defaultCurrency,
      });
      setTenants((prev) => [...prev, created]);
      setStats((prev) => prev ? { ...prev, totalTenants: prev.totalTenants + 1, activeTenants: prev.activeTenants + 1 } : prev);
      setForm(EMPTY_FORM);
      setCreateOpen(false);
    } catch {
      setError("Erro ao criar tenant. O slug pode já existir.");
    } finally {
      setCreating(false);
    }
  }

  async function handleToggleStatus(tenant: Tenant) {
    setTogglingId(tenant.id);
    const newStatus = tenant.status === "ACTIVE" ? "INACTIVE" : "ACTIVE";
    try {
      const updated = await tenantService.updateStatus(tenant.id, newStatus);
      setTenants((prev) => prev.map((t) => (t.id === updated.id ? updated : t)));
      setStats((prev) => {
        if (!prev) return prev;
        const delta = newStatus === "ACTIVE" ? 1 : -1;
        return { ...prev, activeTenants: prev.activeTenants + delta, inactiveTenants: prev.inactiveTenants - delta };
      });
    } catch {
      setError("Erro ao alterar estado do tenant.");
    } finally {
      setTogglingId(null);
    }
  }

  function handleEnterTenant(tenant: Tenant) {
    dispatch(enterTenant({ tenantId: tenant.id, tenantName: tenant.name }));
    router.push("/");
  }

  function handleLogout() {
    sessionStorage.removeItem("auth_token");
    sessionStorage.removeItem("tenant_id");
    document.cookie = "auth_token=; path=/; max-age=0; SameSite=Strict";
    dispatch(logout());
    router.push("/login");
  }

  return (
    <div className="min-h-screen bg-[radial-gradient(circle_at_top_left,#ffdad2_0,transparent_32%),linear-gradient(135deg,#fff8f6_0%,#fff1ed_45%,#fff8f6_100%)]">
      {/* Top bar */}
      <header className="sticky top-0 z-20 flex h-16 items-center justify-between border-b border-outline-variant bg-white/80 px-6 backdrop-blur-xl">
        <div className="flex items-center gap-3">
          <div className="flex h-9 w-9 items-center justify-center rounded-xl bg-primary text-on-primary">
            <Globe className="h-4 w-4" />
          </div>
          <div>
            <p className="text-sm font-bold text-on-surface">Pede Aqui</p>
            <p className="text-xs font-bold uppercase tracking-wider text-primary">Super Admin</p>
          </div>
        </div>
        <div className="flex items-center gap-3">
          <div className="hidden text-right sm:block">
            <p className="text-sm font-bold text-on-surface">{user?.name ?? "Utilizador"}</p>
            <p className="text-xs text-on-surface-variant">{user?.email}</p>
          </div>
          <div className="flex h-9 w-9 items-center justify-center rounded-full bg-primary text-xs font-bold text-on-primary">
            {user?.name?.split(" ").map((n) => n[0]).join("").slice(0, 2).toUpperCase() ?? "SA"}
          </div>
          <Button variant="ghost" size="icon" onClick={handleLogout} aria-label="Terminar sessão">
            <LogOut className="h-4 w-4" />
          </Button>
        </div>
      </header>

      <main className="mx-auto max-w-6xl space-y-8 p-6 md:p-10">
        {/* Title */}
        <div className="flex flex-wrap items-start justify-between gap-4">
          <div>
            <h1 className="text-2xl font-bold text-on-surface">Painel da Plataforma</h1>
            <p className="mt-1 text-sm text-on-surface-variant">
              Visibilidade e controlo global sobre todos os tenants.
            </p>
          </div>
          <Button onClick={() => setCreateOpen(true)}>
            <Plus className="mr-2 h-4 w-4" />
            Novo Tenant
          </Button>
        </div>

        {error && (
          <div className="rounded-xl bg-red-50 p-4 text-sm text-red-700">{error}</div>
        )}

        {/* Stats */}
        {stats && (
          <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-5">
            <StatCard icon={Building2} label="Total Tenants" value={stats.totalTenants} />
            <StatCard icon={ShieldCheck} label="Ativos" value={stats.activeTenants} sub={`${stats.inactiveTenants} inativos`} />
            <StatCard icon={Users} label="Utilizadores" value={stats.totalUsers} />
            <StatCard icon={Truck} label="Estafetas" value={stats.totalCouriers} />
            <StatCard icon={Store} label="Vendedores" value={stats.totalVendors} />
          </div>
        )}

        {/* Tenant table */}
        <Card>
          <CardHeader>
            <CardTitle>Todos os Tenants</CardTitle>
          </CardHeader>
          <CardContent>
            {loading ? (
              <p className="text-sm text-on-surface-variant">A carregar...</p>
            ) : tenants.length === 0 ? (
              <p className="text-sm text-on-surface-variant">Nenhum tenant criado ainda.</p>
            ) : (
              <div className="overflow-x-auto">
                <table className="w-full text-sm">
                  <thead>
                    <tr className="border-b border-outline-variant text-xs font-bold uppercase tracking-wider text-on-surface-variant">
                      <th className="pb-3 pr-4 text-left">Nome</th>
                      <th className="pb-3 pr-4 text-left">Slug</th>
                      <th className="pb-3 pr-4 text-left">Moeda</th>
                      <th className="pb-3 pr-4 text-left">Estado</th>
                      <th className="pb-3 pr-4 text-left">Criado</th>
                      <th className="pb-3 text-right">Acções</th>
                    </tr>
                  </thead>
                  <tbody>
                    {tenants.map((tenant) => (
                      <tr key={tenant.id} className="border-b border-outline-variant/50 last:border-0">
                        <td className="py-3 pr-4 font-bold text-on-surface">{tenant.name}</td>
                        <td className="py-3 pr-4 font-mono text-xs text-on-surface-variant">{tenant.slug}</td>
                        <td className="py-3 pr-4 text-on-surface-variant">{tenant.defaultCurrency}</td>
                        <td className="py-3 pr-4">
                          {tenant.status === "ACTIVE" ? (
                            <Badge className="bg-green-100 text-green-800 border-green-200">Ativo</Badge>
                          ) : (
                            <Badge variant="secondary">Inativo</Badge>
                          )}
                        </td>
                        <td className="py-3 pr-4 text-on-surface-variant">
                          {formatDate(tenant.createdAt)}
                        </td>
                        <td className="py-3">
                          <div className="flex justify-end gap-2">
                            <Button
                              variant="outline"
                              size="sm"
                              onClick={() => handleToggleStatus(tenant)}
                              disabled={togglingId === tenant.id}
                              title={tenant.status === "ACTIVE" ? "Desativar" : "Ativar"}
                            >
                              {tenant.status === "ACTIVE"
                                ? <ToggleRight className="h-4 w-4 text-green-600" />
                                : <ToggleLeft className="h-4 w-4 text-on-surface-variant" />}
                            </Button>
                            <Button
                              size="sm"
                              onClick={() => handleEnterTenant(tenant)}
                              disabled={tenant.status !== "ACTIVE"}
                              title="Gerir este tenant"
                            >
                              <ArrowRight className="mr-1 h-4 w-4" />
                              Entrar
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
      </main>

      {/* Create tenant dialog */}
      <Dialog open={createOpen} onOpenChange={setCreateOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Novo Tenant</DialogTitle>
            <DialogDescription>
              Crie uma nova organização na plataforma. O slug deve ser único e conter apenas letras minúsculas, números e hífens.
            </DialogDescription>
          </DialogHeader>
          <form onSubmit={handleCreate} className="space-y-4">
            <div>
              <Label htmlFor="t-name">Nome</Label>
              <Input
                id="t-name"
                value={form.name}
                onChange={(e) => setForm((p) => ({
                  ...p,
                  name: e.target.value,
                  slug: p.slug || slugify(e.target.value),
                }))}
                placeholder="Pede Aqui Maputo"
                required
                autoFocus
              />
            </div>
            <div>
              <Label htmlFor="t-slug">Slug</Label>
              <Input
                id="t-slug"
                value={form.slug}
                onChange={(e) => setForm((p) => ({ ...p, slug: slugify(e.target.value) }))}
                placeholder="pede-aqui-maputo"
                required
              />
              <p className="mt-1 text-xs text-on-surface-variant">Apenas minúsculas, números e hífens.</p>
            </div>
            <div>
              <Label htmlFor="t-currency">Moeda</Label>
              <Input
                id="t-currency"
                value={form.defaultCurrency}
                onChange={(e) => setForm((p) => ({ ...p, defaultCurrency: e.target.value.toUpperCase().slice(0, 3) }))}
                placeholder="MZN"
                maxLength={3}
                required
              />
            </div>
            <div className="flex justify-end pt-2">
              <Button type="submit" disabled={creating}>
                {creating ? "A criar..." : "Criar Tenant"}
              </Button>
            </div>
          </form>
        </DialogContent>
      </Dialog>
    </div>
  );
}
