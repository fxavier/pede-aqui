import { ManagementCrudPage, type ManagementItem } from "@/features/management/management-crud-page";

const itensIniciais: ManagementItem[] = [
  { id: "mkt-1", nome: "Campanha Boas-Vindas", estado: "Activa", detalhe: "Cupao de 10% para novos clientes", actualizadoEm: "2026-05-18T14:00:00Z" },
  { id: "mkt-2", nome: "Push Fim de Semana", estado: "Agendada", detalhe: "Segmento: Clientes recorrentes", actualizadoEm: "2026-05-20T09:10:00Z" },
  { id: "mkt-3", nome: "Promo Combos Familia", estado: "Concluida", detalhe: "ROI medio: 2.4x", actualizadoEm: "2026-05-17T17:50:00Z" },
];

export default function MarketingPage() {
  return (
    <ManagementCrudPage
      titulo="Marketing"
      descricao="Listar, criar e editar campanhas e accoes de marketing."
      entidadeSingular="Campanha"
      entidadePlural="Campanhas"
      itensIniciais={itensIniciais}
      apiKey="marketing"
    />
  );
}
