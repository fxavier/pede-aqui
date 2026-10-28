# ADR-0001 — Modelo de tenancy

| Campo | Valor |
|---|---|
| Status | **Accepted** |
| Date | 2026-10-28 (proposto) |
| Accepted | 2026-10-28 |
| Decisores | Xavier Francisco (owner técnico) |
| Achados relacionados | AS-03, SEC-06, SEC-09, D1 ([AS-IS-ARCHITECTURE.md §5, §16](../assessment/AS-IS-ARCHITECTURE.md)) |
| Bloqueia | INC-11 (resolução de tenant e perfil do cliente) e toda a refatoração estrutural da Fase 2 |

## Context

O backend é multi-tenant por coluna (`tenant_id` em quase todas as tabelas de negócio). A semântica de "tenant" nunca foi decidida explicitamente e o código atual (pós-Fase 1, `origin/main` `a915731`) suporta duas leituras incompatíveis:

| Facto verificado no código atual | Evidência |
|---|---|
| Cada registo self-service de comerciante cria um **tenant novo** + utilizador Keycloak com atributo `tenant_id` + perfil `VENDOR_ADMIN` + **um** `Vendor` | `backend/src/main/java/com/delivery/auth/service/MerchantRegistrationService.java` (linhas ~55–90: `UUID tenantId = UUID.randomUUID()`, `new Tenant(...)`, `new Vendor(..., tenantId, ...)`) |
| O provisioning M2M (SaaS) também cria tenants, de forma idempotente | `auth/service/SubscriptionProvisioningService.java`, `V034__provisioning_idempotency.sql` |
| `fee_policies` é única por tenant; zonas, estafetas, carrinhos, encomendas, pagamentos e entregas são *tenant-scoped* | `V010__admin_ops_audit.sql`, `V003__customer_order_flow.sql`, `V007` |
| **Não existe tabela `merchant_users`** nem qualquer associação utilizador→vendor | `grep merchant_users backend/src/main/resources/db/migration` vazio (V001–V038) |
| **O realm não tem mapper `vendor_id`**: os clientes OIDC só emitem `tenant_id` (`oidc-usermodel-attribute-mapper`) | `keycloak/delivery-realm.json` (mappers `tenant_id` nos clientes `delivery-app`, `pede-aqui-web`, `pede-aqui-mobile`, `pede-aqui-courier-mobile`; nenhum `vendor_id`) |
| Os *guards* de propriedade introduzidos na Fase 1 leem um claim `vendor_id` **opcional** e, na sua ausência, recorrem a "**um único vendor por tenant**"; com 0 ou ≥2 vendors falham fechados (403 `vendor_scope_unresolved`) | `sales/service/SalesAccessGuard.java` (`resolveOwnVendorId`, `requireOwnVendorId`), `catalog/service/CatalogAccessGuard.java` (`ensureCanManageVendor`), `report/service/SalesReportService.java` (`resolveOwnVendorId`) |
| `VENDOR_STAFF` não tem associação a vendor; é resolvido como `VENDOR_ADMIN` | Javadoc de `SalesAccessGuard.isMerchantScoped()` |
| `TenantContext.currentTenantId()` usa o claim `tenant_id` e, se faltar, aceita **qualquer** `X-Tenant-Id` sem validar pertença nem papel (**SEC-06, ainda aberto**) | `common/security/TenantContext.java` (`jwtTenantId.or(this::currentTenantIdHeader)`) |
| Os utilizadores seed `admin`, `ops`, `finance`, `support` do realm não têm atributo `tenant_id`; só `admin` é *platform admin* legítimo; `ops`/`finance`/`support` escolhem o tenant pelo header | `keycloak/delivery-realm.json` (`users`) |
| Clientes auto-registados não têm `tenant_id` nem `AppUserProfile`; o carrinho/checkout exigem ambos | `auth/service/CustomerRegistrationService.java` ("no tenant"), `cart/service/CartService.java` (`getCurrentCustomerId` → `User profile was not found`; `tenantId()` → `tenant_required`), `order/service/OrderService.java` (`getCurrentCustomerId`) |
| A pesquisa pública anónima atravessa todos os tenants | `geo/service/SearchService.java` ("Anonymous requests search across all tenants") |
| A web cliente não envia `X-Tenant-Id`; o backoffice envia-o para impersonação | `pede-aqui-delivery/src` (sem ocorrências), `pede-aqui-backoffice/src/lib/api/client.ts` |
| `app_user_profiles` tem `UNIQUE (tenant_id, keycloak_user_id)` | `V002__tenants_and_users.sql` |

