import test from "node:test";
import assert from "node:assert/strict";
import { renderToStaticMarkup } from "react-dom/server";
import { OperationsDashboard } from "./operations-dashboard";

test("operations dashboard renders reassignment metrics", () => {
  const html = renderToStaticMarkup(
    <OperationsDashboard
      state={{
        kind: "success",
        data: [
          { id: "j1", orderId: "o1", deliveryId: "d1", courierId: "c1", status: "REASSIGNABLE", rejectionReason: "busy" },
          { id: "j2", orderId: "o2", deliveryId: "d2", courierId: null, status: "ASSIGNED", rejectionReason: null },
        ],
      }}
    />,
  );
  assert.match(html, /Jobs reatribuiveis/);
  assert.match(html, /j1 - REASSIGNABLE/);
});

test("operations dashboard renders forbidden state", () => {
  const html = renderToStaticMarkup(<OperationsDashboard state={{ kind: "forbidden" }} />);
  assert.match(html, /Sem permissao/);
});
