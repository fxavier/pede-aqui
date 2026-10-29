# Contrato de erros e correlação da API

| Campo | Valor |
|---|---|
| Incremento | INC-14 (AS-15, AS-17) |
| Estado | Em vigor no backend; janela de compatibilidade aberta (ver §5) |
| Código | `common/exception/ApiProblems.java`, `common/exception/GlobalExceptionHandler.java`, `common/security/ProblemDetailSecurityHandlers.java`, `common/security/TenantRequestFilter.java`, `common/config/CorrelationIdFilter.java` |

## 1. Resumo

Todas as respostas de erro da API (`4xx`/`5xx`) passam a ser um **documento de problema RFC 9457**
(`Content-Type: application/problem+json`), seja qual for a origem do erro:

- exceções da aplicação (`BusinessException`, `NotFoundException` e subclasses);
- exceções-padrão do Spring MVC (rota inexistente, método não suportado, `Content-Type` não suportado,
  JSON malformado, parâmetro em falta ou com tipo inválido, validação Bean Validation);
- `401`/`403` decididos pela cadeia de segurança (token ausente/inválido, regras por URL);
- `400`/`404` do `TenantRequestFilter` (INC-11) quando um administrador de plataforma envia um `X-Tenant-Id`
  malformado ou de um tenant inexistente — o filtro corre depois da cadeia de segurança e escreve o corpo
  diretamente via `ApiProblems.create(...)`;
- `429` do limitador de pedidos (LAC-11, `common/ratelimit`), com header `Retry-After`.

Os campos do formato antigo (`code`, `message`, `correlationId`, `timestamp`, `fieldErrors`, `details`)
continuam presentes **em paralelo**, pelo que os clientes atuais não precisam de alterações.

## 2. Forma do corpo

```json
{
  "type": "about:blank",
  "title": "Conflict",
  "status": 409,
  "detail": "Prices changed since the items were added to the cart; review the cart and confirm again",
  "instance": "/api/v1/checkout",

  "code": "price_changed",
  "details": { "items": [ { "skuId": "…", "currentUnitPrice": 120.00 } ] },
  "correlationId": "3f2c1a8e-1b2c-4d5e-8f90-0123456789ab",
  "traceId": "3f2c1a8e-1b2c-4d5e-8f90-0123456789ab",

  "message": "Prices changed since the items were added to the cart; review the cart and confirm again",
  "timestamp": "2026-10-28T17:30:16.629Z",
  "fieldErrors": []
}
```

| Membro | Origem | Sempre presente | Significado |
|---|---|---|---|
| `type` | RFC 9457 | sim | Por agora sempre `about:blank`; a semântica está em `code` (ver §6). |
| `title` | RFC 9457 | sim | Frase-padrão do estado HTTP (`Not Found`, `Conflict`, …). Não traduzida. |
| `status` | RFC 9457 | sim | Estado HTTP numérico, igual ao da resposta. |
| `detail` | RFC 9457 | sim | Mensagem legível (hoje em inglês, como antes em `message`), segura para mostrar. Nunca contém SQL, nomes de constraints, stack traces nem a mensagem de exceções inesperadas. |
| `instance` | RFC 9457 | sim | Caminho do pedido (**sem** query string, que pode conter dados pessoais). |
| `code` | extensão | sim | Código estável, `snake_case` minúsculo. **É o campo em que os clientes devem decidir.** |
| `details` | extensão | não | Contexto estruturado opcional (ex.: `price_changed` → itens e preços atuais). Omitido quando não há. |
| `correlationId` | extensão | sim | Igual ao header `X-Correlation-Id` da resposta e ao campo `correlationId` dos logs. |
| `traceId` | extensão | sim | Id do trace W3C (32 hex) do pedido, posto no MDC pelo Micrometer Tracing/OTel (LAC-50) — o mesmo id que aparece no log e no Jaeger/X-Ray quando o span é exportado (amostrado). Só sem tracer ativo (ex.: testes de slice MVC) volta a ser igual ao `correlationId`. |
| `message` | legado | sim | Cópia de `detail`. **Obsoleto** — usar `detail`. |
| `timestamp` | legado | sim | Instante ISO-8601 (UTC). **Obsoleto.** |
| `fieldErrors` | legado | sim | Lista `[{ "field", "message" }]`; vazia exceto em `validation_failed`. Mantém-se até existir substituto acordado. |

## 3. Códigos

### 3.1 Genéricos (derivados do estado HTTP)

