import { ManagementCrudPage, type ManagementItem } from "@/features/management/management-crud-page";

const itensIniciais: ManagementItem[] = [
  { id: "ord-1", nome: "PA-2026-00123", estado: "Pendente", detalhe: "Cliente: Ana Matola", actualizadoEm: "2026-05-20T10:10:00Z" },
  { id: "ord-2", nome: "PA-2026-00124", estado: "Em Entrega", detalhe: "Cliente: Joao Nhantumbo", actualizadoEm: "2026-05-20T11:20:00Z" },
  { id: "ord-3", nome: "PA-2026-00125", estado: "Entregue", detalhe: "Cliente: Marta Macamo", actualizadoEm: "2026-05-20T13:05:00Z" },
];

export default function OrdersPage() {
  return (
    <ManagementCrudPage
      titulo="Encomendas"
      descricao="Listar, criar e editar registos de encomendas."
      entidadeSingular="Encomenda"
      entidadePlural="Encomendas"
      itensIniciais={itensIniciais}
      apiKey="orders"
    />
  );
}
