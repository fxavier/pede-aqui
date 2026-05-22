import { ManagementCrudPage, type ManagementItem } from "@/features/management/management-crud-page";

const itensIniciais: ManagementItem[] = [
  { id: "fin-1", nome: "Liquidacao Semanal #19", estado: "Concluida", detalhe: "Valor: 145.000 MT", actualizadoEm: "2026-05-19T16:00:00Z" },
  { id: "fin-2", nome: "Reembolso #PA-2026-00098", estado: "Pendente", detalhe: "Valor: 1.250 MT", actualizadoEm: "2026-05-20T08:45:00Z" },
  { id: "fin-3", nome: "Comissao Vendor Norte", estado: "Processada", detalhe: "Valor: 24.300 MT", actualizadoEm: "2026-05-20T10:30:00Z" },
];

export default function FinancePage() {
  return (
    <ManagementCrudPage
      titulo="Financas"
      descricao="Listar, criar e editar registos financeiros."
      entidadeSingular="Registo"
      entidadePlural="Registos"
      itensIniciais={itensIniciais}
      apiKey="finance"
    />
  );
}
