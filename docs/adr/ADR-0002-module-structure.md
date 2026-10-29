# ADR-0002 — Estrutura de módulos do backend

| Campo | Valor |
|---|---|
| Status | **Accepted** |
| Date | 2026-10-28 (proposto) |
| Accepted | 2026-10-28 |
| Decisores | Xavier Francisco (owner técnico) |
| Achados relacionados | AS-12, AS-18, D2 ([AS-IS-ARCHITECTURE.md §4.3, §16](../assessment/AS-IS-ARCHITECTURE.md)) |
| Substitui | A cláusula "Architecture Constraints" do `CLAUDE.md` (ver *Decision* §4) |
| Depende de | ADR-0001 (a quebra dos ciclos `auth↔tenant`/`auth↔vendor` segue o novo registo de comerciante) |

## Context

O backend (`backend/src/main/java/com/delivery`, Spring Boot **3.5.16** após INC-09) é um monólito com **22 pacotes de topo** (`auth, cart, catalog, common, customer, dashboard, delivery, dispatch, finance, geo, inventory, marketing, notification, order, payment, rating, report, sales, support, tenant, upload, vendor`), cada um com camadas `controller/service/repository/entity/dto/mapper`.

O `CLAUDE.md` define hoje a restrição: *"The backend deliberately avoids DDD, Clean/Hexagonal Architecture, CQRS, event sourcing, command buses, and Kafka. Keep it in the simple layered model described in `docs/architecture.md`."* Não há Spring Modulith no `pom.xml` e não há eventos de aplicação (`ApplicationEventPublisher`, `@EventListener`, `@TransactionalEventListener`: 0 ocorrências). Todos os efeitos entre domínios são chamadas síncronas, muitas vezes diretas a repositórios e entidades de outro domínio: **128 imports** de `…<outro domínio>.entity|repository` fora de `common` (maiores: `sales` 24, `order` 21, `dashboard` 17, `dispatch` 11, `marketing` 10).

### Ciclos atuais (verificados por `grep '^import com.delivery.<x>'` em `origin/main` `a915731`)

Grafo de dependências diretas (excluindo `common`):

```text
auth      -> tenant vendor
cart      -> auth catalog inventory marketing
catalog   -> inventory upload vendor
customer  -> geo
dashboard -> auth delivery dispatch finance order payment vendor
delivery  -> auth dispatch order
dispatch  -> auth dashboard delivery order
finance   -> payment
geo       -> catalog vendor
inventory -> catalog
marketing -> cart catalog order vendor
notification -> auth
order     -> auth cart catalog delivery inventory notification payment sales vendor
payment   -> auth order
sales     -> auth catalog finance notification order payment vendor
tenant    -> auth dispatch geo vendor
vendor    -> auth
rating, report, support, upload -> (nenhum)
```

**Ciclos diretos (A↔B):**

