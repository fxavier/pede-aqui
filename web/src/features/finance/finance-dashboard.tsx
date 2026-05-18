import { AsyncStateView } from "../../components/async-state-view";
import { MetricCard, StateCard } from "../../components/backoffice-shell";
import { formatMzn } from "../../services/format";
import type { AsyncState } from "../../services/api";
import type { FinanceSummary } from "../../services/types";

export function FinanceDashboard({ state }: { state: AsyncState<FinanceSummary> }) {
  return (
    <AsyncStateView state={state}>
      {(data) => (
        <>
          <MetricCard label="Transacoes confirmadas" value={formatMzn(data.confirmedPaymentsTotal)} />
          <MetricCard label="Comissoes" value={formatMzn(data.commissionTotal)} />
          <MetricCard label="Reembolsos" value={formatMzn(data.refundsTotal)} />
          <MetricCard label="COD por reconciliar" value={formatMzn(data.unreconciledCashTotal)} />
          <StateCard title="Reembolsos" detail="Aprovacao e rejeicao com trilha de auditoria para equipa financeira." />
          <StateCard title="Payouts" detail="Comissoes pendentes e liquidadas por ciclo de pagamento." />
          <StateCard title="Exportacao" detail="Exportacao basica de transacoes, comissoes, reembolsos e COD." />
        </>
      )}
    </AsyncStateView>
  );
}
