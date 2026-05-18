import { AsyncStateView } from "../../components/async-state-view";
import { MetricCard, StateCard } from "../../components/backoffice-shell";
import type { AsyncState } from "../../services/api";
import type { DispatchJob } from "../../services/types";

export function OperationsDashboard({ state }: { state: AsyncState<DispatchJob[]> }) {
  return (
    <AsyncStateView state={state}>
      {(jobs) => {
        const reassignable = jobs.filter((job) => job.status === "REASSIGNABLE").length;
        return (
          <>
            <MetricCard label="Atribuicoes ativas" value={String(jobs.length)} />
            <MetricCard label="Falhas e rejeicoes" value={String(jobs.filter((job) => job.status === "REJECTED" || job.status === "REASSIGNABLE").length)} />
            <MetricCard label="Jobs reatribuiveis" value={String(reassignable)} />
            <MetricCard label="SLA abaixo de 60 min" value="91%" />
            <StateCard title="Despacho" detail="Monitoria de pedidos e reatribuicao por zona de operacao." />
            <StateCard title="Eventos" detail="Historico de eventos de entrega para triagem de incidentes." />
            <article className="state-card">
              <h3>Fila de jobs</h3>
              <ul>
                {jobs.slice(0, 6).map((job) => (
                  <li key={job.id}>{job.id} - {job.status}</li>
                ))}
              </ul>
            </article>
          </>
        );
      }}
    </AsyncStateView>
  );
}