| # | Ciclo | A → B | B → A | Origem |
|---|---|---|---|---|
| C1 | `order↔payment` | `order/service/OrderService.java` → `payment.entity.Payment`, `payment.repository.PaymentRepository`, `payment.service.PaymentService`, `payment.dto.RefundRequest` (cria `Payment` no checkout; pede reembolso na rejeição) | `payment/service/PaymentService.java` → `order.entity.Order`, `OrderStatus`, `order.repository.OrderRepository` (transita a encomenda ao confirmar/reembolsar) | pré-existente |
| C2 | `order↔delivery` | `OrderService.java` → `delivery.entity.Delivery`, `delivery.repository.DeliveryRepository`, `delivery.service.DeliveryCodeHasher` (cria `Delivery` no checkout) | `delivery/service/DeliveryService.java` → `order.entity.Order`, `order.repository.OrderRepository` (marca a encomenda entregue/falhada) | pré-existente |
| C3 | `cart↔marketing` | `cart/service/CartCouponService.java` → `marketing.service.PromotionResolver` | `marketing/service/PromotionResolver.java`, `CheckoutPromotionResolver.java` → `cart.entity.Cart`, `CartItem` | pré-existente |
| C4 | `dispatch↔dashboard` | `dispatch/service/CourierService.java` → `dashboard.service.DashboardService` (`calculateCourierEarnings`) | `dashboard/service/DashboardService.java` → `dispatch.entity.Courier`, `DispatchJobStatus`, `dispatch.repository.CourierRepository`, `DispatchJobRepository` | pré-existente (o assessment listava só `dispatch→dashboard`; na prática é bidirecional) |
| C5 | `order↔sales` | `order/service/OrderService.java` → `sales.service.SalesAccessGuard` (instanciado com `new SalesAccessGuard(vendorRepository)`) | `sales/service/SalesService.java`, `SalesOrderSpecifications.java`, `sales/repository/SalesOrderSearchRepository.java` → `order.entity.*`, `order.repository.OrderRepository` | **introduzido na Fase 1 (INC-04)** |
| C6 | `catalog↔inventory` | `catalog/service/CatalogService.java` → `inventory.entity.InventoryItem`, `inventory.repository.InventoryItemRepository` (cria stock ao criar SKU) | `inventory/service/InventoryService.java` → `catalog.service.CatalogAccessGuard` | **introduzido na Fase 1 (INC-05)** — o lado `catalog→inventory` já existia |
| C7 | `delivery↔dispatch` | `delivery/service/DeliveryAccessGuard.java` → `dispatch.entity.Courier`, `dispatch.repository.CourierRepository` | `dispatch/service/DispatchService.java`, `CourierService.java` → `delivery.entity.Delivery`, `DeliveryEvent`, `DeliveryStatus`, `delivery.repository.*` | **introduzido na Fase 1 (INC-02/03)** — o lado `dispatch→delivery` já existia |
| C8 | `auth↔tenant` | `auth/service/MerchantRegistrationService.java`, `SubscriptionProvisioningService.java` → `tenant.entity.Tenant`, `tenant.repository.TenantRepository` | `tenant/controller/AdminController.java` → `auth.service.AppUserProfileService`, `auth.dto.*`; `tenant/controller/PlatformController.java` → `auth.repository.AppUserProfileRepository` | pré-existente |
| C9 | `auth↔vendor` | `MerchantRegistrationService.java` → `vendor.entity.Vendor`, `vendor.repository.VendorRepository` | `vendor/service/VendorService.java` → `auth.repository.AppUserProfileRepository` | pré-existente |

**Ciclos transitivos relevantes:** `order→cart→marketing→order` (`marketing/service/CheckoutPromotionResolver.java` → `order.service.CheckoutDiscountResolver`); `order→delivery→dispatch→order`; `dispatch→dashboard→order→delivery→dispatch`; `tenant→dispatch→auth→tenant`.

Os quatro ciclos listados no INC-10 (`order↔payment`, `order↔delivery`, `cart↔marketing`, `dispatch→dashboard`) confirmam-se; a Fase 1 acrescentou três (C5, C6, C7) ao reutilizar *guards* e repositórios de outros domínios — consequência direta de não existir uma regra estrutural verificável.

## Problem

Sem fronteiras de módulo verificáveis, cada correção local (como os *guards* da Fase 1) tende a criar novos ciclos e acessos cruzados a repositórios. Isto impede: testar módulos isoladamente, introduzir efeitos assíncronos fiáveis (expiração de pagamentos, notificações, ledger, dispatch automático — AS-12) e evoluir domínios sem regressões em cadeia. Ao mesmo tempo, a cláusula do `CLAUDE.md` proíbe qualquer ferramenta que não seja "camadas simples", incluindo verificação estrutural e eventos transacionais.

## Decision Drivers