| Estado | `code` | Quando |
|---|---|---|
| 400 | `validation_failed` | Bean Validation no corpo (`@Valid`) ou nos parâmetros do método; `fieldErrors` preenchido no primeiro caso. |
| 400 | `malformed_request` | Corpo ilegível (JSON malformado, tipo errado num campo). |
| 400 | `missing_parameter` | Parâmetro, header ou parte multipart obrigatório em falta. |
| 400 | `invalid_parameter` | Parâmetro de caminho/query com tipo inválido (ex.: UUID inválido). |
| 400 | `bad_request` | Outros 400 do framework (inclui pedido bearer malformado). |
| 400 | `invalid_tenant_header` | Administrador de plataforma com `X-Tenant-Id` que não é um UUID (`TenantRequestFilter`, INC-11). |
| 401 | `unauthorized` | Sem token ou token inválido/expirado. Header `WWW-Authenticate` inalterado. |
| 403 | `forbidden` | Papel insuficiente (regra por URL ou `@PreAuthorize`). |
| 403 | `tenant_required` | Pedido autenticado sem tenant resolúvel (ver "Tenant model" em `CLAUDE.md`). |
| 404 | `not_found` | Recurso inexistente/fora do âmbito **e** rota inexistente (`NoResourceFoundException`; inclui `/api-docs`, Swagger e `/actuator/health` na porta pública em `prod`). Antes devolvia **500**. |
| 404 | `tenant_not_found` | Administrador de plataforma com `X-Tenant-Id` de um tenant que não existe (`TenantRequestFilter`, INC-11). |
| 405 | `method_not_allowed` | Método HTTP não suportado; header `Allow` presente. Antes 500. |
| 406 | `not_acceptable` | `Accept` impossível de satisfazer. |
| 409 | `conflict` | Violação de unicidade/integridade (sem nomes de constraint). |
| 409 | `concurrent_modification` | Conflito de `@Version` (recarregar e repetir). |
| 413 | `payload_too_large` | Upload acima do limite. |
| 415 | `unsupported_media_type` | `Content-Type` não suportado. Antes 500. |
| 429 | `rate_limited` | Limitador distribuído (LAC-11): header `Retry-After` (segundos) e `details.retryAfterSeconds`. Também usado pelos limites de provisionamento. |
| 500 | `internal_error` | Qualquer exceção não mapeada; `detail` = `Unexpected error`. |

### 3.2 De negócio

Os códigos de `BusinessException` mantêm-se exatamente iguais (ex.: `not_found`, `price_changed`,
`idempotency_key_reused`, `delivery_code_locked`, `vendor_scope_unresolved`, `tenant_slug_exists`,
`email_exists`). Este incremento **não** renomeia nenhum código.

LAC-09 acrescenta `password_policy_violation` (400 — o Keycloak recusou a palavra-passe pela política do realm
em `POST /register` / `POST /customers/register`; antes era 500 `keycloak_error`).

W1-A acrescenta (detalhe em `API-MIGRATION.md`, secção W1-A):
- LAC-12 (uploads): `invalid_content_length` (400 — `contentLength` ausente ou ≤ 0 no presign),
  `upload_too_large` (400 — acima do limite no presign ou no objeto guardado), `upload_not_found` (400 — o
  `storageKey` ligado não existe no storage), `storage_unavailable` (503 — storage inacessível ao validar o
  objeto); `invalid_content_type` e `invalid_storage_key` (400) passam a ser emitidos também ao ligar um
  `storageKey` (tipo/namespace do objeto guardado).
- LAC-13 (`POST /admin/users`): `roles_required` (400), `role_assignment_forbidden` (403),
  `keycloak_user_not_found` (404 — utilizador/role inexistente no Keycloak); outras falhas do Keycloak
  continuam 500 `keycloak_error`.

W2-B acrescenta (detalhe em `API-MIGRATION.md`, secção W2-B): `invalid_cursor` (400 — parâmetro `cursor`
malformado nos endpoints keyset `/admin/audit/cursor`, `/notifications/cursor`, `/orders/cursor`).

W2-D acrescenta (detalhe em `API-MIGRATION.md`, secção W2-D):
- LAC-27 (horários/encerramentos): `store_closed` (422 — loja fechada em `POST /customers/{id}/cart/items` ou
  em `POST /checkout`; `details` = `{ vendorId, nextOpeningAt }`, com `nextOpeningAt` ISO-8601 ou `null` quando
  não há reabertura conhecida nos próximos 8 dias), `invalid_closure_period` (400 — encerramento com
  `endsAt` ≤ `startsAt` ou já terminado).
