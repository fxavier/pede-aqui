import { AsyncStateView } from "../../components/async-state-view";
import { MetricCard, StateCard } from "../../components/backoffice-shell";
import type { AsyncState } from "../../services/api";
import type { SupportTicketItem } from "../../services/types";

type Props = {
  state: AsyncState<SupportTicketItem[]>;
  viewer: "customer" | "support";
};

export function SupportDashboard({ state, viewer }: Props) {
  return (
    <AsyncStateView state={state}>
      {(items) => (
        <>
          <MetricCard label="Tickets abertos" value={String(items.length)} />
          <MetricCard label="Tickets criticos" value={String(items.filter((i) => i.classification === "CRITICAL").length)} />
          <MetricCard label="Tempo medio de resolucao" value="2h 10m" />
          <MetricCard label="Resolvidos hoje" value="16" />
          <StateCard title="Privacidade" detail="Notas internas visiveis apenas para suporte e admin autorizado." />
          <StateCard title="Fluxo" detail="Ticket ligado ao pedido para contexto de pagamento, entrega e fornecedor." />
          <article className="state-card">
            <h3>Tickets recentes</h3>
            <ul>
              {items.slice(0, 6).map((item) => (
                <li key={item.id}>
                  {item.subject} - {item.classification}
                  {viewer === "support" && item.internalNote ? ` | Nota interna: ${item.internalNote}` : ""}
                </li>
              ))}
            </ul>
          </article>
        </>
      )}
    </AsyncStateView>
  );
}
