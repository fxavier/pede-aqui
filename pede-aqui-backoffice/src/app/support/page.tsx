import { ManagementCrudPage, type ManagementItem } from "@/features/management/management-crud-page";

const itensIniciais: ManagementItem[] = [
  { id: "sup-1", nome: "Ticket #8421", estado: "Aberto", detalhe: "Atraso de entrega - Bairro Central", actualizadoEm: "2026-05-20T07:55:00Z" },
  { id: "sup-2", nome: "Ticket #8422", estado: "Em analise", detalhe: "Falha de pagamento M-Pesa", actualizadoEm: "2026-05-20T10:22:00Z" },
  { id: "sup-3", nome: "Ticket #8423", estado: "Resolvido", detalhe: "Reembolso confirmado ao cliente", actualizadoEm: "2026-05-19T15:18:00Z" },
];

export default function SupportPage() {
  return (
    <ManagementCrudPage
      titulo="Apoio"
      descricao="Listar, criar e editar tickets de apoio."
      entidadeSingular="Ticket"
      entidadePlural="Tickets"
      itensIniciais={itensIniciais}
      apiKey="support"
    />
  );
}