- LAC-64 (farmácia): `pharmacy_not_available` (422 — produto com receita ou da vertical `pharmacy` enquanto
  `app.verticals.pharmacy.enabled=false`), `prescription_required` (422 — produto com receita com a vertical
  ativa; ainda não há fluxo de receita).
- Carrinho: `sku_vendor_mismatch` (422 — o SKU não pertence ao `vendorId` do pedido).
- A ordem de avaliação em `addItem` é: `invalid_quantity` → `single_vendor_cart` → `sku_inactive` →
  `product_not_available` → `sku_vendor_mismatch` → `pharmacy_not_available`/`prescription_required` →
  `store_closed` → `insufficient_stock`. No checkout: `invalid_cart` → `store_closed` → farmácia →
  `sku_inactive`/`product_not_available`/`price_changed`.

ADR-0001 (passos 3–4) acrescenta/clarifica: `vendor_access_denied` (403 — comerciante a atuar sobre
um vendor que não é o seu, ou claim `vendor_id` fora das suas associações em `merchant_users`),
`vendor_scope_unresolved` (403 — passa a ser a resposta de **todos** os caminhos de comerciante sem vendor
resolúvel, incluindo `GET /sales/orders`), `operator_tenant_missing` (500 — `POST /register` sem o tenant
do operador V045; falha de configuração). `tenant_slug_exists` deixa de ser emitido por `POST /register`
(só pelo provisioning M2M).

W3-D acrescenta (detalhe em `API-MIGRATION.md`, secção W3-D): `sku_code_conflict` (409 — `POST /catalog/skus`
com um `skuCode` já usado pelo **mesmo** vendor; desde V056 o código é único por (tenant, vendor, sku_code)).

W4-A (máquina de estados da encomenda, LAC-25; detalhe em `API-MIGRATION.md`, secção W4-A) muda o estado HTTP
de um código existente: `invalid_order_transition` passa a **409** em todos os caminhos (antes **400** nas ações
do comerciante `accept`/`preparing`/`ready-for-pickup`/`reject`). É emitido pela `OrderStateMachine` para
qualquer transição fora da tabela — ações do comerciante, aprovação de reembolso total de uma encomenda nunca
paga — sempre **antes** de qualquer efeito (sem histórico, evento, stock, reembolso nem auditoria). Os códigos
de política mais estreitos mantêm-se: `sale_not_cancellable` (409), `transition_not_allowed` (409, lista do
*override*), `order_not_ready_for_dispatch` (409), `order_not_deliverable` (409). Uma ação do utilizador que
perde a corrida com um job de timeout na mesma encomenda recebe 409 `concurrent_modification` (`@Version`).

W5-B (estado e GPS do estafeta, LAC-42/LAC-40; detalhe em `API-MIGRATION.md`, secção W5-B) acrescenta:
- LAC-42: `invalid_courier_status` (400 — o estafeta pediu `BUSY`/`SUSPENDED` em `PUT /couriers/me/status`),
  `courier_suspended` (409 — estafeta suspenso a ficar online ou a aceitar uma oferta), `courier_has_active_delivery`
  (409 — ficar offline ou retirar o consentimento com uma entrega aceite em curso), `courier_not_suspended` (409 —
  `reinstate` de um estafeta não suspenso), `invalid_vehicle_type` (400 — `vehicleType` desconhecido em
  `POST /couriers`). `courier_not_verified` (409) passa a ser emitido também por `PUT /couriers/me/status`.
- LAC-40: `location_consent_required` (409 — lote de localizações sem consentimento na versão atual),
  `location_consent_outdated` (409 — `PUT /couriers/me/location-consent` com uma versão que não é a atual),
  `courier_off_duty` (409 — lote de localizações com o estafeta fora de serviço e sem entrega aceite),
  `location_batch_too_large` (400 — mais pontos do que `app.courier-location.max-batch-size`, só alcançável se
  o limite configurado for menor que os 50 da validação). Pontos de baixa qualidade não são erro: são
  descartados e contados em `droppedByReason`.

## 4. Correlação (`X-Correlation-Id`)

- O `CorrelationIdFilter` corre **antes** de qualquer outro filtro (incluindo a cadeia de segurança),
  por isso **todas** as respostas — sucesso ou erro, incluindo 401/403 — levam o header
  `X-Correlation-Id`.
- Se o pedido trouxer `X-Correlation-Id` com valor são (`[A-Za-z0-9._:-]`, 1–128 caracteres; cobre UUIDs e
  `traceparent` W3C), o valor é reutilizado; caso contrário é gerado um UUID. Valores com espaços, aspas,
  quebras de linha ou demasiado longos são descartados, para não injetar conteúdo no log JSON nem no header.