1. Manter o modelo mental simples (controller/service/repository por domínio) que a equipa e os agentes já seguem.
2. Tornar as fronteiras entre domínios **verificáveis em CI**, para que novas violações falhem o build.
3. Ter um mecanismo **fiável** (com registo persistente e *retry*) para efeitos entre domínios após *commit*, pré-requisito de PSP/webhooks, notificações, ledger e jobs.
4. Migração incremental: o build não pode ficar vermelho durante meses enquanto os 9 ciclos e os 128 acessos cruzados são resolvidos.
5. Não introduzir DDD tático, hexagonal, CQRS, event sourcing, *command bus* nem Kafka.

## Considered Alternatives

| Alternativa | Prós | Contras |
|---|---|---|
| **A. Manter camadas sem verificação** (status quo) | Zero custo imediato | Os ciclos continuam a crescer (já +3 na Fase 1); sem eventos fiáveis |
| **B. ArchUnit apenas** (regras de pacotes/ciclos) | Leve; sem runtime | Não resolve eventos transacionais nem *event publication registry*; regras escritas à mão |
| **C. Spring Modulith só para verificação + eventos transacionais (recomendada)** | `ApplicationModules.verify()` deteta ciclos e acesso a internos; `@ApplicationModuleListener` + *event publication registry* (JPA) dá entrega *after-commit* com persistência e republicação; documentação gerada (C4/PlantUML); compatível com Boot 3.5 | Nova dependência; tabela `event_publication`; exige *named interfaces* ou reorganização para os pacotes partilhados |
| **D. Módulos Maven separados** | Fronteiras impostas pelo compilador | Refatoração grande e *big bang*; atrito com Flyway/JPA únicos; custo desproporcionado agora |
| **E. Hexagonal/DDD completo** | Isolamento forte | Contradiz o driver 1 e 5; reescrita |

## Decision

**Proposta: alternativa C.**

1. **Estrutura:** cada pacote de topo de `com.delivery` é um *application module* do Spring Modulith; **dentro** de cada módulo mantêm-se as camadas `controller/service/repository/entity/dto/mapper`. `common` é declarado módulo partilhado (`@ApplicationModule(type = Type.OPEN)` ou `sharedModules`).
2. **Uso permitido de Spring Modulith (e só este):**
   - verificação estrutural: `ApplicationModules.of(DeliveryApplication.class).verify()` num teste;
   - API pública de cada módulo explícita: pacote base do módulo e/ou subpacote `api` marcado com `@NamedInterface` (fachadas finas + DTOs/records). Repositórios e entidades deixam de ser consumidos por outros módulos;
   - eventos transacionais: `ApplicationEventPublisher` + `@ApplicationModuleListener` com *event publication registry* JPA (`spring-modulith-starter-jpa`), tabela criada por **migração Flyway** (não pela inicialização automática, por causa de `ddl-auto: validate`);
   - `spring-modulith-starter-test` (`@ApplicationModuleTest`) e geração de documentação.
   - **Não** se adotam: *event externalization* para brokers, `@DomainEvents`/agregados, Moments, nem qualquer barramento de comandos.
3. **Regra de dependência:** sem ciclos entre módulos; chamadas síncronas só para leitura ou para operações que **têm** de ser atómicas com o chamador, sempre via API pública; efeitos subsequentes (notificar, criar entrega após pagamento, atualizar estado por evento de entrega, ledger) via eventos *after-commit*.
4. **Nova redação proposta para "Architecture Constraints" no `CLAUDE.md`** (aplicada ao `CLAUDE.md` com a aceitação deste ADR, 2026-10-28):

   > The backend is a layered modular monolith. Each top-level package under `com.delivery` is a Spring Modulith application module that keeps the `controller/service/repository/entity/dto/mapper` layering internally. Spring Modulith is used **only** for structural verification (`ApplicationModules.verify()` in CI) and transactional application events (`@ApplicationModuleListener` with the JPA event publication registry). Modules talk to each other through their public API (base package or `@NamedInterface` `api` package) or events — never through another module's repositories or entities, and without cycles. Still avoided: DDD tactical patterns, Clean/Hexagonal Architecture, CQRS, event sourcing, command buses, and Kafka/event externalization. See `docs/adr/ADR-0002-module-structure.md`.

