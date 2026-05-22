import test from "node:test";
import assert from "node:assert/strict";
import { renderToStaticMarkup } from "react-dom/server";
import { SupportDashboard } from "./support-dashboard";

const state = {
  kind: "success" as const,
  data: [
    {
      id: "t-1",
      orderId: "o-1",
      subject: "Atraso",
      description: "Pedido atrasado",
      status: "OPEN",
      classification: "DELIVERY",
      internalNote: "contactar courier",
      assigneeUserId: "support-1",
      createdAt: "2026-05-01T10:00:00Z",
    },
  ],
};

test("internal notes hidden for customer-style view", () => {
  const html = renderToStaticMarkup(<SupportDashboard state={state} viewer="customer" />);
  assert.doesNotMatch(html, /Nota interna/);
});

test("internal notes visible for support-style view", () => {
  const html = renderToStaticMarkup(<SupportDashboard state={state} viewer="support" />);
  assert.match(html, /Nota interna: contactar courier/);
});
