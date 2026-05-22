import { ManagementCrudPage, type ManagementItem } from "@/features/management/management-crud-page";

const itensIniciais: ManagementItem[] = [
  { id: "cou-1", nome: "Mateus Tavares", estado: "Activo", detalhe: "Zona: Maputo Cidade", actualizadoEm: "2026-05-20T09:40:00Z" },
  { id: "cou-2", nome: "Celina Mabote", estado: "Offline", detalhe: "Zona: Matola", actualizadoEm: "2026-05-20T11:35:00Z" },
  { id: "cou-3", nome: "Paulo Mucavele", estado: "Em rota", detalhe: "Zona: Baixa", actualizadoEm: "2026-05-20T12:15:00Z" },
];

export default function CouriersPage() {
  return (
    <ManagementCrudPage
      titulo="Estafetas"
      descricao="Listar, criar e editar perfis de estafetas."
      entidadeSingular="Estafeta"
      entidadePlural="Estafetas"
      itensIniciais={itensIniciais}
      apiKey="couriers"
    />
  );
}