5. **Como quebrar os ciclos:**

| Ciclo | Estratégia |
|---|---|
| C1 `order↔payment` | `payment` expõe `PaymentApi` (`createForOrder`, `requestRefund`) e deixa de ler `OrderRepository`; ao confirmar/reembolsar publica `PaymentConfirmed`/`RefundCompleted`, consumidos por `order` (`@ApplicationModuleListener`) que transita a encomenda. Direção final: `order → payment` |
| C2 `order↔delivery` | `delivery` consome `OrderReadyForPickup` (ou `OrderPlaced`) para criar a `Delivery`; publica `DeliveryCompleted`/`DeliveryFailed`, consumidos por `order`. `DeliveryCodeHasher` passa para a API de `delivery` (ou `common`) e `order` deixa de usar `DeliveryRepository`. Direção final: `delivery → order` (leitura via `OrderApi`) |
| C3 `cart↔marketing` (+ `order→cart→marketing→order`) | `marketing` deixa de importar `Cart`/`CartItem`: o `PromotionResolver` recebe um record neutro (`PricingInput` com linhas, vendor, cliente) definido na API de `marketing`; `cart` e `order` fazem o mapeamento. O SPI `CheckoutDiscountResolver` (hoje em `order`, implementado em `marketing`) passa para a API de `marketing` e `order` depende dele. Direção final: `cart → marketing`, `order → marketing` |
| C4 `dispatch↔dashboard` | `calculateCourierEarnings` sai de `DashboardService` para `dispatch` (ou `finance`); `dashboard` passa a ser só leitor (agregador) via APIs. Direção final: `dashboard → dispatch` |
| C5 `order↔sales` | Extrair o escopo de comerciante (`isMerchantScoped`, `resolveOwnVendorId`, `requireOwnVendorId`, `ensureOwnVendorOrder`) de `SalesAccessGuard` para um *guard* partilhado — `vendor.api.MerchantScopeGuard` (alinhado com `merchant_users` do ADR-0001). `sales` fica só com mascaramento de PII. Direção final: `sales → order`, ambos `→ vendor` |
| C6 `catalog↔inventory` | `InventoryService` usa o mesmo `MerchantScopeGuard` em vez de `CatalogAccessGuard`; `catalog` cria stock via `InventoryApi.initialize(...)` (síncrono, mesma transação — a criação de SKU e stock tem de ser atómica). Direção final: `catalog → inventory` |
| C7 `delivery↔dispatch` | Extrair um módulo `courier` (entidade `Courier`, documentos, disponibilidade, `CourierRepository`) de `dispatch`; `delivery`, `dispatch` e `dashboard` dependem de `courier`. `dispatch` consulta entregas via `DeliveryApi`. Direção final: `dispatch → delivery → courier` |
| C8 `auth↔tenant` | Com o ADR-0001 o registo de comerciante deixa de criar tenants; o provisioning (`SubscriptionProvisioningService`) passa para `tenant`. `auth` fica identidade/perfis. Direção final: `tenant → auth` |
| C9 `auth↔vendor` | `MerchantRegistrationService` passa para `vendor` (onboarding), que usa `auth` (Keycloak + perfis) via API. Direção final: `vendor → auth` |

## Rationale

O Spring Modulith dá precisamente as duas coisas que faltam — fronteiras verificáveis e eventos *after-commit* persistidos — sem impor um estilo de modelação. Mantém-se o código "em camadas por domínio" que a cláusula atual pretende proteger; o que se proíbe passa a ser o acoplamento acidental entre domínios. ArchUnit resolveria só metade (verificação) e obrigaria a construir o *outbox* à mão. Módulos Maven ou hexagonal são desproporcionados para uma equipa pequena e um MVP. O facto de a Fase 1 ter criado três ciclos novos ao corrigir segurança mostra que a regra tem de ser executável, não apenas escrita.

## Consequences

