import { AsyncStateView } from "../../components/async-state-view";
import { MetricCard, StateCard } from "../../components/backoffice-shell";
import { formatMzn } from "../../services/format";
import type { AsyncState } from "../../services/api";
import type { DashboardAdmin } from "../../services/types";

export function AdminDashboard({ state }: { state: AsyncState<DashboardAdmin> }) {
  return (
    <AsyncStateView state={state}>
      {(data) => (
        <>
          <MetricCard label="Fornecedores ativos" value={String(data.activeVendors)} />
          <MetricCard label="Estafetas ativos" value={String(data.activeCouriers)} />
          <MetricCard label="Cancelamentos" value={String(data.cancelledOrders)} />
          <MetricCard label="Perdas operacionais" value={formatMzn(data.failedDeliveries * 450)} />
          <StateCard title="Verificacao" detail="Aprovacao de fornecedores e estafetas pendentes." />
          <StateCard title="Zonas e politicas" detail="Configuracao de taxas, comissoes, impostos e cancelamentos." />
          <StateCard title="Auditoria" detail="Acoes sensiveis de operacoes e administracao com trilha completa." />
        </>
      )}
    </AsyncStateView>
  );
}
