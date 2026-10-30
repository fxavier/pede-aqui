# ADR-0005 — Razão de partidas dobradas e comissões (modelo de agente de cobrança)

| Campo | Valor |
|---|---|
| Status | **Proposed** — o modelo de lançamentos está **"a validar"** com o contabilista certificado e o parecer jurídico (LAC-38) |
| Date | 2026-10-30 |
| Decisores | Xavier Francisco (owner técnico); validação contabilística/jurídica pendente |
| Achados relacionados | LAC-33 (ledger), LAC-34 (comissões), LAC-38 (fluxo de fundos e fiscal), LAC-26 (taxa de cancelamento), LAC-36 (reembolso no PSP), LAC-47 (eventos) em `docs/gaps/REGISTO-LACUNAS.md` |
| Relacionado | ADR-0002 (módulos/eventos), ADR-0003 (dinheiro: `NUMERIC` + `BigDecimal`; D5: ledger `NUMERIC(14,2)` com moeda explícita) |
| Pacote de trabalho | W6-B (V065, V066) |

## Context

- O dinheiro do cliente é capturado no checkout (`payments`, hoje confirmação *mock*; PSP em LAC-31). Os reembolsos são registos administrativos (`refunds`, sem *payout*). A tabela `commissions` (V008) existia sem escritor; `fee_policies.commission_rate` (V010) nunca foi lido.
- Não havia razão: os valores devidos a comerciantes e estafetas e a receita da plataforma não eram calculados em lado nenhum.
- Decisão do owner (2026-10-30): **a plataforma é agente de cobrança** — o comerciante é o vendedor de registo (*merchant of record*); a plataforma cobra por conta dele e retém a comissão e as suas taxas. O razão regista o que se deve aos comerciantes (e, mais tarde, aos estafetas). **Comissão por defeito: 15 % do subtotal de bens do comerciante**, configurável por plano, com override por comerciante.

## Decision

1. **Módulo `settlement`** (Spring Modulith, topo do grafo): consome eventos (`PaymentConfirmed`, `RefundApproved`, `OrderStatusChanged`) e as APIs de leitura `payment.api.PaymentSettlementFacts` / `order.api.OrderSettlementFacts`. Nenhum módulo depende dele.
2. **Esquema (V065)**: `ledger_accounts` (tenant, tipo, dono, moeda), `ledger_transactions` (chave de idempotência UNIQUE por tenant, tipo, origem, `occurred_at`, descrição), `ledger_entries` (débito **ou** crédito > 0, `NUMERIC(14,2)`, moeda `'MZN'` por CHECK). Imutável (UPDATE/DELETE/TRUNCATE → 42501; correções só por estorno). Equilíbrio por *constraint trigger* `DEFERRABLE INITIALLY DEFERRED` (Σ débitos = Σ créditos por transação e moeda no COMMIT; transação sem linhas recusada). Saldos derivados (vista `ledger_account_balances`), sem saldo corrente guardado.
3. **Escrita**: só pelos *listeners* do módulo (`@ApplicationModuleListener`, depois do commit, at-least-once) — cada lançamento tem uma chave fixa (`payment-captured:<paymentId>`, `order-delivered:<orderId>`, `refund-approved:<refundId>`, `cancellation-fee:<orderId>`), por isso uma reentrega não duplica nada. Os lançamentos leem os factos *committed* (não confiam na ordem dos eventos) e garantem primeiro os lançamentos de que dependem (captura → entrega → reembolso/taxa).
4. **Comissões (V066)**: `commission_plans` (taxa, vigência, `is_default`) + `vendor_commission_overrides` (plano ou taxa própria, vigência). Resolução no instante DELIVERED: override do comerciante → plano por defeito do tenant → `app.settlement.default-commission-rate` (0.15). V066 semeia um plano "Comissão padrão 15%" por tenant. A comissão bruta é gravada em `commissions` (uma linha por encomenda) no mesmo commit do lançamento.

### Plano de contas

