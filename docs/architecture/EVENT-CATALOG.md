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
| Produtor | módulo `order` — `OrderService.acceptByVendor`, `markPreparing`, `markReadyForPickup` (transições de *fulfilment* do comerciante) |
| Quando | A encomenda mudou de estado nessas transições (`PAYMENT_CONFIRMED → ACCEPTED_BY_VENDOR → PREPARING → READY_FOR_PICKUP`) |
| Payload | `tenantId` (UUID), `orderId` (UUID), `vendorId` (UUID), `previousStatus` (String, `OrderStatus`), `newStatus` (String, `OrderStatus`), `occurredAt` (Instant) |
| Consumidores | `dashboard` — `OrderStatusMetricsListener`: incrementa `order_status_transitions_total{from,to}` e regista uma linha de log. Não altera estado. |
| Garantia | At-least-once; o consumidor atual tolera duplicados (pode apenas sobrecontar a métrica). |
| Testes | `OrderStatusChangedEventPostgresTest` (publicação persistida e concluída; falha → incompleta → reenviada pelo *retry job*) |
| Notas | Ainda **não** é publicado por cancelamento, rejeição, reembolso, entrega nem *override* de vendas: esses caminhos entram com a máquina de estados explícita (LAC-25) e a quebra dos ciclos C1/C2 (W3-A). Não usar este evento como fonte completa do histórico de estados até lá. |

## Eventos previstos (ainda não implementados)

Do ADR-0002 e do registo de lacunas (LAC-25, LAC-47) — entram aqui quando forem implementados: `PaymentConfirmed`, `RefundCompleted` (`payment` → `order`), `OrderReadyForPickup`/`OrderCancelled` (`order` → `delivery`), `DeliveryPickedUp`/`DeliveryCompleted`/`DeliveryFailed` (`delivery` → `order`).
