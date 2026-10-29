# Baseline de testes e build — INC-00

| Campo | Valor |
|---|---|
| Data | 2026-10-27 |
| Incremento | INC-00 — Baseline ([CLAUDE-CODE-PROMPTS.md](../refactoring/CLAUDE-CODE-PROMPTS.md)) |
| Base medida | `feat/spec-002-backoffice-catalog-sales` @ `54be398` (inclui a migração `V034__provisioning_idempotency.sql`), integrada por merge em `refactor/INC-00-baseline` |
| Base anterior (só referência) | `main` @ `7e93a8d` — backend medido primeiro aqui (ver 3.5); os resultados de frontend/Flutter foram medidos nesta base e não são afetados pelos 3 commits adicionais, que só tocam backend, realm Keycloak e openspec |
| Branch | `refactor/INC-00-baseline` |
| Código de produção alterado | Nenhum |

> Este documento regista o estado **real** observado ao executar cada suite numa cópia limpa
> do repositório (git worktree, sem ficheiros não versionados). Nenhuma falha foi corrigida neste
> incremento. Todos os números abaixo vêm de execuções efetivas.

## 1. Versões das ferramentas

| Ferramenta | Versão |
|---|---|
| Java (usado nos builds) | OpenJDK 21.0.8 (Microsoft) — `JAVA_HOME=/Users/xavier/Library/Java/JavaVirtualMachines/ms-21.0.8/Contents/Home` |
| Java (predefinido no `PATH`) | OpenJDK 25.0.4.1 — **não** usar; o projeto exige JDK 21 |
| Maven | 3.9.10 |
| Docker | Docker Desktop, Engine 29.6.2 (API 1.55, API mínima 1.40), Compose v5.3.1 |
| Node.js / npm | v24.17.0 / 11.13.0 |
| Flutter / Dart | 3.29.3 (stable) / 3.7.2 |
| Spring Boot (pom) | 3.3.5 — Testcontainers gerido pelo BOM |
| Next.js (backoffice) | 15.5.18 |

## 2. Resumo

| Suite | Comando | Resultado | Duração |
|---|---|---|---|
| Backend (**comando de referência**) | `mvn clean verify -DargLine=-Dapi.version=1.44` | **OK** — 221 testes, 0 falhas, 0 erros, 0 ignorados | 119 s |
| Backend (comando padrão) | `mvn clean verify` | BUILD SUCCESS **enganador** — 202 testes; 4 classes Testcontainers (19 testes) não executam, sem aviso | 53 s |
| Backoffice | `npm ci` | OK | 83 s |
| Backoffice | `npm run validate` | **FALHA** no passo `lint` (7 erros, 23 avisos); `typecheck` OK | 15 s |
| Backoffice | `npm run validate:screens` (isolado) | **FALHA** — `ENOENT src/features/screens/generated-screens.ts` | <5 s |
| Backoffice | `npm run build` | **FALHA** — compila, mas o lint do `next build` falha (5 erros) | 81 s |
| Web cliente | `npm ci` | OK | 23 s |
| Web cliente | `npm run typecheck` | OK | 12 s |
| Web cliente | `npm test` | OK — 2 ficheiros, 11 testes, 0 falhas | 6 s |
| Web cliente | `npm run build` | OK | 13 s |
| App cliente (Flutter) | `flutter pub get` | OK | 9 s |
| App cliente (Flutter) | `flutter analyze` | **FALHA** — 126 issues (75 erros, 51 info) | 17 s |
| App cliente (Flutter) | `flutter test` | **FALHA** — 8 passaram, 1 falhou (`widget_test.dart` não compila) | 20 s |
| App estafeta (Flutter) | `flutter pub get` | OK | 4 s |
| App estafeta (Flutter) | `flutter analyze` | **FALHA** — 140 issues (116 erros, 13 info) | 8 s |
| App estafeta (Flutter) | `flutter test` | **FALHA** — 1 passou, 2 falharam (`remote_data_test.dart` e `widget_test.dart` não compilam) | 13 s |
| Contrato OpenAPI | `GET /api-docs` | OK — guardado em [`docs/api/openapi-baseline.json`](../api/openapi-baseline.json) | — |

## 3. Backend (`backend/`)

Medido sobre `feat/spec-002-backoffice-catalog-sales` @ `54be398`, com 34 migrações (V001–V034,
incluindo a nova `V034__provisioning_idempotency.sql`).

### 3.1 Comandos

