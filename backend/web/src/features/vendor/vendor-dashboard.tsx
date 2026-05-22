import { MetricCard, StateCard } from "../../components/backoffice-shell";
import { AsyncStateView } from "../../components/async-state-view";
import type { AsyncState } from "../../services/api";
import { formatMzn } from "../../services/format";
import type { Vendor } from "../../services/types";

type Props = {
  state: AsyncState<Vendor[]>;
};

export function VendorDashboard({ state }: Props) {
  return (
    <AsyncStateView state={state}>
      {(vendors) => (
        <>
          <MetricCard label="Fornecedores ativos" value={String(vendors.length)} />
          <MetricCard label="Vendas hoje" value={formatMzn(vendors.length * 5200)} />
          <MetricCard label="Pedidos rejeitados" value="1" />
          <MetricCard label="Tempo medio de entrega" value="42 min" />
          <StateCard title="Perfil da loja" detail="Dados de registo, verificacao e disponibilidade por filial." />
          <StateCard title="Catalogo e SKUs" detail="Produtos, preco e regras de farmacia e combustivel por item." />
          <StateCard title="Inventario" detail="Ajuste de stock em tempo real com bloqueio de quantidades negativas." />
          <StateCard title="Fulfillment" detail="Aceitar, rejeitar com motivo, preparar e marcar pronto para recolha." />
          <article className="state-card">
            <h3>Fornecedores ativos</h3>
            <ul>
              {vendors.slice(0, 6).map((vendor) => (
                <li key={vendor.id}>
                  {vendor.name} - {vendor.available ? "Disponivel" : "Indisponivel"}
                </li>
              ))}
            </ul>
          </article>
        </>
      )}
    </AsyncStateView>
  );
}
