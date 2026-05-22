# [FASE 1] Autenticação real com Keycloak — Todas as apps

## Objectivo

Substituir a autenticação mock por autenticação real via Keycloak JWT em todas as três aplicações.

**Specs de referência**: spec:9dbc5b64-42c8-4468-94c6-81606f0dc063/`[spec-overview]`, spec:9dbc5b64-42c8-4468-94c6-81606f0dc063/`[spec-delivery]`, spec:9dbc5b64-42c8-4468-94c6-81606f0dc063/`[spec-courier]`

## App Cliente (`pede_aqui_delivery_app`)

**Ficheiros**: file:pede_aqui_delivery_app/lib/features/auth/

### Trabalho necessário

1. **`ApiAuthRepository`** — implementar `login()` e `register()`:
  - `login`: `POST /keycloak/token` com `grant_type=password`
  - `register`: `POST /api/v1/auth/register` (endpoint a criar no backend — ver lacuna)
  - Guardar `access_token` e `refresh_token` em `flutter_secure_storage`
2. **Interceptor de refresh** — adicionar ao `ApiClient` para renovar token expirado automaticamente
3. **Verificação de sessão ao arrancar** — `GET /api/v1/me` no `main.dart` antes de navegar
4. **Remover credenciais hardcoded** do `LoginRegisterScreen`
5. **Validação de formulário**: email válido, palavra-passe ≥ 6 caracteres, nome obrigatório no registo
6. **Mensagens de erro em PT**: "Credenciais inválidas.", "Não foi possível criar conta.", "Sem ligação à internet."
7. **Activar** `USE_MOCK_DATA=false` no `service_locator.dart`

### Lacuna de backend

`POST /api/v1/auth/register` não existe. Proposta de contrato:

```
POST /api/v1/auth/register
Body: { name, email, password }
Response: { userId, email, name }
```

Se não for implementado, manter registo via Keycloak Admin API ou desactivar o tab "Registar" temporariamente.

## App Estafeta (`pede_aqui_courier_app`)

**Ficheiros**: file:pede_aqui_courier_app/lib/presentation/screens/login_screen.dart

### Trabalho necessário

1. **Criar ****`AuthRepository`** (abstracto + implementação API + mock)
2. **Criar ****`AuthCubit`** com estados `loading`, `authenticated`, `error`
3. **`LoginScreen`**: ligar ao `AuthCubit`, validar telefone `+258 XX XXX XXXX`
4. **Verificar papel ****`COURIER`** após login — bloquear se papel diferente
5. **Criar ****`InjectionContainer`** com GetIt para toda a app
6. **Guardar token** em `flutter_secure_storage`

## Backoffice (`pede-aqui-backoffice`)

**Ficheiros**: file:pede-aqui-backoffice/src/

### Trabalho necessário

1. **Instalar e configurar NextAuth.js** com provider Keycloak
2. **Middleware de protecção de rotas** por papel (`ADMIN`, `VENDOR_ADMIN`, `FINANCE`, `SUPPORT`, `OPS`)
3. **Injectar token JWT** em todas as chamadas `httpClient`
4. **Página de login** ou redireccionamento para Keycloak
5. **Página 403 Forbidden** com mensagem em PT

## Critérios de Aceitação

Login com credenciais válidas → navega para ecrã principalLogin com credenciais inválidas → mensagem de erro em PTToken guardado de forma segura (não em texto simples)Token expirado → refresh automático sem logoutSem token → redireccionamento para loginPapel errado (ex: VENDOR no app estafeta) → mensagem de erro claraLogout limpa token e navega para login