## Problem

Que entidade de negócio é um *tenant*? Enquanto isto não estiver decidido:

- o fluxo B2C não funciona ponta-a-ponta (um cliente não tem tenant nem perfil, AS-03);
- não é possível corrigir SEC-06 de forma definitiva (não há regra para o tenant do cliente, nem para ops/finance/support);
- os *guards* de propriedade dependem de uma heurística ("um vendor por tenant") que é verdade **apenas** porque o registo de comerciante cria um tenant por comerciante — e deixa de ser verdade em qualquer modelo com várias lojas por tenant;
- os estafetas e as zonas, que são *tenant-scoped*, não podem servir lojas de comerciantes diferentes.

## Decision Drivers

1. O PRD descreve um marketplace B2C: o cliente compra em várias lojas e os estafetas da plataforma servem todas as lojas da zona.
2. Preservar o investimento existente em `tenant_id` (45 tabelas), `fee_policies`, zonas, provisioning M2M idempotente e impersonação no backoffice.
3. Segurança: o tenant de um pedido nunca pode ser escolhido livremente pelo cliente (fechar SEC-06).
4. Migração incremental e reversível, sem *big bang* (Regras comuns: expand/contract, sem apagar dados sem aprovação).
5. Manter aberta a opção comercial de vender a plataforma a outros operadores (white-label) sem a exigir agora.

## Considered Alternatives

### (a) Tenant = operador do marketplace (recomendada)

O Pede-Aqui é um tenant (p.ex. "Pede-Aqui Maputo"). Vendors, estafetas, zonas, taxas e clientes pertencem ao tenant do operador. O comerciante passa a ser um `vendor` dentro desse tenant, com utilizadores associados por `merchant_users`. Novos tenants só via provisioning M2M (outro operador white-label).

- **+** Coerente com `fee_policies` único por tenant, `couriers.tenant_id`, zonas e provisioning SaaS.
- **+** Carrinho, encomendas e dispatch funcionam sem atravessar tenants.
- **+** Mantém as queries `findByTenantIdAnd…` e os índices existentes.
- **−** Exige mudar o registo de comerciante e substituir a heurística "um vendor por tenant" por uma associação explícita.
- **−** Os dados de dev criados por registo de comerciante (um tenant por loja) têm de ser consolidados.

### (b) Tenant = comerciante

Mantém o registo atual. Clientes, estafetas e zonas passam a ser globais (fora do tenant).

- **+** Nenhuma alteração ao registo de comerciante; a heurística dos *guards* continua válida.
- **−** Carrinho, encomenda, pagamento, entrega, estafeta, zona e taxas teriam de deixar de ser *tenant-scoped* ou atravessar tenants em cada pedido: reescrita do modelo de dados central.
- **−** `fee_policies` por tenant deixa de fazer sentido (taxas da plataforma ≠ taxas do comerciante).
- **−** O provisioning M2M de "tenants" perde significado.

### (c) Sem multi-tenancy

Remover `tenant_id` e tratar o sistema como um único marketplace.

- **+** Modelo mais simples; elimina SEC-06 por construção.
- **−** Migração destrutiva em ~45 tabelas, índices e todas as queries; perde o provisioning SaaS e a impersonação.
- **−** Custo alto agora para resolver um problema que (a) resolve com menos alterações; irreversível na prática.

## Decision

**Proposta: opção (a) — tenant = operador do marketplace.** Concretamente:

