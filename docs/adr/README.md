# Architecture Decision Records (ADR)

Registo das decisões arquiteturais do Pede-Aqui (pt-PT). Cada ADR segue o mesmo modelo: Status, Date, Context, Problem, Decision Drivers, Considered Alternatives, Decision, Rationale, Consequences (Positive/Negative), Risks and Mitigations, Migration Implications, Validation Criteria, References.

## Índice

| ADR | Título | Status | Data | Bloqueia |
|---|---|---|---|---|
| [ADR-0001](./ADR-0001-tenancy-model.md) | Modelo de tenancy — tenant = operador do marketplace | Accepted (2026-10-28) | 2026-10-28 | INC-11 e restante Fase 2 |
| [ADR-0002](./ADR-0002-module-structure.md) | Estrutura de módulos — camadas por domínio + Spring Modulith só para verificação e eventos transacionais | Accepted (2026-10-28) | 2026-10-28 | Incremento "Modulith baseline" (cláusula "Architecture Constraints" do `CLAUDE.md` já atualizada) |
| [ADR-0003](./ADR-0003-money-representation.md) | Representação monetária — `NUMERIC(12,2)` + `BigDecimal`; sem `double` em dinheiro | Accepted (2026-10-28) | 2026-10-28 | INC-13 (parte do `SearchService`) |
| [ADR-0004](./ADR-0004-phone-otp.md) | Login de clientes por telemóvel + SMS — autenticador SPI no Keycloak (rejeita OTP no backend + impersonation) | Proposed | 2026-10-29 | W5-D (LAC-18) |

## Ciclo de vida

- **Proposed** → em revisão; não pode ser implementado.
- **Accepted** → aprovado pelo owner técnico; os incrementos da Fase 2 que dependem dele podem avançar (ver `docs/refactoring/CLAUDE-CODE-PROMPTS.md`).
- **Rejected** / **Superseded by ADR-NNNN** → mantém-se no repositório como histórico; não se apaga.

Para alterar uma decisão aceite, cria-se um ADR novo que a substitui, em vez de editar o original (exceto correções de redação ou mudança de Status).

## Convenções

- Nome do ficheiro: `ADR-NNNN-<slug>.md`, numeração sequencial.
- As afirmações sobre o estado atual citam ficheiros do código; os IDs `AS-xx`, `SEC-xx`, `COR-xx` e `Dx` referem-se a `docs/assessment/AS-IS-ARCHITECTURE.md`, `docs/assessment/FEATURE-INVENTORY.md` e `docs/history/AUDIT.md`.