```bash
export JAVA_HOME=/Users/xavier/Library/Java/JavaVirtualMachines/ms-21.0.8/Contents/Home
# Comando de referência — OBRIGATÓRIO neste ambiente (ver 3.3):
cd backend && mvn clean verify -DargLine=-Dapi.version=1.44
# Comando padrão — registado como achado; NÃO serve de quality gate:
cd backend && mvn clean verify
```

### 3.2 Resultado

- **Com `-DargLine=-Dapi.version=1.44`:** `Tests run: 221, Failures: 0, Errors: 0, Skipped: 0` →
  **BUILD SUCCESS** (1 min 56 s). Inclui as classes Testcontainers:
  `MigrationValidationTest` (1 — as 34 migrações aplicam-se e o `ddl-auto: validate` passa),
  `ProvisioningPostgresIntegrationTest` (3), `SalesReportAggregationTest` (9),
  `SalesReportCsvExportTest` (6).
- **Sem o override (`mvn clean verify`):** `Tests run: 202, Failures: 0, Errors: 0, Skipped: 0` →
  BUILD SUCCESS, **mas** essas 4 classes reportam `Tests run: 0`: 19 testes, incluindo a
  validação de migrações, não correm e o build fica verde na mesma (verde falso).
- Não há falhas pré-existentes no backend nesta base.

### 3.3 Achado/bloqueio: Testcontainers vs Docker Engine 29

- O Testcontainers gerido pelo BOM do Spring Boot 3.3.5 falha a deteção do Docker:
  `Could not find a valid Docker environment … BadRequestException (Status 400 …)`. O cliente
  docker-java usa uma versão de API inferior à mínima aceite pelo Engine 29 (1.40).
- As classes Testcontainers protegem-se com `Assumptions`/`isDockerAvailable()`, por isso são
  **ignoradas em silêncio** (0 testes) em vez de falharem. O `mvn clean verify` padrão não valida
  migrações nem os testes de integração com PostgreSQL.
- **Contorno obrigatório** (sem alterar código): `-DargLine=-Dapi.version=1.44`. Os incrementos
  seguintes devem usar este comando como quality gate e confirmar que `MigrationValidationTest`
  reporta `Tests run: 1`.
- Correção duradoura (fora do âmbito do INC-00): atualizar Testcontainers (≥ 1.21.x) ou fixar
  `api.version` na configuração do Surefire / `~/.docker-java.properties`.

### 3.4 Falhas pré-existentes

Nenhuma nesta base.

### 3.5 Referência: medição anterior sobre `main` @ `7e93a8d`

O backend foi medido primeiro sobre `main` (`7e93a8d`, 33 migrações): 202 testes com o override
(186 sem ele), **3 falhas e 1 erro**:

| Teste | Sintoma | Causa |
|---|---|---|
| `dispatch.OperationsReassignmentTest` | 403 em vez de 200 | Teste (e `CourierService.isAdminOrOperations()`) usavam `ROLE_OPERATIONS`, que o realm não emite (SEC-14). |
| `admin.AdminAuthorizationTest` | contexto não carrega | Faltava `@MockBean AppUserProfileService` no slice. |
| `cart.CartSecurityApiTest` | 200 em vez de 403 | Slice com o serviço mockado; a regra de propriedade nunca era exercida. |
| `order.CheckoutSecurityApiTest` | 201 em vez de 403 | Idem; a verificação vive em `OrderService.createOrder`. |

Todas estão corrigidas pelos commits `3a060eb` e `6ddb4f5`, presentes na base atual. Um
incremento que parta de `main` herda estas falhas.

## 4. Backoffice (`pede-aqui-backoffice/`)

```bash
cd pede-aqui-backoffice && npm ci && npm run validate && npm run build
```

- `npm ci`: OK. O npm reporta 10 vulnerabilidades (2 moderadas, 7 altas, 1 crítica) — não analisadas.
- `npm run typecheck`: OK.
- `npm run lint` (`eslint . --max-warnings=0`): **7 erros, 23 avisos**. Erros:
  - `scripts/extract-strings.js` — 2× `@typescript-eslint/no-require-imports`;
  - `src/app/register/page.tsx:99,107` — 2× `no-explicit-any`;
  - `src/components/ui/label.tsx:6`, `src/components/ui/textarea.tsx:6` — 2× `no-empty-object-type`;
  - `src/lib/api/types.ts:312` — 1× `no-explicit-any`.
  Como o `validate` encadeia com `&&`, o passo `validate:screens` não chega a correr.