1. **Resolução do tenant**
   - **Utilizadores internos e de comerciante** (VENDOR_ADMIN, VENDOR_STAFF, OPS, FINANCE, SUPPORT, COURIER): tenant **sempre** do claim `tenant_id` do JWT. Os seed `ops`/`finance`/`support` passam a ter `tenant_id`.
   - **Clientes (CUSTOMER)**: o tenant é determinado pelo **client OIDC** (`azp`) que emitiu o token — cada client de cliente (`pede-aqui-web`, `pede-aqui-mobile`) é configurado no backend com o tenant do operador (mapa `client_id → tenant_id`), ou, para white-label futuro, por **domínio/host** mapeado em configuração. Alternativa equivalente aceitável: um *hardcoded claim mapper* `tenant_id` por client no realm. **Nunca** por header.
   - **Pedidos anónimos** (pesquisa, catálogo público): mesma regra por domínio/client configurado, com *default* para o tenant do operador; deixa de haver pesquisa transversal a todos os tenants.
   - **`X-Tenant-Id`**: honrado **apenas** quando `TenantContext.isPlatformAdmin()` (ADMIN sem `tenant_id`), e cada uso é auditado em `audit_logs`. Para qualquer outro principal o header é ignorado (ou rejeitado com 403 se divergir do tenant resolvido). Utilizador sem tenant resolvível → 403 `tenant_required`.
2. **Perfil do cliente**: `AppUserProfile` provisionado de forma idempotente no tenant resolvido — no registo (`/customers/register`) e, como rede de segurança, no primeiro pedido autenticado — apoiado na `UNIQUE (tenant_id, keycloak_user_id)` existente (concorrência resolvida por *insert-or-select*).
3. **Registo de comerciante**: deixa de criar tenant. `POST /register` cria, no tenant do operador, um `Vendor` em estado pendente de verificação (fecha também o caminho para SEC-09), o utilizador Keycloak com `tenant_id` do operador e uma linha em **`merchant_users`** (`tenant_id`, `vendor_id`, `app_user_profile_id`, `role` ∈ {OWNER, STAFF}, `UNIQUE (tenant_id, app_user_profile_id, vendor_id)`).
4. **Escopo de comerciante**: os *guards* (`SalesAccessGuard`, `CatalogAccessGuard`, `SalesReportService`) passam a resolver o vendor do chamador por `merchant_users` (fonte de verdade), com o claim `vendor_id` como otimização opcional emitida por um mapper do realm. A heurística "único vendor do tenant" é **removida** no fim da migração (é incorreta em (a)). Um utilizador associado a vários vendors escolhe o vendor ativo explicitamente (fora de âmbito deste ADR; 403 até lá).
5. **Novos tenants** apenas via provisioning M2M (`subscriptions-provisioner`) ou pela consola de plataforma.

## Rationale

(a) é a única opção que torna o fluxo B2C e o dispatch partilhado possíveis **sem** reescrever o modelo de dados central: carrinho, encomenda, pagamento, entrega, estafeta, zona e taxas já são *tenant-scoped* e continuam a sê-lo. O que muda é a fronteira entre comerciantes, que passa do nível tenant para o nível vendor — onde já existe `vendor_id` em `products`, `skus`, `inventory_items`, `orders`, `promotion`, `delivery_fee_rules`. Os *guards* da Fase 1 já isolam por vendor; só lhes falta uma fonte de verdade que não seja a heurística. Resolver o tenant do cliente pelo client OIDC/domínio elimina a superfície de SEC-06 sem exigir que o cliente "escolha" um tenant.

## Consequences

### Positive
- Fluxo B2C desbloqueado (AS-03): o cliente tem tenant e perfil sem header.
- SEC-06 fechado por construção; impersonação limitada e auditada.
- Estafetas, zonas e taxas partilhados por todas as lojas do operador, como o PRD exige.
- Isolamento entre comerciantes explícito (`merchant_users`) em vez de implícito, suportando `VENDOR_STAFF` e comerciantes com várias lojas.
- Provisioning M2M e white-label mantêm-se como evolução natural.

### Negative
- Alteração de contrato de `POST /register` (deixa de devolver/criar tenant) e do backoffice de registo.
- Nova tabela e novas queries no caminho quente dos *guards* (uma leitura extra por pedido de comerciante, mitigável com o claim `vendor_id`).
- Um bug nos *guards* passa a ter impacto entre comerciantes do **mesmo** tenant (antes a fronteira de tenant protegia-os). A cobertura de testes de propriedade torna-se obrigatória.
- Dados de dev com "um tenant por comerciante" precisam de consolidação.

## Risks and Mitigations