### Positive
- CI falha em novos ciclos ou acessos a internos de outro módulo.
- Base fiável para AS-12 (expiração, notificações, ledger, dispatch automático) via eventos com registo e republicação.
- APIs de módulo explícitas tornam os testes `@ApplicationModuleTest` possíveis e reduzem o raio de impacto das alterações.
- Documentação de módulos gerada a partir do código.

### Negative
- Nova dependência (`spring-modulith-bom` 1.4.x, compatível com Boot 3.5) e uma tabela `event_publication` a operar (limpeza de publicações concluídas).
- Por defeito o Modulith considera **internos** os subpacotes (`entity`, `repository`, `service`); a verificação inicial reportará as ~128 importações cruzadas como violações até serem migradas.
- Algumas transições passam de síncronas (mesma transação) a eventuais (*after-commit*): é preciso desenhar estados intermédios e idempotência nos *listeners*.
- Mais código de fachada/DTO nas fronteiras.

## Risks and Mitigations

| Risco | Mitigação |
|---|---|
| `verify()` vermelho bloqueia o desenvolvimento | Introduzir primeiro um teste com *baseline* de violações (`ApplicationModules.detectViolations()` comparado com uma lista versionada): falha só em violações **novas**; a lista só pode diminuir |
| Perda/duplicação de efeitos com eventos | *Event publication registry* JPA (entrega *at-least-once*, republicação no arranque); *listeners* idempotentes com chaves únicas existentes (`UNIQUE (tenant_id, order_id)` em `deliveries`, idempotência de reembolsos) |
| Consistência: checkout deixa de criar `Payment`/`Delivery` na mesma transação | Manter `Payment` síncrono via `PaymentApi` (faz parte da resposta do checkout); passar apenas a criação de `Delivery` e as notificações para eventos |
| Agentes/IA a interpretar a cláusula nova como licença para DDD/CQRS | Texto do `CLAUDE.md` lista explicitamente o que continua proibido; revisão de PR |
| Schema auto-inicializado do Modulith conflitua com `ddl-auto: validate` | `spring.modulith.events.jdbc.schema-initialization.enabled=false`; tabela por migração Flyway |

## Migration Implications

1. **Incremento "Modulith baseline"**: dependências (`spring-modulith-starter-core`, `-starter-jpa`, `-starter-test`), migração `V0NN__event_publication.sql`, `package-info.java` para `common` (OPEN), teste `ModularityTest` com baseline de violações e geração de documentação. Sem alterações de comportamento.
2. **Quebra de ciclos**, um por incremento, por ordem de risco/benefício: C5 + C6 (*guard* partilhado, sem mudança de comportamento) → C4 → C9/C8 (com ADR-0001) → C3 → C7 (módulo `courier`) → C2 → C1 (estes dois introduzem eventos e exigem testes de caracterização dos estados da encomenda).
3. Cada incremento remove as suas entradas do baseline; quando o baseline estiver vazio, o teste passa a `verify()` estrito.
4. O `CLAUDE.md` (cláusula nova) e `docs/architecture.md` foram atualizados na aceitação deste ADR (2026-10-28); o código (dependências, migração, `ModularityTest`) só entra no incremento "Modulith baseline".
5. Não há migração de dados de negócio; só a tabela `event_publication`.

## Validation Criteria

- `ApplicationModules.of(DeliveryApplication.class).verify()` passa em CI sem baseline.
- `grep -rE '^import com\.delivery\.[a-z]+\.(entity|repository)\.'` fora do próprio módulo devolve 0 (exceto `common`).
- Grafo de módulos gerado sem ciclos; C1–C9 ausentes.
- Testes de integração confirmam que, após `PaymentConfirmed`/`DeliveryCompleted`, a encomenda chega ao estado esperado e que reentregar o mesmo evento é idempotente.
- Tabela `event_publication` sem publicações incompletas após a suite E2E.

## Implementation notes