- O valor fica no MDC (`correlationId`) durante o pedido e aparece em cada linha de log
  (logging estruturado nativo do Spring Boot, `logging.structured.*` em `application.yml`; LAC-50). O filtro não regista nada: nenhum token, header ou dado pessoal é logado por ele.
- CORS: `X-Correlation-Id` está nos headers permitidos (o cliente pode enviá-lo) e nos **expostos** (o
  browser pode lê-lo da resposta).

**Para suporte:** pedir ao utilizador o `correlationId` mostrado no erro e procurar esse valor nos logs.

## 5. Consumidores e compatibilidade

Análise feita sobre o código atual de cada cliente:

| Cliente | Como lê o erro | Impacto |
|---|---|---|
| Backoffice (`pede-aqui-backoffice/src/lib/http-client.ts`) | `response.text()` + `JSON.parse`, lança o objeto. Os chamadores leem `code`, `message`, `fieldErrors` (`register/page.tsx`, `catalogo/catalog-view.ts`, `sales/status-maps.ts`) e `detail`/`title`/`errors` (`marketing/promotion-errors.ts`). | Nenhuma alteração necessária. `promotion-errors.ts`, que já esperava `ProblemDetail`, passa a mostrar a mensagem do backend (antes caía no texto genérico). `errors` (mapa campo→mensagem) **não** é emitido: os erros de campo continuam em `fieldErrors`. |
| Backoffice `ApiError` (`src/lib/api/types.ts`) | Tipo `{ code, message, fieldErrors?, correlationId }`. | Continua válido (subconjunto do corpo). |
| Web cliente (`pede-aqui-delivery/src/lib/api/client.ts`, Axios) | Só usa `error.response.status === 401`. Axios faz `JSON.parse` independentemente do `Content-Type`. | Nenhum. |
| App cliente Flutter (`auth_repository.dart`, Dio 5) | Em registo lê `data['message'] ?? data['error']`. Dio 5 descodifica `application/*+json` como JSON. | Nenhum: `message` continua presente. |
| App estafeta Flutter (`remote_auth_data_source.dart`) | Só usa `statusCode == 401`. | Nenhum. |

**Janela de compatibilidade:** `message`, `timestamp` e `fieldErrors` mantêm-se pelo menos até todos os
clientes lerem `detail` (e um eventual substituto de `fieldErrors`). A remoção será anunciada em
`docs/api/API-MIGRATION.md` com um incremento próprio. Novos clientes devem usar `code`, `detail`,
`details`, `correlationId` e `status`.

**Atenção a `status`:** o corpo de erro passa a ter um membro `status` numérico. Nenhum cliente atual lê
`status` do corpo de erro (todos usam o estado HTTP da resposta), mas código novo não deve confundir este
campo com os `status` de recursos (ex.: estado de encomenda).

## 6. Trabalho futuro

- `type` por código (URI estável por `code`, ex.: `https://api.pede-aqui.mz/problems/price-changed`) quando
  houver onde publicar as páginas.
- Tracing distribuído (Micrometer Tracing + OTel): **feito em LAC-50/W4-B** — `traceId` passou a ser o id do trace sem mudar o contrato.
- Remover os campos legados após a janela de compatibilidade.

## 7. Cobertura: quem escreve corpos de erro

Só três sítios produzem corpos de erro, todos via `ApiProblems`:

| Origem | Como | Teste |
|---|---|---|
| `GlobalExceptionHandler` | exceções MVC/aplicação | `ErrorContractApiTest` |
| `ProblemDetailSecurityHandlers` | 401/403 da cadeia de segurança | `ErrorContractApiTest` |
| `TenantRequestFilter` | 400/404 de impersonação de tenant (corre **depois** da cadeia de segurança) | `TenantRequestFilterProblemDetailTest`, `CustomerTenantResolutionE2ETest` |

O limitador (`common/ratelimit/RateLimitInterceptor`) lança `RateLimitedException` (subclasse de `BusinessException`), pelo que cai no handler, que acrescenta `Retry-After`. Qualquer novo filtro ou
interceptor que tenha de responder diretamente deve usar `ApiProblems.create(...)` e
`Content-Type: application/problem+json`, nunca JSON escrito à mão.

Fora deste contrato fica apenas o `/error` do Spring Boot (`BasicErrorController`), que só é atingido se um
filtro chamar `response.sendError(...)` ou se um erro escapar à cadeia de filtros; nenhum código da aplicação
faz isso hoje. Não há `ErrorController` próprio nem propriedades `server.error.*` configuradas.
