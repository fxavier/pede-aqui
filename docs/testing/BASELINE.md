# Baseline de testes e build — INC-00

| Campo | Valor |
|---|---|
| Data | 2026-10-27 |
| Incremento | INC-00 — Baseline ([CLAUDE-CODE-PROMPTS.md](../refactoring/CLAUDE-CODE-PROMPTS.md)) |
| Commit medido | `7e93a8d` (merge do PR #1, `feat/spec-002-backoffice-catalog-sales` → `main`) |
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
| Backend | `mvn clean verify` | **FALHA** — 186 testes, 3 falhas, 1 erro; 3 classes Testcontainers com 0 testes executados | 51 s |
| Backend (Docker API forçada) | `mvn clean verify -DargLine=-Dapi.version=1.44` | **FALHA** — 202 testes, 3 falhas, 1 erro | 77 s |
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

### 3.1 Comandos

```bash
export JAVA_HOME=/Users/xavier/Library/Java/JavaVirtualMachines/ms-21.0.8/Contents/Home
cd backend && mvn clean verify
# Re-execução com Testcontainers funcional (ver 3.3):
cd backend && mvn clean verify -DargLine=-Dapi.version=1.44
```

### 3.2 Resultado

- Execução padrão: `Tests run: 186, Failures: 3, Errors: 1, Skipped: 0` → BUILD FAILURE.
- Com `-Dapi.version=1.44`: `Tests run: 202, Failures: 3, Errors: 1, Skipped: 0` → BUILD FAILURE.
  Os 16 testes adicionais são os das classes Testcontainers (todos passaram):
  `MigrationValidationTest` (1 — as migrações V001…V033 aplicam-se e o `ddl-auto: validate` passa),
  `SalesReportAggregationTest` (9), `SalesReportCsvExportTest` (6).
- As 4 falhas são idênticas nas duas execuções.

### 3.3 Falhas pré-existentes e causa provável

| Teste | Sintoma | Causa provável |
|---|---|---|
| `dispatch.OperationsReassignmentTest.opsCanReassignAndNonOpsCannot` | esperado 200, obtido 403 | O teste autentica com `ROLE_OPERATIONS`, papel que o realm nunca emite (o realm usa `OPS`). Associado: `CourierService.isAdminOrOperations()` compara com `ROLE_OPERATIONS`, pelo que utilizadores `OPS` reais são negados (SEC-14). |
| `admin.AdminAuthorizationTest.rejectsNonAdminRoles` | `IllegalStateException: Failed to load ApplicationContext` | Slice `@WebMvcTest` sem `@MockBean AppUserProfileService`; o construtor de `AdminController` exige esse bean. Defeito do teste, não de produção. |
| `cart.CartSecurityApiTest.preventsCustomerFromAddingToAnotherCustomersCart` | esperado 403, obtido 200 | Slice `@WebMvcTest` com o serviço mockado: o controlador devolve o sucesso por omissão e a asserção de propriedade nunca é exercida. O `addItem` é auto-scoped ao JWT (o `{customerId}` do path é inerte). Defeito do teste. |
| `order.CheckoutSecurityApiTest.preventsCustomerFromCheckingOutAnotherCustomersCart` | esperado 403, obtido 201 | Mesmo padrão: a verificação de propriedade do carrinho vive em `OrderService.createOrder`, que está mockado no slice. Defeito do teste. |

Nota: estas quatro falhas já têm correção no branch `feat/spec-002-backoffice-catalog-sales`
(commits `3a060eb` e `6ddb4f5`), que **não** fazem parte do commit medido (`7e93a8d`). Um
incremento que parta de `main` herda estas falhas; um que parta do branch de feature não.

### 3.4 Bloqueio: Testcontainers vs Docker Engine 29

- Na execução padrão, o Testcontainers do BOM do Spring Boot 3.3.5 falha a deteção do Docker:
  `Could not find a valid Docker environment … BadRequestException (Status 400 …)`. O cliente
  docker-java negocia uma versão de API inferior à mínima aceite pelo Engine 29 (1.40).
- As classes protegidas por `Assumptions`/`isDockerAvailable()` reportam **0 testes executados**
  em vez de falharem — ou seja, o `mvn clean verify` padrão **não valida as migrações** neste
  ambiente, silenciosamente.
- Contorno verificado (sem alterar código): `-DargLine=-Dapi.version=1.44`. Correção duradoura
  (fora do âmbito do INC-00): atualizar Testcontainers (≥ 1.21.x) ou fixar `api.version` na
  configuração do Surefire / `~/.docker-java.properties`.

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

- Ficheiro: [`docs/api/openapi-baseline.json`](../api/openapi-baseline.json) (JSON normalizado com
  chaves ordenadas e indentação de 2 espaços, para diffs estáveis).
- Conteúdo: OpenAPI 3.0.1, 122 paths, 147 operações, 123 schemas.
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
  lazy e o Redis/S3 não são contactados no arranque. Flyway aplicou 33 migrações numa base vazia.
- O campo `servers[0].url` contém `http://localhost:18080` (porta usada na geração) — ignorar ao
  comparar contratos.
- O backend **não** foi arrancado via serviço `backend` do compose porque esse serviço exige o
  ficheiro `.env` na raiz (não versionado) e publica a porta 8082.

## 9. Bloqueios e limitações

| Bloqueio | Impacto | Comando para reproduzir/contornar |
|---|---|---|
| Testcontainers incompatível com Docker Engine 29 | `mvn clean verify` padrão salta silenciosamente 16 testes (incl. validação de migrações) | `mvn clean verify -DargLine=-Dapi.version=1.44` |
| Ficheiros fonte ignorados pelo `.gitignore` | Apps Flutter e `validate:screens` não verificáveis a partir do git | — (requer versionar os ficheiros) |
| `java` predefinido é JDK 25 | Builds devem exportar `JAVA_HOME` para JDK 21 | `export JAVA_HOME=…/ms-21.0.8/Contents/Home` |
| Sem `.env.local` nos frontends num clone limpo | Não afetou typecheck/test/build | — |