**2026-10-29 — incremento "Modulith baseline" (W2-A, LAC-46/LAC-47):**
- Spring Modulith **1.4.14** (BOM) com `spring-modulith-starter-core` (sem `spring-modulith-moments`, excluído), `spring-modulith-starter-jdbc` e `spring-modulith-starter-test`.
- **Registo de eventos JDBC em vez de JPA.** O texto acima diz "JPA", mas a mitigação de risco deste mesmo ADR usa a propriedade `spring.modulith.events.jdbc.schema-initialization.enabled` e o LAC-47 pede `-starter-jdbc`. Escolheu-se JDBC porque: (1) não acrescenta uma entidade ao *persistence unit*, logo não há risco de o `ddl-auto: validate` divergir do esquema Flyway; (2) a escrita usa a mesma ligação/transação do `JpaTransactionManager`, portanto a atomicidade com a transação de negócio é a mesma; (3) o esquema é exatamente o `schema-postgresql.sql` do módulo, copiado para a migração V052. A garantia (at-least-once, republicação) é idêntica.
- Republicação no arranque desligada; um job com ShedLock (`EventPublicationRetryJob`) reenvia as incompletas (seguro com várias réplicas). Limpeza de concluídas aos 7 dias.
- `ModularityTest` com *baseline* versionado (`backend/src/test/resources/modulith/violations-baseline.txt`): os ciclos são registados como arestas dentro de componentes fortemente ligados (o ArchUnit trunca a lista de ciclos aos 100 caminhos) e os acessos a internos como `<módulo> -> <tipo>`. Falha em entradas novas e em entradas obsoletas. Com `common` OPEN, os ciclos `common↔tenant` e `common↔auth` deixam de contar como ciclos de módulo.
- Primeiro evento: `order.OrderStatusChanged` (ver `docs/architecture/EVENT-CATALOG.md`).

**2026-10-29 — W3-A, quebra dos ciclos (LAC-47, restante):**

Resultado: o grafo de módulos ficou **acíclico** — C1–C9, `cart↔geo` e o transitivo `tenant→dispatch→order→cart→tenant` desapareceram; o *baseline* passou de 248 linhas (60 `cycle-edge` + 188 `non-exposed`) para 140 (0 `cycle-edge` + 140 `non-exposed`). Nenhuma linha nova (o `-Dmodulith.baseline.write=true` só apagou linhas). Dependências finais (sem `common`):

```text
auth -> (nenhum)                 upload, rating, support -> (nenhum)
vendor -> auth upload            inventory -> vendor
catalog -> inventory upload vendor
geo -> catalog vendor            tenant -> auth geo vendor
marketing -> catalog vendor      customer -> auth geo
notification -> auth             payment -> auth
delivery -> upload               finance -> payment
cart -> auth catalog geo inventory marketing tenant vendor
order -> auth cart catalog customer delivery inventory marketing notification payment vendor
dispatch -> auth delivery order upload
sales -> auth catalog delivery finance notification order payment vendor
dashboard -> auth delivery dispatch finance order payment tenant vendor
report -> vendor
```