| Conta | Natureza | Dono | Uso |
|---|---|---|---|
| `PLATFORM_CASH_CLEARING` | Ativo | plataforma | dinheiro recebido do PSP/mock ainda não liquidado no banco |
| `CUSTOMER_FUNDS_HELD` | Passivo | plataforma | dinheiro do cliente retido até à entrega (agente: não é receita) |
| `CUSTOMER_REFUNDS_PAYABLE` | Passivo | plataforma | reembolsos aprovados ainda não devolvidos (sem PSP, LAC-36) |
| `MERCHANT_PAYABLE` | Passivo | comerciante (`vendor_id`) | valor devido ao comerciante |
| `COURIER_PAYABLE` | Passivo | estafeta | previsto; ainda sem lançamentos |
| `PLATFORM_REVENUE_COMMISSION` | Receita | plataforma | comissão |
| `PLATFORM_REVENUE_FEES` | Receita | plataforma | taxa de entrega + taxa de serviço |
| `PLATFORM_PROMOTION_EXPENSE` | Gasto | plataforma | descontos de promoção suportados pela plataforma |
| `PLATFORM_VAT_PAYABLE` | Passivo | plataforma | IVA sobre a receita própria (só com `platform-vat-rate` > 0) |

### Modelo de lançamentos (a validar — LAC-38)

Notação da encomenda: S = subtotal de bens, T = imposto cobrado sobre os bens (`orders.taxes`), F = taxas (entrega + serviço), D = desconto, P = total capturado (= S + F + T − D), r = taxa de comissão, C = r × S.

| Facto (evento) | Débito | Crédito |
|---|---|---|
| Pagamento capturado (`PaymentConfirmed`) | `PLATFORM_CASH_CLEARING` P | `CUSTOMER_FUNDS_HELD` P |
| Encomenda entregue (`OrderStatusChanged` → DELIVERED) | `CUSTOMER_FUNDS_HELD` P; `PLATFORM_PROMOTION_EXPENSE` D (desconto da plataforma) | `PLATFORM_REVENUE_COMMISSION` C; `PLATFORM_REVENUE_FEES` F; `MERCHANT_PAYABLE[v]` S + T − C (− D se o desconto for do comerciante); `PLATFORM_VAT_PAYABLE` (se IVA > 0, extraído de C e F) |
| Reembolso aprovado **antes** da entrega (`RefundApproved`) | `CUSTOMER_FUNDS_HELD` R | `CUSTOMER_REFUNDS_PAYABLE` R |
| Reembolso aprovado **depois** da entrega | estorno proporcional R/base de cada linha de receita/gasto e `MERCHANT_PAYABLE[v]` | `CUSTOMER_REFUNDS_PAYABLE` R (e `PLATFORM_PROMOTION_EXPENSE` proporcional) |
| Cancelamento pelo cliente com taxa retida (W5-A, `OrderStatusChanged` → CANCELLED + `CUSTOMER_CANCELLED_WITH_FEE`) | `CUSTOMER_FUNDS_HELD` fee | `PLATFORM_REVENUE_COMMISSION` r × fee; `MERCHANT_PAYABLE[v]` fee − r × fee |

Regras de detalhe:

- **Antes/depois da entrega** decide-se pelo instante: aprovação do reembolso (`refunds.updated_at`) vs. primeira transição para DELIVERED (`order_status_history`). Assim o resultado não depende da ordem em que os eventos são processados.
- **Reembolsos parciais antes da entrega** reduzem a base da entrega: base = P − Σ reembolsos aprovados antes de DELIVERED; S, F e D são escalados por base/P antes de calcular C.
- **Estorno proporcional depois da entrega**: cada linha da entrega (exceto fundos retidos e comerciante) × R/base, arredondada HALF_UP a 2 casas; o comerciante absorve o resíduo, para a transação fechar exata. Um reembolso total anula exatamente a entrega; vários parciais podem desviar ±0,01 por componente (vai para o comerciante).
- **Comissão em `commissions`** = bruta acumulada na entrega (ou sobre a taxa de cancelamento, `source = CANCELLATION_FEE`); os estornos por reembolso só existem no razão. Os leitores atuais (relatórios, dashboard, detalhe de venda) continuam a somar `commission_amount`.
- Outros cancelamentos (rejeição do vendedor, timeouts, cancelamento gratuito, entrega falhada) não geram lançamento próprio: o dinheiro move-se pelos reembolsos. Uma entrega falhada sem reembolso deixa o valor em `CUSTOMER_FUNDS_HELD` (visível na conta).
- Uma encomenda entregue sem pagamento capturado (dados antigos/seeds) não gera lançamento (`ledger_postings_total{result=skipped}` + WARN).

