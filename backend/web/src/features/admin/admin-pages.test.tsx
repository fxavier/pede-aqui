import test from "node:test";
import assert from "node:assert/strict";
import { renderToStaticMarkup } from "react-dom/server";
import { AdminDashboard } from "./admin-dashboard";

test("admin dashboard renders metrics on success", () => {
  const html = renderToStaticMarkup(
    <AdminDashboard state={{ kind: "success", data: { activeVendors: 5, activeCouriers: 12, cancelledOrders: 2, failedDeliveries: 1 } }} />,
  );
  assert.match(html, /Fornecedores ativos/);
  assert.match(html, /Estafetas ativos/);
});

test("admin dashboard renders forbidden state", () => {
  const html = renderToStaticMarkup(<AdminDashboard state={{ kind: "forbidden" }} />);
  assert.match(html, /Sem permissao/);
});
