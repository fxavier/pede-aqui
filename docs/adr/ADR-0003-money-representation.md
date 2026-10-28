# ADR-0003 — Representação monetária

| Campo | Valor |
|---|---|
| Status | **Proposed** |
| Date | 2026-10-28 |
| Decisores | Xavier Francisco (owner técnico) |
| Achados relacionados | D3, AS-23, AS-14 ([AS-IS-ARCHITECTURE.md §6, §13, §16](../assessment/AS-IS-ARCHITECTURE.md)) |
| Relacionado | INC-13 (motor de pricing); futuro ADR financeiro (ledger/liquidações) |

## Context

Estado verificado em `origin/main` `a915731`:

| Tema | Facto | Evidência |
|---|---|---|
| Colunas monetárias | Todas `NUMERIC(12,2)` (preços, subtotais, taxas, descontos, totais, pagamentos, reembolsos, comissões, numerário) | `V003__customer_order_flow.sql` (17 colunas: `price`, `subtotal`, `fees`, `taxes`, `discounts`, `total`, `unit_price_snapshot`, `line_total`, `amount`, `cash_collected_amount`…), `V008`, `V010`, `V013`, `V015`, `V016`, `V028`–`V031` |
| Taxas/percentagens | `NUMERIC(6,4)` / `NUMERIC(8,4)` (`commission_rate`, `tax_rate`) | `V008__finance.sql`, `V010__admin_ops_audit.sql` |
| Floats em colunas monetárias | **Nenhum.** `DOUBLE PRECISION` só em coordenadas e `accuracy_meters` | `V017`, `V006` |
| Java | `BigDecimal` em entidades, DTOs e serviços de dinheiro; arredondamento `RoundingMode.HALF_UP` (11 ocorrências, todas `HALF_UP`) | `cart/service/PricingService.java`, `common/pricing/PricingMath.java`, `report/mapper/SalesReportMapper.java` |
| Moeda | Uma por tenant (`tenants.default_currency VARCHAR(3)`); **sem** coluna de moeda por valor | `V002__tenants_and_users.sql` |
| **Exceção 1 — dinheiro em `double`** | Taxa de entrega exibida na pesquisa calculada em `double`: `BASE_DELIVERY_FEE = 2.50`, `PER_KM_FEE = 0.50`, `deliveryFee = BASE + distanceKm * PER_KM`, arredondada com `Math.round(x * 100.0) / 100.0`; exposta como `Double deliveryFee` | `geo/service/SearchService.java` (linhas 24–25, 114, 123), `geo/dto/SearchVendorResponse.java` (linha 14) |
| Divergência pesquisa vs checkout | A pesquisa mostra `2.50 + 0.50/km` (double); o checkout cobra `DELIVERY_FEE = 5.00` fixo (BigDecimal); nenhum lê `fee_policies`/`delivery_fee_rules` | `SearchService.java` vs `cart/service/PricingService.java` (linhas 12–22) — AS-23 |
| Exceção 2 — percentagem em `double` | `sharePercent` dos relatórios é calculado em `BigDecimal` e convertido para `double` só na resposta (`.doubleValue()`); **não é dinheiro** | `report/mapper/SalesReportMapper.java` (linha 99), `report/dto/DimensionRowResponse.java`, `ProductDimensionRowResponse.java` |
| Clientes | JSON numérico → `number` em TypeScript; a web cliente soma `unitPrice * quantity` em `number` para o subtotal do carrinho na UI | `pede-aqui-delivery/src/lib/api/types.ts`, `pede-aqui-delivery/src/store/cart-slice.ts` (linha 78) |
| Formatação | Web: `formatMZN` (`pt-MZ`, sufixo "MT"); backoffice: `Intl.NumberFormat("pt-PT", { currency: "MZN" })` | `pede-aqui-delivery/src/lib/utils.ts`, `pede-aqui-backoffice/src/lib/utils.ts` |
| Ledger | Inexistente | AS-IS §6 |

## Problem

Que representação monetária adotar daqui em diante — e, em particular, vale a pena migrar para inteiros em unidades mínimas (centavos) antes de construir PSP, ledger e liquidações? E como eliminar os pontos onde dinheiro é calculado em vírgula flutuante?

