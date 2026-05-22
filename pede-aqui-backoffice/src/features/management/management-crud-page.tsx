"use client";

import { useEffect, useMemo, useState } from "react";
import { AppShell } from "@/components/layout/app-shell";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { managementService } from "@/lib/api/services";

type ManagementItem = {
  id: string;
  nome: string;
  estado: string;
  detalhe: string;
  actualizadoEm: string;
};

type ManagementCrudPageProps = {
  titulo: string;
  descricao: string;
  entidadeSingular: string;
  entidadePlural: string;
  itensIniciais: ManagementItem[];
  apiKey?: keyof typeof managementService;
};

function createEmptyItem(): ManagementItem {
  return {
    id: "",
    nome: "",
    estado: "Activo",
    detalhe: "",
    actualizadoEm: new Date().toISOString(),
  };
}

export function ManagementCrudPage({
  titulo,
  descricao,
  entidadeSingular,
  entidadePlural,
  itensIniciais,
  apiKey,
}: ManagementCrudPageProps) {
  const api = apiKey ? managementService[apiKey] : undefined;
  const [itens, setItens] = useState<ManagementItem[]>(itensIniciais);
  const [loading, setLoading] = useState(false);
  const [erro, setErro] = useState<string | null>(null);
  const [query, setQuery] = useState("");
  const [modo, setModo] = useState<"create" | "edit">("create");
  const [itemActual, setItemActual] = useState<ManagementItem>(createEmptyItem());

  useEffect(() => {
    let mounted = true;
    if (!api) return;

    setLoading(true);
    setErro(null);
    api.list()
      .then((data) => {
        if (!mounted) return;
        if (Array.isArray(data) && data.length > 0) {
          setItens(data);
        }
      })
      .catch(() => {
        if (!mounted) return;
        setErro("Nao foi possivel carregar dados da API. A usar dados locais.");
      })
      .finally(() => {
        if (mounted) setLoading(false);
      });

    return () => {
      mounted = false;
    };
  }, [api]);

  const itensFiltrados = useMemo(() => {
    const normalized = query.trim().toLowerCase();
    if (!normalized) return itens;
    return itens.filter((item) => {
      const haystack = `${item.nome} ${item.estado} ${item.detalhe}`.toLowerCase();
      return haystack.includes(normalized);
    });
  }, [itens, query]);

  function resetForm() {
    setModo("create");
    setItemActual(createEmptyItem());
  }

  async function submitForm(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();

    if (!itemActual.nome.trim()) {
      return;
    }

    if (modo === "create") {
      const payload = {
        nome: itemActual.nome,
        estado: itemActual.estado,
        detalhe: itemActual.detalhe,
      };
      const novoLocal: ManagementItem = {
        ...itemActual,
        id: crypto.randomUUID(),
        actualizadoEm: new Date().toISOString(),
      };
      if (api) {
        try {
          const criado = await api.create(payload);
          setItens((prev) => [criado, ...prev]);
        } catch {
          setItens((prev) => [novoLocal, ...prev]);
          setErro("Falha ao criar via API. Registo criado localmente.");
        }
      } else {
        setItens((prev) => [novoLocal, ...prev]);
      }
    } else {
      const payload = {
        nome: itemActual.nome,
        estado: itemActual.estado,
        detalhe: itemActual.detalhe,
      };
      if (api) {
        try {
          const actualizado = await api.update(itemActual.id, payload);
          setItens((prev) => prev.map((item) => (item.id === itemActual.id ? actualizado : item)));
        } catch {
          setItens((prev) => prev.map((item) => (
            item.id === itemActual.id ? { ...itemActual, actualizadoEm: new Date().toISOString() } : item
          )));
          setErro("Falha ao actualizar via API. Alteracao guardada localmente.");
        }
      } else {
        setItens((prev) => prev.map((item) => (
          item.id === itemActual.id ? { ...itemActual, actualizadoEm: new Date().toISOString() } : item
        )));
      }
    }

    resetForm();
  }

  function editarItem(item: ManagementItem) {
    setModo("edit");
    setItemActual(item);
  }

  return (
    <AppShell>
      <main className="space-y-6 p-4 md:p-8">
        <div>
          <h1 className="font-headline-lg text-headline-lg text-on-surface">{titulo}</h1>
          <p className="mt-1 text-body-md text-on-surface-variant">{descricao}</p>
        </div>
        {erro && <p className="rounded-xl border border-outline-variant bg-white px-4 py-3 text-sm text-on-surface-variant">{erro}</p>}

        <section className="grid grid-cols-1 gap-5 lg:grid-cols-3">
          <Card className="lg:col-span-2">
            <CardHeader>
              <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
                <CardTitle>Lista de {entidadePlural}</CardTitle>
                <Input
                  placeholder={`Pesquisar ${entidadePlural.toLowerCase()}...`}
                  value={query}
                  onChange={(event) => setQuery(event.target.value)}
                  className="sm:w-72"
                />
              </div>
            </CardHeader>
            <CardContent>
              {loading ? (
                <p className="text-sm text-on-surface-variant">A carregar...</p>
              ) : itensFiltrados.length === 0 ? (
                <p className="text-sm text-on-surface-variant">Sem registos para mostrar.</p>
              ) : (
                <div className="overflow-x-auto">
                  <table className="w-full text-left text-sm">
                    <thead>
                      <tr className="border-b border-outline-variant text-xs font-bold uppercase text-on-surface-variant">
                        <th className="pb-3 pr-4">Nome</th>
                        <th className="pb-3 pr-4">Estado</th>
                        <th className="pb-3 pr-4">Detalhe</th>
                        <th className="pb-3 pr-4">Actualizado</th>
                        <th className="pb-3">Accoes</th>
                      </tr>
                    </thead>
                    <tbody>
                      {itensFiltrados.map((item) => (
                        <tr key={item.id} className="border-b border-outline-variant/50 last:border-0">
                          <td className="py-3 pr-4 font-bold text-on-surface">{item.nome}</td>
                          <td className="py-3 pr-4 text-on-surface-variant">{item.estado}</td>
                          <td className="py-3 pr-4 text-on-surface-variant">{item.detalhe}</td>
                          <td className="py-3 pr-4 text-on-surface-variant">
                            {new Intl.DateTimeFormat("pt-PT", { day: "2-digit", month: "2-digit", year: "numeric" }).format(new Date(item.actualizadoEm))}
                          </td>
                          <td className="py-3">
                            <Button variant="outline" size="sm" onClick={() => editarItem(item)}>
                              Editar
                            </Button>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle>{modo === "create" ? `Criar ${entidadeSingular}` : `Editar ${entidadeSingular}`}</CardTitle>
            </CardHeader>
            <CardContent>
              <form className="space-y-3" onSubmit={submitForm}>
                <Input
                  placeholder="Nome"
                  value={itemActual.nome}
                  onChange={(event) => setItemActual((prev) => ({ ...prev, nome: event.target.value }))}
                  required
                />
                <Input
                  placeholder="Estado"
                  value={itemActual.estado}
                  onChange={(event) => setItemActual((prev) => ({ ...prev, estado: event.target.value }))}
                />
                <Input
                  placeholder="Detalhe"
                  value={itemActual.detalhe}
                  onChange={(event) => setItemActual((prev) => ({ ...prev, detalhe: event.target.value }))}
                />
                <div className="flex gap-2">
                  <Button type="submit" className="flex-1">
                    {modo === "create" ? "Criar" : "Guardar"}
                  </Button>
                  {modo === "edit" && (
                    <Button type="button" variant="outline" onClick={resetForm}>
                      Cancelar
                    </Button>
                  )}
                </div>
              </form>
            </CardContent>
          </Card>
        </section>
      </main>
    </AppShell>
  );
}

export type { ManagementItem };