### Itens a validar com o contabilista / parecer jurídico (LAC-38)

1. Enquadramento de agente de cobrança (mandato) vs. comerciante de registo da plataforma vs. PSP com *split* — define se `CUSTOMER_FUNDS_HELD`/`MERCHANT_PAYABLE` são passivos de terceiros (como aqui) ou receita/custo.
2. **IVA sobre bens** (`orders.taxes`): tratado como imposto do comerciante, cobrado por conta dele e incluído no `MERCHANT_PAYABLE`. A W6-A muda o IVA para preços com IVA incluído por produto — rever o lançamento quando chegar.
3. **IVA sobre a receita da plataforma** (comissão e taxas, 16 %): desligado por defeito (`app.settlement.platform-vat-rate=0`); quando ligado, comissão e taxas são tratadas como **IVA incluído** e repartidas em receita líquida + `PLATFORM_VAT_PAYABLE`. Confirmar se a comissão é faturada ao comerciante com IVA acrescido (e quem o suporta).
4. **Base da comissão**: subtotal de bens sem imposto e antes do desconto. Confirmar se o desconto suportado pelo comerciante reduz a base.
5. **Quem suporta os descontos** de promoção: por defeito a plataforma (`PLATFORM_PROMOTION_EXPENSE`); alternativa `app.settlement.discount-funded-by=MERCHANT`. As promoções não têm hoje atributo de financiador.
6. **Taxa de entrega/serviço** inteiramente receita da plataforma até existir remuneração de estafetas (`COURIER_PAYABLE` + gasto, LAC-35).
7. **Taxa de cancelamento** retida: atribuída ao comerciante menos a comissão à mesma taxa — confirmar se pertence ao comerciante, à plataforma ou é repartida.
8. **Estorno proporcional** depois da entrega (o comerciante e a plataforma devolvem na proporção do que receberam) vs. reembolso suportado só pelo comerciante ou só pela plataforma conforme a causa.
9. Faturação (faturas de comissão numeradas), retenções na fonte e momento de reconhecimento (entrega vs. pagamento) — fora do âmbito desta versão.

## Consequences

### Positive
- Saldo devido a cada comerciante disponível (`GET /api/v1/finance/merchants/{vendorId}/balance`) e diário completo (`GET /api/v1/finance/ledger/transactions`), derivados de factos imutáveis.
- O equilíbrio é garantido pela base de dados, não só pelo código; a idempotência por chave torna os eventos at-least-once seguros; a reconciliação diária (`ledger-reconciliation`) alerta se algo escapar.
- Nenhum módulo produtor mudou de comportamento: `payment` só passou a publicar dois eventos.

### Negative
- O modelo de lançamentos pode ter de mudar após o parecer (LAC-38). Como o razão é imutável, uma mudança aplica-se a lançamentos novos; os antigos corrigem-se com estornos (ou, antes de dinheiro real, por *rollback* da V065 — ver cabeçalho).
- Encomendas anteriores à V065 não têm lançamentos (não há *backfill*); só entram quando um evento novo lhes tocar (ex.: reembolso), e nesse caso a captura/entrega são lançadas primeiro.
- Uma corrida de microssegundos entre a aprovação de um reembolso e a entrega da mesma encomenda pode classificar o reembolso do lado "errado" (ambos os lados fecham Σ = 0; só a repartição muda).
- Sem FKs do razão para `tenants`/`orders`/`vendors` (referências por valor, como `audit_logs`), para que o registo imutável nunca bloqueie nem seja bloqueado por limpezas de dados mestre.

## Rollback

V065/V066 só criam tabelas/colunas novas; os cabeçalhos das migrações têm o SQL de reversão (perde o razão — só aceitável antes de haver dinheiro real). Desligar os lançamentos sem reverter o esquema: `APP_SETTLEMENT_ENABLED=false` (os eventos continuam registados e concluídos; ao religar, os eventos seguintes lançam também os factos anteriores de que dependem).