| Ciclo | Como foi quebrado | Direção final |
|---|---|---|
| C5 `order↔sales` | A regra de mascaramento de PII passou para `order.api.CustomerPiiMasking`; `OrderService`/`OrderListingService` usam `vendor.api.MerchantScopeGuard` diretamente; `SalesAccessGuard` delega em ambos (API pública inalterada). | `sales → order`, ambos `→ vendor` |
| C6 `catalog↔inventory` | `InventoryService` usa `MerchantScopeGuard` em vez de `CatalogAccessGuard`, com a **mesma** regra (ADMIN/OPS são *tenant-wide*; não `hasTenantWideRole()`, que inclui FINANCE). O `InventoryApi.initialize` do plano não foi feito: `catalog → inventory` já é a direção certa e o acesso fica no *baseline*. | `catalog → inventory → vendor` |
| C4 `dispatch↔dashboard` | `calculateCourierEarnings` saiu de `DashboardService` para `dispatch.api.CourierEarnings`, usado por `CourierService` e pelo `DashboardService`. | `dashboard → dispatch` |
| `tenant→dispatch` (transitivo `tenant→dispatch→order→cart→tenant`) | `PlatformController` (`/api/v1/platform/stats`) mudou para o módulo `dashboard` (agregador entre domínios); as contagens de tenants vêm de `tenant.api.TenantStats`. Caminho, `operationId` e *tag* OpenAPI inalterados. | `dashboard → tenant` |
| C8 `auth↔tenant` / C9 `auth↔vendor` | `MerchantRegistrationService`, `SubscriptionProvisioningService`, os dois *controllers*, os DTOs e a entidade/repositório `ProvisioningRequest` passaram de `auth` para **`tenant`**. Identidade via `auth.api.UserAccounts` (Keycloak + perfis); vendedor + OWNER via `vendor.api.MerchantOnboarding`; `@PasswordPolicy` exposto como *named interface* `auth.validation`. Mesma transação e mesma compensação (apagar o utilizador Keycloak). **Desvio do plano (C9 → `vendor`)**: o registo procura/cria tenants, logo em `vendor` criaria `vendor→tenant` com `tenant→vendor` (provisioning) — em `tenant` fica acíclico. | `tenant → auth`, `tenant → vendor → auth` |
| C3 `cart↔marketing` (+ `order→cart→marketing→order`) | `marketing.api.PromotionBasket` (interface neutra: tenant, vendedor, cliente, subtotal, cupão, linhas) substitui `Cart`/`CartItem` no `PromotionResolver`; a entidade `Cart` implementa-a (sem cópia, os valores são sempre os do carrinho). `CheckoutDiscountResolver`/`CheckoutDiscount` passaram de `order.service` para `marketing.api`. | `cart → marketing`, `order → marketing` |
| `cart↔geo` | `geo.api.DeliveryFeeEstimator` (interface) é implementada pelo motor único `cart.service.PricingService`; `SearchService` depende só da interface. Mover o motor para `geo` foi rejeitado: `tenant → geo` (zonas no `AdminController`, ficheiro do W3-D) fecharia `geo→tenant→geo` por causa das `fee_policies`. | `cart → geo` |
| C7 `delivery↔dispatch` | `delivery` declara `AssignedCourierResolver` (estafeta do chamador) e `DeliveryRetirementListener` (cancelar o *dispatch job* ativo); `dispatch` implementa-os (`CourierIdentityResolver`, `DispatchJobRetirement`). `DeliveryRetirementService` passou para `delivery.api`. **Desvio do plano:** não se extraiu o módulo `courier` — com os dois *hooks* o ciclo desaparece sem mover entidades/tabelas; fica como opção se `courier` crescer. | `dispatch → delivery` |
| C2 `order↔delivery` | `delivery.api.DeliveryOrderHooks` (implementado por `order.service.OrderDeliveryHooks`): verificação `order_not_deliverable`, DELIVERED + *commit* de stock, FAILED → CANCELLED + *release*, *commit* no PICKED_UP, morada. `DeliveryAddressResponse` passou para `delivery.api` (schema OpenAPI com o mesmo nome). **Desvio do plano (direção `delivery → order` com eventos):** o *checkout* cria a `Delivery` e a rejeição retira-a na mesma transação, por isso `order → delivery` mantém-se e é `delivery` que deixa de ver `order`. | `order → delivery` |
| C1 `order↔payment` | `payment.api.PaymentOrderHooks` (implementado por `order.service.OrderPaymentHooks`): dados da encomenda para a confirmação, PAYMENT_CONFIRMED, e os efeitos do reembolso total (REFUNDED + auditoria, retirada da entrega e libertação de stock salvo se a mercadoria já saiu da loja). `payment` deixa também de depender de `delivery`. | `order → payment` |