## Decision Drivers

1. **Exatidão:** nenhum valor cobrado, reembolsado ou liquidado pode depender de aritmética binária de vírgula flutuante.
2. **Custo de migração:** 30+ colunas, entidades, DTOs, *snapshots* de encomendas e relatórios (índices de `V032`) assentam em `NUMERIC(12,2)`.
3. **Contrato de API estável** para backoffice, web e apps Flutter.
4. **Requisitos futuros de ledger/PSP** (M-Pesa, e-Mola): integrações que costumam trocar valores em unidades mínimas e exigir somas exatas e auditáveis.
5. Uma única moeda operacional hoje (MZN, 2 casas decimais).

## Considered Alternatives

| Alternativa | Prós | Contras |
|---|---|---|
| **A. Manter `NUMERIC(12,2)` + `BigDecimal` nas tabelas existentes (recomendada)** | Já é exato; zero migração; SQL de relatórios legível; `BigDecimal` com escala fixa é idiomático em JPA | Exige disciplina de escala/arredondamento (centralizar); `BigDecimal.equals` sensível à escala |
| **B. Migrar tudo para `BIGINT` em unidades mínimas** | Aritmética inteira trivial; alinhado com PSPs | Migração de ~30 colunas + código + contratos de API + relatórios; risco alto, benefício marginal porque A já é exato |
| **C. Tipo `Money` (JSR 354 / Moneta)** | Moeda + valor juntos | Dependência e mapeamento JPA extra; uma só moeda hoje; não resolve nada que A não resolva |
| **D. Status quo** (inclui `double` na pesquisa) | — | Mantém o valor exibido ≠ valor cobrado e um padrão inseguro para copiar |

## Decision

**Proposta:**

1. **Tabelas existentes:** manter `NUMERIC(12,2)` + `BigDecimal` (escala 2) para valores e `NUMERIC(p,4)` para taxas/percentagens. Não migrar para `BIGINT`.
2. **Regras de código:**
   - Dinheiro **nunca** em `double`/`float`/`Double` (Java) — nem em cálculo, nem em DTO de resposta.
   - Arredondamento centralizado em `common/pricing/PricingMath` (escala 2, `RoundingMode.HALF_UP`, já usado em todas as 11 ocorrências); arredondar **por linha** e somar valores já arredondados; comparar com `compareTo`, nunca `equals`.
   - Moeda continua por tenant (`tenants.default_currency`); novas tabelas financeiras (pagamentos PSP, ledger) guardam a moeda explicitamente por linha.
3. **Eliminar o `double` em `SearchService`:** `BASE_DELIVERY_FEE`/`PER_KM_FEE` e `deliveryFee` passam a `BigDecimal`; `SearchVendorResponse.deliveryFee` passa a `BigDecimal` (o JSON continua numérico — compatível). No INC-13 o valor passa a vir do **mesmo** serviço de pricing que o checkout usa (`fee_policies`/`delivery_fee_rules`), eliminando a divergência pesquisa ≠ checkout. A distância (`distanceKm`) pode continuar `double` — não é dinheiro — mas é convertida para `BigDecimal` antes de multiplicar por uma tarifa.
4. **`sharePercent` (relatórios)** pode continuar `double` na resposta: é uma percentagem de apresentação, calculada em `BigDecimal` com 2 casas antes da conversão.
5. **Clientes:** o valor autoritativo é sempre o do backend (`GET /cart/pricing`, resposta do checkout). Somas no cliente (ex.: `cart-slice.ts`) são apenas indicativas e não são enviadas ao backend como valor a cobrar.
6. **`BIGINT` em unidades mínimas** só será adotado no **futuro ledger**, e só se o ADR financeiro o justificar (ex.: requisitos do PSP ou de reconciliação). A conversão faz-se numa fronteira única (adaptador PSP / ledger), com teste de ida-e-volta.

## Rationale

