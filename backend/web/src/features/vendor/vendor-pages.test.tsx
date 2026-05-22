import test from "node:test";
import assert from "node:assert/strict";
import { renderToStaticMarkup } from "react-dom/server";
import { VendorDashboard } from "./vendor-dashboard";

test("renders loading state", () => {
  const html = renderToStaticMarkup(<VendorDashboard state={{ kind: "loading" }} />);
  assert.match(html, /A carregar/);
});

test("renders empty state", () => {
  const html = renderToStaticMarkup(<VendorDashboard state={{ kind: "empty" }} />);
  assert.match(html, /Sem dados/);
});

test("renders error state", () => {
  const html = renderToStaticMarkup(<VendorDashboard state={{ kind: "error", message: "Falha" }} />);
  assert.match(html, /Erro: Falha/);
});

test("renders forbidden state", () => {
  const html = renderToStaticMarkup(<VendorDashboard state={{ kind: "forbidden" }} />);
  assert.match(html, /Sem permissao/);
});

test("renders accepted-style vendor data", () => {
  const html = renderToStaticMarkup(
    <VendorDashboard
      state={{
        kind: "success",
        data: [
          {
            id: "1",
            name: "Loja Azul",
            categoryId: "cat-1",
            status: "ACTIVE",
            verificationStatus: "APPROVED",
            rating: 4.6,
            estimatedDeliveryMinutes: 35,
            available: true
          }
        ]
      }}
    />
  );
  assert.match(html, /Loja Azul - Disponivel/);
});