- `npm run validate:screens` (executado isoladamente): falha com `ENOENT` em
  `src/features/screens/generated-screens.ts` — ver 7.1.
- `npm run build`: o Next.js compila (`✓ Compiled successfully`), mas a fase de lint do build falha
  com 5 erros (os mesmos acima, excluindo `scripts/`). Aviso adicional: o Next infere a raiz do
  workspace a partir de `/Users/xavier/package-lock.json` (lockfile fora do repositório); definir
  `outputFileTracingRoot` resolve.
- Efeito lateral: `npm run typecheck` reescreve `tsconfig.tsbuildinfo`, que está versionado.

## 5. Web cliente (`pede-aqui-delivery/`)

```bash
cd pede-aqui-delivery && npm ci && npm run typecheck && npm test && npm run build
```

Tudo verde. `vitest`: 2 ficheiros, 11 testes, 0 falhas. O npm reporta 15 vulnerabilidades
(7 moderadas, 8 altas) — não analisadas.

## 6. Apps Flutter

```bash
cd pede_aqui_delivery_app && flutter pub get && flutter analyze && flutter test
cd pede_aqui_courier_app  && flutter pub get && flutter analyze && flutter test
```

| App | analyze | test |
|---|---|---|
| `pede_aqui_delivery_app` | 75 erros, 51 info | 8 ✓, 1 ✗ (`test/widget_test.dart` não compila) |
| `pede_aqui_courier_app` | 116 erros, 13 info | 1 ✓, 2 ✗ (`test/remote_data_test.dart`, `test/widget_test.dart` não compilam) |

Causa de praticamente todos os erros: ficheiros fonte ausentes do repositório (ver 7.1), por ex.
`lib/features/{cart,catalog,orders}/data/*_models.dart`, `*_repository.dart` (app cliente) e
`lib/data/models/*`, `lib/data/repositories/courier_repository.dart`,
`lib/data/datasources/{courier_data_source,mock_courier_data_source}.dart` (app estafeta). Os
erros em cascata (`Undefined class 'Vendor'`, `Type 'Delivery' not found`, …) derivam disso.
Os `info` são lints de estilo (`require_trailing_commas`, `prefer_const_constructors`).

## 7. Achados transversais

### 7.1 `.gitignore` da raiz exclui código-fonte (crítico para reprodutibilidade)

O `.gitignore` da raiz contém padrões não ancorados pensados para dados locais:

```text
data/      # linha 32 — "Local infrastructure"
screens/   # linha 50
```

Estes padrões excluem também diretórios de **código**:
`pede_aqui_delivery_app/lib/features/*/data/`, `pede_aqui_courier_app/lib/data/` e
`pede-aqui-backoffice/src/features/screens/`. Só alguns ficheiros foram adicionados à força
(`git add -f`); os restantes existem apenas na cópia de trabalho local do autor. Consequência:
**um clone limpo não compila as duas apps Flutter** e o `validate:screens` do backoffice falha.
Os resultados de Flutter acima refletem um clone limpo; na cópia local do autor podem ser verdes.
Correção recomendada (incremento próprio): ancorar os padrões (`/data/`, `/minio-data/`, …) e
versionar os ficheiros em falta.

### 7.2 Testes de segurança que não exercem a proteção

Ver 3.3: dois testes de propriedade (carrinho/checkout) usam slices com o serviço mockado e
nunca testaram a regra. Qualquer incremento de segurança deve escrever testes contra o serviço
real ou integração.

## 8. Contrato OpenAPI baseline

> Histórico. Desde LAC-56 o contrato vivo é `docs/api/openapi.json`, gerado por
> `OpenApiContractExportTest` e verificado em CI (ver [`docs/api/README.md`](../api/README.md)).

- Ficheiro: [`docs/api/openapi-baseline.json`](../api/openapi-baseline.json) (JSON normalizado com
  chaves ordenadas e indentação de 2 espaços, para diffs estáveis).
- Conteúdo: OpenAPI 3.0.1, 123 paths, 148 operações, 125 schemas — gerado sobre `54be398`
  (inclui o endpoint M2M de provisionamento de tenants).
- Como foi gerado (sem colidir com outros serviços nas portas 5432/8080/8081):

  ```bash
  # PostgreSQL via compose, projeto isolado, porta do host 15432 (override com `ports: !override`)
  docker compose -p inc00-baseline -f docker-compose.yml -f <override> up -d --wait postgres
  cd backend && mvn -DskipTests package
  DB_URL=jdbc:postgresql://localhost:15432/delivery SERVER_PORT=18080 \
    java -jar target/delivery-marketplace-0.0.1-SNAPSHOT.jar
  # GET http://localhost:18080/api-docs  → docs/api/openapi-baseline.json
  docker compose -p inc00-baseline ... down -v
  ```

