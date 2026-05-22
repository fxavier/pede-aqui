import test from "node:test";
import assert from "node:assert/strict";
import { renderToStaticMarkup } from "react-dom/server";
import { FinanceDashboard } from "./finance-dashboard";

test("finance dashboard renders summary and refund state", () => {
  const html = renderToStaticMarkup(
    <FinanceDashboard
      state={{
        kind: "success",
        data: {
          confirmedPaymentsTotal: 1000,
          commissionTotal: 120,
          refundsTotal: 40,
          unreconciledCashTotal: 85,
        },
      }}
    />,
  );
  assert.match(html, /Transacoes confirmadas/);
  assert.match(html, /Reembolsos/);
  assert.match(html, /COD por reconciliar/);
});

test("finance dashboard renders forbidden state", () => {
  const html = renderToStaticMarkup(<FinanceDashboard state={{ kind: "forbidden" }} />);
  assert.match(html, /Sem permissao/);
});
