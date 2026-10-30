# Diagramas de arquitetura — Pede Aqui

Gerados a partir do código da `main` em `5283219` (2026-10-28). Cada diagrama existe em três formatos:

- `.drawio` — fonte editável (draw.io desktop ou diagrams.net);
- `.drawio.png` — PNG com o XML embebido (abre editável no draw.io);
- `.svg` — vetorial, também com o XML embebido.

Convenções: rótulos em pt-PT; nomes de classes, endpoints e códigos de erro tal como estão no código. Elementos a tracejado cinzento estão planeados e não existem no código.

## Estrutura

| Diagrama | Ficheiro | Conteúdo |
|---|---|---|
| C4 nível 3 — Componentes do backend | [c4-03-componentes-backend](c4-03-componentes-backend.drawio.png) | Pipeline de pedidos (filtros, segurança, tenant, erros), um componente por módulo, guards e serviços partilhados, dependências externas. Ciclos entre módulos a vermelho tracejado. |
| LLD do backend (1/2) | [lld-backend-p1](lld-backend-p1.drawio.png) | Camadas controller → service → repository → entity → dto/mapper para `order`, `delivery` e `payment`, com nomes reais. |
| LLD do backend (2/2) | [lld-backend-p2](lld-backend-p2.drawio.png) | Cadeia de filtros, resolução de tenant, resolução do vendor do comerciante, ordem de locks, idempotência e contrato de erros RFC 9457. |
| Classes do domínio (1/2) | [classes-dominio-p1](classes-dominio-p1.drawio.png) | Catálogo e comerciantes. |
| Classes do domínio (2/2) | [classes-dominio-p2](classes-dominio-p2.drawio.png) | Encomenda, pagamento e entrega. Linha contínua = FK real; tracejada = coluna UUID sem FK. |

## Comportamento

| Diagrama | Ficheiro | Conteúdo |
|---|---|---|
| Casos de uso | [casos-de-uso](casos-de-uso.drawio.png) | ~40 casos de uso em 11 áreas; papéis tirados dos `@PreAuthorize` e do `SecurityConfig`. |
| Sequência — checkout | [sequencia-checkout](sequencia-checkout.drawio.png) | `POST /api/v1/checkout`: idempotência, lock do carrinho, revalidação de preço, quote com distância PostGIS, reserva de stock, PIN HMAC. |
| Sequência — entrega com PIN | [sequencia-entrega-pin](sequencia-entrega-pin.drawio.png) | `POST /deliveries/{id}/complete`: ownership, guardas de estado, `order_not_deliverable`, bloqueio 423, HMAC com fallback legado, desbloqueio pelo OPS. |
| Sequência — rejeição e reembolso | [sequencia-rejeicao-reembolso](sequencia-rejeicao-reembolso.drawio.png) | Rejeição pelo vendedor → `CANCELLED` + `SELLER_REJECTED`, retirada da entrega, libertação de stock, reembolso do restante, aprovação pelas Finanças. |
| Sequência — dispatch | [sequencia-dispatch](sequencia-dispatch.drawio.png) | Atribuição pelo OPS, exclusão de estafetas ocupados, TTL da oferta, aceitação atómica por UPDATE condicional, índices V035. |
| Estados da encomenda | [estados-encomenda](estados-encomenda.drawio.png) | `OrderStatus` com triggers e guardas, e estado financeiro do pagamento. |
| Estados da entrega | [estados-entrega](estados-entrega.drawio.png) | `DeliveryStatus` (incluindo retirada antes da recolha) e `DispatchJob`. |

## Em falta

C4 nível 1 (contexto), C4 nível 2 (containers / HLD), deploy e fluxo de dados ainda não foram gerados.

## O que os diagramas revelaram sobre o código

- **Ciclos entre módulos:** os nove ciclos C1–C9 do ADR-0002 continuam presentes, e há três novos: `cart↔geo` (pricing), `common↔tenant` e `common↔auth` (filtro de tenant). O Spring Modulith ainda não está no `pom.xml`.
- **Estados nunca escritos:** a encomenda nunca passa por `PICKED_UP` nem por `DELIVERING` — fica em `ASSIGNED_TO_COURIER` até ao `/complete`. Também nunca são escritos `PENDING`, `DISPATCH_PENDING`, `REFUND_PENDING` e `REJECTED_BY_VENDOR` (encomenda), `DeliveryStatus.REJECTED`, `DispatchJobStatus.COMPLETED` e os estados de pagamento `INITIATED`, `FAILED`, `CANCELLED`, `REFUND_PENDING` e `REFUNDED`.
- **Reembolsos sem verificação de estado:** `requestRefund` e `approveRefund` não verificam o estado do pagamento.
- **Um pagamento por encomenda:** é o comportamento do código, mas não há UNIQUE em `payments.order_id`.