**Decisões "correção primeiro" (síncrono via `api`, não evento):** nenhum dos efeitos cruzados destes ciclos podia passar a *after-commit* sem mudar comportamento, por isso **não foi introduzido nenhum evento novo** e os eventos previstos (`PaymentConfirmed`, `RefundCompleted`, `DeliveryCompleted`/`DeliveryFailed`, `OrderReadyForPickup`) continuam por fazer:
- *Reembolso total* (`PaymentService.approveRefund` → `PaymentOrderHooks.onPaymentFullyRefunded`) e *rejeição pelo vendedor* (`OrderService.rejectByVendor` → retirada → *release* → `PaymentService.requestRemainingRefund`): regra de ordem de *locks* (pagamento `FOR UPDATE` → encomenda → entrega/*dispatch* → inventário) e "qualquer falha desfaz tudo" — têm de ser uma transação.
- *Conclusão/falha da entrega* (`DeliveryOrderHooks`): estado da entrega, estado da encomenda e ciclo de stock (INC-07) são uma invariante; testes (`DeliveryCompletionPostgresIntegrationTest`, `RefundAndOverrideStockPinPostgresTest`) verificam-na logo a seguir à chamada; o `order_not_deliverable` tem de ser verificado **antes** de qualquer efeito.
- *Confirmação de pagamento* (`PaymentOrderHooks.markPaymentConfirmed`): o cliente vê PAYMENT_CONFIRMED na resposta; um evento abriria uma janela em que o pagamento está confirmado e a encomenda não.
- *Retirada → cancelamento do dispatch job* (`DeliveryRetirementListener`): liberta o estafeta e as vagas dos índices V035 na mesma transação.
- *Desconto no checkout* (`CheckoutDiscountResolver`): consome a quota da promoção com `UPDATE` condicional e escreve a `promotion_redemption` antes do *commit* do checkout.

Estes *hooks* são inversão de dependência simples (interface no `api` do módulo de baixo, implementação no de cima, ligada pelo Spring) — não um *command bus*. Quando estes fluxos ganharem estados intermédios (LAC-25) ou um PSP real, os efeitos que puderem ser eventuais passam a eventos.

**O que fica no baseline (140 `non-exposed`, nenhum ciclo):** acessos "para baixo" a entidades/repositórios/serviços internos que não formam ciclo — maiores grupos `order→cart/catalog/payment/delivery` (24), `dashboard→*` (23), `sales→order/payment/…` (20), `cart→catalog/vendor/…` (18), `dispatch→delivery/order` (11), `finance→payment` (8), `marketing→catalog` (6), `tenant→auth/geo` (6, `AdminController`, ficheiro do W3-D), `common→auth/geo/tenant` (3, filtros de tenancy). Removê-los exige fachadas de leitura por módulo (ex.: `payment.api` para *sales/finance/dashboard*, `order.api` de leitura para *sales/dashboard*); é o trabalho que falta para o `verify()` estrito.

Ficheiros de teste: as instâncias de serviços nos testes unitários passaram a ligar as implementações reais dos *hooks* com os mesmos *mocks* (as asserções não mudaram); os testes de *slice* (`@DataJpaTest` + `@Import`) importam as implementações dos *hooks*; os testes do registo/provisioning mudaram para o pacote `com.delivery.tenant`.

## References

- [AS-IS-ARCHITECTURE.md §4.3 (dependências), §4.4 (eventos/jobs), §13 (AS-12, AS-18), §16 (D2)](../assessment/AS-IS-ARCHITECTURE.md)
- `CLAUDE.md` § Architecture Constraints; `docs/architecture.md`
- `docs/refactoring/CLAUDE-CODE-PROMPTS.md` — INC-10 e secção "Fora destes prompts" (Spring Modulith após ADR-0002)
- [Spring Modulith — Reference](https://docs.spring.io/spring-modulith/reference/) (Fundamentals, Verifying Application Module Structure, Working with Application Events / Event Publication Registry)
- ADR-0001 (tenancy) — pré-requisito para C8/C9 e para o `MerchantScopeGuard`