| Risco | Mitigação |
|---|---|
| Fuga de dados entre comerciantes do mesmo tenant | Todos os acessos de comerciante passam pelos *guards*; testes de propriedade por endpoint (padrão de INC-03/04/05); remover a heurística só depois de `merchant_users` estar preenchida |
| Cliente com token de um client sem mapeamento → 403 em massa | Mapa `client_id → tenant_id` validado no arranque (*fail-fast* se um client de cliente conhecido não tiver tenant); teste E2E registo → checkout (INC-11) |
| Platform admin a operar no tenant errado | `X-Tenant-Id` só para `isPlatformAdmin()`, auditado; UI do backoffice já mostra o tenant ativo |
| Migração de tenants de dev perde ligações (FKs ausentes em `orders.vendor_id`, `products.vendor_id`) | Script de consolidação em transação, com contagens antes/depois e *dry-run*; sem apagar tenants antigos até validação |
| Utilizadores Keycloak com `tenant_id` antigo | Script Admin REST que reescreve o atributo; tokens antigos expiram (TTL curto) |

## Migration Implications

Faseado (expand → migrate → contract), cada passo num incremento próprio:

1. **INC-11 (expand, compatível):** `TenantContext` honra `X-Tenant-Id` só para platform admin; resolução do tenant do cliente por client/domínio; provisionamento idempotente de `AppUserProfile`; seed `ops`/`finance`/`support` com `tenant_id`.
2. **Migração `V0NN__merchant_users.sql`:** criar `merchant_users` + backfill a partir de `app_user_profiles` com papel `VENDOR_ADMIN` e do vendor único de cada tenant (consulta de validação de ambiguidades no relatório: tenants com ≠1 vendor).
3. **Guards:** ler `merchant_users` primeiro, heurística como *fallback* temporário com log de aviso; mapper `vendor_id` opcional no realm.
4. **Registo de comerciante:** novo comportamento (vendor pendente no tenant do operador). Contrato documentado em `docs/api/API-MIGRATION.md`.
5. **Dev data (tenants existentes):** escolher/criar o tenant do operador; para cada tenant criado por registo de comerciante, mover vendors, produtos, SKUs, inventário, promoções, perfis e encomendas para o tenant do operador (`UPDATE … SET tenant_id = :operator WHERE tenant_id = :merchantTenant`), atualizar o atributo `tenant_id` dos utilizadores Keycloak, marcar o tenant antigo como inativo (não apagar). Em produção não há dados — o script é só para dev/staging.
6. **Contract:** remover a heurística "um vendor por tenant" e a pesquisa transversal anónima.

## Validation Criteria

- Teste E2E de API: registo de cliente → login → adicionar ao carrinho → checkout **sem** `X-Tenant-Id` (INC-11).
- Teste: CUSTOMER/OPS/FINANCE/SUPPORT/VENDOR_ADMIN com `X-Tenant-Id` de outro tenant → header ignorado ou 403; platform admin → aceite e auditado.
- Teste: dois vendors no mesmo tenant; VENDOR_ADMIN de A recebe 403/404 em encomendas, produtos, inventário, vendas e relatórios de B.
- `grep "vendors.size() == 1"` / `findVendorIdsForTenant` sem ocorrências nos *guards* após o *contract*.
- Novo registo de comerciante não cria linha em `tenants`.

## References

- [AS-IS-ARCHITECTURE.md §5, §8.2 (SEC-06), §13 (AS-03), §16 (D1)](../assessment/AS-IS-ARCHITECTURE.md)
- [FEATURE-INVENTORY.md](../assessment/FEATURE-INVENTORY.md) (registo de cliente/comerciante, pesquisa transversal, impersonação)
- [`AUDIT.md`](../../AUDIT.md) (SEC-06, SEC-09)
- `docs/refactoring/CLAUDE-CODE-PROMPTS.md` — INC-10, INC-11
- Código: `common/security/TenantContext.java`, `sales/service/SalesAccessGuard.java`, `catalog/service/CatalogAccessGuard.java`, `report/service/SalesReportService.java`, `auth/service/MerchantRegistrationService.java`, `auth/service/CustomerRegistrationService.java`, `keycloak/delivery-realm.json`