`NUMERIC(12,2)` com `BigDecimal` já é uma representação decimal exata; o problema real do projeto não é o tipo das colunas mas dois pontos isolados de `double` e a ausência de uma fonte única de pricing (AS-23). Migrar para `BIGINT` agora custaria uma migração transversal (dados, *snapshots*, relatórios, contratos de API e três clientes) para obter uma exatidão que já existe. Deixar a decisão sobre unidades mínimas para o ledger permite tomá-la com os requisitos reais do PSP em mãos, numa tabela nova, sem tocar no que funciona.

## Consequences

### Positive
- Sem migração de dados nem alteração de contrato (o JSON continua numérico).
- O valor de entrega exibido na pesquisa passa a ser exato e, após INC-13, igual ao cobrado.
- Regra simples e verificável ("sem `double` em dinheiro") para humanos e agentes.

### Negative
- `BigDecimal` exige disciplina (escala, `compareTo`); erros de escala são possíveis sem testes.
- Se o ledger adotar `BIGINT`, coexistirão duas representações, com uma fronteira de conversão a manter.
- Os clientes TypeScript/Dart continuam a receber `number`/`double`; aceitável apenas porque não calculam valores autoritativos.

## Risks and Mitigations

| Risco | Mitigação |
|---|---|
| Reintrodução de `double` em dinheiro | Regra ArchUnit/grep em CI: nenhum campo/método com nome `*fee*|*price*|*amount*|*total*|*discount*` do tipo `double`/`Double`/`float` em `com.delivery` |
| Escalas inconsistentes (`5` vs `5.00`) em comparações | `PricingMath` como único ponto de arredondamento; testes com `compareTo`; `setScale(2)` antes de persistir |
| Mudança de `Double` → `BigDecimal` no JSON da pesquisa altera a forma (`3.5` vs `3.50`) | Ambos são números JSON válidos; os clientes usam `number`/formatação; validar com o contrato OpenAPI baseline (`docs/api/openapi-baseline.json`) |
| PSP exige unidades mínimas antes do ledger | Conversão no adaptador PSP (`amount.movePointRight(2).longValueExact()`), com teste |
| Valores > 9 999 999 999,99 | Fora do domínio realista para MZN numa encomenda; o ledger pode usar precisão maior |

## Migration Implications

- **Nenhuma migração de base de dados** para as tabelas existentes.
- Alteração de código pequena e isolada: `SearchService` e `SearchVendorResponse` (`double` → `BigDecimal`), com teste de caracterização do valor atual antes da mudança; idealmente dentro do INC-13 (pricing), que substitui as constantes por `fee_policies`/`delivery_fee_rules`.
- Atualizar o tipo no OpenAPI (continua `number`) e verificar os consumidores (`pede-aqui-delivery/src/lib/api/types.ts` — `deliveryFee: number | null` mantém-se).
- O futuro ADR financeiro decide a representação do ledger.

## Validation Criteria

- `grep -rnE '\b(double|Double|float)\b' backend/src/main/java | grep -iE 'fee|price|amount|total|discount|commission'` sem resultados (excluindo `sharePercent`, que não é dinheiro).
- Teste: o `deliveryFee` devolvido pela pesquisa para um vendor/distância é igual ao `fees` de entrega cobrado no checkout para o mesmo cenário (após INC-13).
- Testes de `PricingMath` cobrem arredondamento `HALF_UP` por linha e somas.
- `MigrationValidationTest` continua verde (sem migrações novas por causa deste ADR).

## References

- [AS-IS-ARCHITECTURE.md §6 (modelo de dados), §13 (AS-23), §16 (D3)](../assessment/AS-IS-ARCHITECTURE.md)
- [FEATURE-INVENTORY.md](../assessment/FEATURE-INVENTORY.md) (FR-014, FR-021, FR-063)
- `docs/refactoring/CLAUDE-CODE-PROMPTS.md` — INC-10, INC-13
- Código: `geo/service/SearchService.java`, `geo/dto/SearchVendorResponse.java`, `cart/service/PricingService.java`, `common/pricing/PricingMath.java`, `report/mapper/SalesReportMapper.java`
- [PostgreSQL — Numeric Types](https://www.postgresql.org/docs/current/datatype-numeric.html) · [`java.math.BigDecimal`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/math/BigDecimal.html)