- Keycloak, Redis e MinIO não foram necessários: `/api-docs` é público, o JWK é obtido de forma
  lazy e o Redis/S3 não são contactados no arranque. Flyway aplicou 34 migrações (até V034) numa base vazia.
- O campo `servers[0].url` contém `http://localhost:18080` (porta usada na geração) — ignorar ao
  comparar contratos.
- O backend **não** foi arrancado via serviço `backend` do compose porque esse serviço exige o
  ficheiro `.env` na raiz (não versionado) e publica a porta 8082.

## 9. Bloqueios, riscos e limitações

| Bloqueio / risco | Impacto | Comando para reproduzir/contornar |
|---|---|---|
| Testcontainers incompatível com Docker Engine 29 | `mvn clean verify` padrão salta em silêncio 19 testes (incl. validação de migrações) e fica verde | Usar sempre `mvn clean verify -DargLine=-Dapi.version=1.44` |
| **Risco:** `.gitignore` da raiz exclui código-fonte (`data/` e `screens/` não ancorados — ver 7.1) | Um clone limpo não compila as apps Flutter; `validate:screens` falha; ficheiros novos nesses diretórios ficam fora dos commits sem aviso | — (ancorar os padrões e versionar os ficheiros em falta, em incremento próprio) |
| `java` predefinido é JDK 25 | Builds devem exportar `JAVA_HOME` para JDK 21 | `export JAVA_HOME=…/ms-21.0.8/Contents/Home` |
| Sem `.env.local` nos frontends num clone limpo | Não afetou typecheck/test/build | — |

## 10. Pós-correção — CI verde (branch `fix/ci-green`, 2026-10-28)

Medido sobre `origin/main` @ `8d09b1c` + commits de `fix/ci-green`, com os comandos exatos do
`.github/workflows/ci.yml`. Node 20.20.2 / npm 10.9.9 (as versões do CI; o npm 11 local esconde a
falha do lockfile) e Flutter 3.29.3.

| Job | Causa raiz | Correção | Resultado local |
|---|---|---|---|
| backoffice | `--max-warnings=0`: 7 erros + 23 avisos de lint; `generated-screens.ts` e `src/app/screens/**` fora do Git | Lint corrigido (tipos, imports mortos, `ImageIcon`, deps do efeito, disables por linha justificados); ficheiros versionados | `npm ci && npm run validate && npm run build` → **OK** (32 ecrãs, build 57/57 páginas) |
| delivery-web | `package-lock.json` escrito pelo npm 11 sem o `esbuild@0.28.2` aninhado do vitest; o `npm ci` do npm 10 (Node 20) aborta com EUSAGE | Lockfile ressincronizado com `npm@10 install --package-lock-only` (só adições) | `npm ci && npm run typecheck && npm test && npm run build` → **OK** (4 ficheiros, 22 testes) |
| delivery-app | Fontes `lib/features/*/data/` ignoradas pelo `.gitignore`; CI em Flutter `stable` flutuante (3.47.8) exige `intl ^0.20.3` → `pub get` falha; 47 infos fatais no `analyze` | `.gitignore` ancorado + fontes versionadas; Flutter fixado em 3.29.3 no CI; `dart fix --apply` | `flutter pub get && flutter analyze && flutter test` → **OK** (0 issues, 9 testes) |
| courier-app | Fontes `lib/data/` e `lib/presentation/screens/` ignoradas; infos (`withOpacity` deprecado, `const`) | Idem + `withOpacity(x)` → `withValues(alpha: x)` | `flutter pub get && flutter analyze && flutter test` → **OK** (0 issues, 9 testes) |
| backend | **Novo em `main`** (fora do âmbito, não corrigido): o merge `5f8095c` perdeu o parâmetro/atribuição `deliveryRetirementService` do construtor de `OrderService` (introduzido em `380c1cc`) | — | `mvn compile` → **FALHA** `OrderService.java:[132,5] variable deliveryRetirementService might not have been initialized` |

`.gitignore`: `data/` → `/data/` e `screens/` → `/screens/` (só a raiz: volumes locais e a pasta de
referências de design). Ficaram versionados 62 ficheiros antes só existentes na cópia local do autor.
