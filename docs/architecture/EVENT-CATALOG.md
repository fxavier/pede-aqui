# Catálogo de eventos de aplicação

Fonte de verdade dos eventos transacionais do backend (LAC-47, ADR-0002). Todo o evento novo entra aqui no mesmo PR que o introduz.

## Mecanismo (comum a todos os eventos)

| Aspeto | Regra |
|---|---|
| Publicação | `ApplicationEventPublisher.publishEvent(...)` **dentro** da transação de negócio do produtor. |
| Registo (*outbox*) | Spring Modulith 1.4, registo **JDBC** (`spring-modulith-starter-jdbc`), tabela `event_publication` (Flyway V052). Na mesma transação grava-se uma linha por *listener*; se a transação fizer rollback, não fica nada. |
| Consumo | `@ApplicationModuleListener` = depois do *commit*, assíncrono, na sua própria transação. Sucesso → `completion_date` preenchida. |
| Garantia | **At-least-once.** Um *listener* que lança exceção deixa a publicação incompleta; o `EventPublicationRetryJob` (1 min, uma réplica via ShedLock) reenvia as incompletas com mais de 5 min. Os *listeners* têm de ser idempotentes. |
| Republicação no arranque | Desligada (`spring.modulith.events.republish-outstanding-events-on-restart=false`): com várias réplicas, uma instância a reiniciar reenviaria publicações que outra ainda está a processar. |
| Retenção | Publicações concluídas apagadas ao fim de 7 dias (`EventPublicationCleanupJob`, diário 03:30 Africa/Maputo). Incompletas nunca são apagadas automaticamente. |
| Localização | O tipo do evento vive no pacote base do módulo produtor (API pública do módulo), como `record`. |
| Conteúdo | Só ids, estados e instantes. **Sem dados pessoais** (nomes, telefones, moradas, PIN). |
| Não adotado | Externalização para *brokers* (Kafka etc.), Moments, `@DomainEvents`. |

Operação: publicações pendentes — `SELECT listener_id, event_type, publication_date FROM event_publication WHERE completion_date IS NULL ORDER BY publication_date;`

## Eventos

### `com.delivery.order.OrderStatusChanged`

| Campo | Valor |
|---|---|
| Produtor | módulo `order` — **`order.api.OrderStateMachine`** (W4-A, LAC-25): a única porta de mudança de estado da encomenda. Chamadores: checkout (só histórico, ver Notas), confirmação de pagamento (`OrderPaymentHooks`), aceitar/preparar/pronto/rejeitar (`OrderService`), atribuição de estafeta (`DispatchService`), recolha/a caminho/entrega/falha (`OrderDeliveryHooks`), reembolso total (`OrderPaymentHooks`), cancelamento e *override* de vendas (`SalesService`), jobs `payment-expiry` e `acceptance-timeout` (`OrderTimeoutService`) |
| Quando | **Toda** a transição aceite pela tabela de estados (ver `CLAUDE.md`, Key API Flows), na mesma transação que muda `orders.status` e escreve `order_status_history` (V058). Uma transição recusada (409 `invalid_order_transition`) não publica nada. |
| Payload | `tenantId` (UUID), `orderId` (UUID), `vendorId` (UUID), `previousStatus` (String, `OrderStatus`), `newStatus` (String, `OrderStatus`), `occurredAt` (Instant) |
| Consumidores | `dashboard` — `OrderStatusMetricsListener`: incrementa `order_status_transitions_total{from,to}` e regista uma linha de log. Não altera estado. |
| Garantia | At-least-once; o consumidor atual tolera duplicados (pode apenas sobrecontar a métrica). Consumidores novos têm de ser idempotentes (chave natural: `orderId` + `newStatus` — a tabela não tem ciclos, cada estado é atingido no máximo uma vez por encomenda). |
| Testes | `OrderStatusChangedEventPostgresTest` (publicação persistida e concluída; falha → incompleta → reenviada pelo *retry job*), `OrderStateMachineTest` (um evento por transição aceite, nenhum por recusada; propriedade sobre sequências aleatórias), `OrderTimeoutPostgresTest` (publicação pelos jobs de timeout) |
| Notas | A criação no checkout escreve a linha de histórico `null → PAYMENT_PENDING` mas **não** publica o evento (não é uma transição; `previousStatus` nunca é nulo). A fonte completa e ordenada do histórico é a tabela `order_status_history` (V058, *append-only*, `GET /api/v1/orders/{id}/history`), não o registo de publicações (as concluídas são apagadas ao fim de 7 dias). Os efeitos síncronos dos ciclos C1/C2 (W3-A) continuam *hooks* (ver abaixo); os estados intermédios da entrega passaram a chegar à encomenda por esses *hooks* (`onPickedUp`, `onOutForDelivery`), não por eventos. |

## Eventos previstos (ainda não implementados)

Do ADR-0002 e do registo de lacunas (LAC-25, LAC-47) — entram aqui quando forem implementados: `PaymentConfirmed`, `RefundCompleted` (`payment` → `order`), `OrderReadyForPickup`/`OrderCancelled` (`order` → `delivery`), `DeliveryPickedUp`/`DeliveryCompleted`/`DeliveryFailed` (`delivery` → `order`). Desde W4-A, `OrderReadyForPickup`/`OrderCancelled` são cobertos por `OrderStatusChanged` (filtrar por `newStatus`); um evento de pagamento (`PaymentStatusChanged`) não foi criado — o histórico de pagamento está em `payment_status_history` (V058).

**Decisão W3-A (2026-10-29):** os ciclos `order↔payment` (C1) e `order↔delivery` (C2) foram quebrados **sem** estes eventos. Os efeitos que eles transportariam têm hoje de ser atómicos com o produtor (regra de ordem de *locks* nos caminhos de dinheiro; estado da entrega + estado da encomenda + ciclo de stock como uma invariante; `order_not_deliverable` verificado antes de qualquer efeito), por isso continuam síncronos através de interfaces declaradas pelo módulo de baixo e implementadas por `order`:

| Interface (API) | Implementação | Substitui o evento previsto |
|---|---|---|
| `payment.api.PaymentOrderHooks` | `order.service.OrderPaymentHooks` | `PaymentConfirmed`, `RefundCompleted` |
| `delivery.api.DeliveryOrderHooks` | `order.service.OrderDeliveryHooks` | `DeliveryPickedUp`, `DeliveryCompleted`, `DeliveryFailed` |
| `delivery.api.DeliveryRetirementListener` | `dispatch.service.DispatchJobRetirement` | (cancelamento do *dispatch job* na retirada) |

Não são eventos: correm na transação do chamador, sem registo em `event_publication`. Quando um destes efeitos puder ser eventual (estados intermédios da LAC-25, PSP com *callbacks*), passa a evento e entra nesta tabela.
