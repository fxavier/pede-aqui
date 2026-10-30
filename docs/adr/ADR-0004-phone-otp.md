# ADR-0004 — Login de clientes por telemóvel + código SMS (OTP)

| Campo | Valor |
|---|---|
| Status | **Proposed** |
| Date | 2026-10-29 |
| Decisores | Xavier Francisco (owner técnico) |
| Achados relacionados | LAC-18 (FR-001) em `docs/gaps/REGISTO-LACUNAS.md`; LAC-09/LAC-10 (realm, PKCE); LAC-11 (rate limiting) |
| Relacionado | ADR-0001 (tenant do cliente derivado do cliente OIDC), W4-C/W4-D/W4-E (PKCE em todos os clientes) |
| Pacote de trabalho | W5-D |

## Context

- Desde a vaga 4 **todos** os clientes fazem Authorization Code + PKCE contra o Keycloak 26.8.1 (`pede-aqui-web`, `delivery-app` via BFF, `pede-aqui-mobile`, `pede-aqui-courier-mobile`); nenhum vê a palavra-passe e o ROPC só existe num cliente de scripts de dev. As credenciais são introduzidas apenas nas páginas do Keycloak.
- O fluxo de browser do realm é `browser-privileged-mfa`: utilizador/palavra-passe + TOTP obrigatório para `ADMIN`/`FINANCE`/`OPS`.
- O cliente regista-se no backend (`POST /customers/register` → `KeycloakAdminService.createCustomer`, email + palavra-passe). Não há autenticação por telemóvel (LAC-18): em Moçambique o telemóvel é o identificador natural do cliente final; muitos não usam email.
- O backend identifica o cliente pelo `sub` do JWT e o tenant pelo `azp` (ADR-0001). O perfil (`app_user_profiles`) tem um `phone` de texto livre, não verificado.
- Ainda não há fornecedor de SMS escolhido (agregador local vs AWS End User Messaging; cobertura Vodacom/Movitel/Tmcel e Sender ID por validar).

## Problem

Como permitir que um cliente entre (e se registe implicitamente) só com o número de telemóvel e um código SMS, mantendo o Keycloak como fonte única de identidade, sem enfraquecer o login dos papéis privilegiados, com proteção contra força bruta e abuso de SMS, e sem depender já de um fornecedor de SMS concreto?

## Decision Drivers

1. **Uma só fonte de identidade e de sessões** (refresh rotativo, SSO, logout, brute-force) — o Keycloak.
2. **PKCE intacto**: as apps já fazem login por redirect; o novo método deve aparecer na mesma página, sem código novo nas apps.
3. **Nada de privilégio por SMS**: SMS é um fator fraco (SIM swap); staff/privilegiados ficam com palavra-passe + TOTP.
4. **Anti-abuso**: código com TTL curto, tentativas limitadas, cooldown de reenvio, quotas por número e por IP, bloqueio progressivo; o código nunca em logs.
5. **Fornecedor de SMS trocável** sem mexer no autenticador (decisão do utilizador: interface `SmsSender` + stub).
6. Restrições do repositório: backend em camadas, sem novos padrões (ADR-0002); `pom.xml` do backend partilhado.

## Considered Alternatives

### (a) Autenticador SPI do Keycloak (escolhida)

Um provider Java (`keycloak/extensions/sms-otp`) com um `Authenticator` no fluxo de browser dos clientes de cliente: ecrã do número → SMS → ecrã do código → utilizador encontrado/criado → sessão Keycloak normal → código de autorização PKCE para a app.

- Prós: tokens, refresh, SSO, logout e eventos continuam no Keycloak; zero alterações nas apps (é só mais um ecrã na página de login); o fluxo é ligado **por cliente** (override), por isso o backoffice e o estafeta ficam como estão.
- Contras: código Java a manter contra a SPI do Keycloak (atualizações de versão), build/imagem própria do Keycloak, templates FreeMarker.

### (b) OTP no backend + emissão de token por token-exchange / impersonation

`POST /api/v1/auth/otp/request` e `/verify` no backend; após verificar, o backend obtém um token do Keycloak para esse utilizador via token-exchange (impersonation "naked") com um cliente confidencial.

- Contras decisivos:
  - **Poder de impersonation**: o cliente do backend passaria a poder emitir tokens para *qualquer* utilizador do realm, incluindo ADMIN; um bug ou fuga desse segredo é tomada total da plataforma. Restringir por política de token-exchange é frágil e é uma *feature* preview/legada no Keycloak 26.
  - **Duplica a identidade**: sessões, refresh, logout e brute-force passam a ter dois donos; as apps teriam um segundo fluxo de login fora do PKCE (o que o LAC-10 acabou de eliminar).
  - Já rejeitada no registo de lacunas (LAC-18: "duplica a gestão de identidade e complica a renovação de sessão").

### (c) Keycloak "SMS 2FA" de terceiros (extensões da comunidade)

- Contras: qualidade/manutenção incerta, acoplamento a um gateway concreto, regras de bloqueio que não controlamos. Rejeitada.

## Decision

**Opção (a).** Implementação nesta PR (W5-D):

1. **Provider** `keycloak/extensions/sms-otp` (Maven autónomo, Keycloak SPI 26.8.1 em `provided`, sem dependências empacotadas), autenticador **`pede-aqui-phone-otp`**:
   - **Número**: normalizado em E.164; região por omissão Moçambique (+258); números +258 têm de ser móveis (9 dígitos, prefixos 82–87); outros indicativos só se estiverem em `allowedCountryCodes` (por omissão só `258`).
   - **Código**: 6 dígitos (`SecureRandom`), **TTL 5 min**, guardado só como **HMAC-SHA256(segredo, challengeId | número | código)** nas notas da sessão de autenticação; comparação em tempo constante.
   - **Tentativas**: **5 códigos errados por número** (contados no store partilhado, não por sessão — reiniciar o login não dá tentativas novas) → número bloqueado; bloqueio **progressivo** 15 min × 2^(bloqueios nas últimas 24 h), máximo 24 h; 20 códigos errados por IP / 15 min → IP bloqueado (mesma progressão).
   - **Reenvio**: cooldown **60 s** por número; **3 SMS por número** e **10 SMS por IP** em janela deslizante de 15 min. Pedidos recusados não consomem cooldown nem quota do número.
   - **Store**: `SingleUseObjectProvider` do Keycloak (cache replicada entre nós, com expiração); as chaves são HMAC do número/IP — o cache não guarda números nem IPs.
   - **Utilizador**: procurado pelo atributo `phone_e164`; mais de um → recusa; nenhum → criado com username opaco `phone-<uuid>`, `phone_e164`, `phone_verified=true` e papel `CUSTOMER`. Utilizadores com qualquer papel de `blockedRoles` (`ADMIN,OPS,FINANCE,SUPPORT,VENDOR_ADMIN,VENDOR_STAFF,COURIER`), sem `CUSTOMER`, desativados ou bloqueados pelo brute-force do realm são recusados com a mesma mensagem genérica. No primeiro login o Keycloak pede nome/apelido (VERIFY_PROFILE).
   - **"Entrar com email e palavra-passe"** e um `login_hint` que não seja telefone (ex.: email após o registo) devolvem ATTEMPTED → o fluxo passa à alternativa palavra-passe.
   - **SMS**: SPI própria `sms-sender` (`SmsSenderProvider.send(SmsMessage)`; a mensagem traz `text` e `redactedText`). Stub **`log`**: escreve número mascarado (`+25884*****67`) e o texto com o código mascarado; só mostra o código com `--spi-sms-sender--log--reveal-code-in-dev-mode=true` **e** Keycloak em `start-dev` (em modo produção a opção é ignorada com WARN). Texto pt-PT (configurável): *"Pede Aqui: o seu código de acesso é {code}. É válido durante {minutes} minutos. Não o partilhe com ninguém."*
   - **Segredo HMAC**: `--spi-authenticator--pede-aqui-phone-otp--hmac-secret` (env `KC_SPI_AUTHENTICATOR__PEDE_AQUI_PHONE_OTP__HMAC_SECRET`, ≥ 32 caracteres, igual em todos os nós). Sem ele o Keycloak **não arranca em modo produção**; em dev usa um segredo aleatório por arranque.
2. **Realm** (`keycloak/delivery-realm.json`):
   - fluxo `browser-customer-phone` (cookie → IdP redirector → [telefone **ou** palavra-passe + as mesmas subflows TOTP condicionais de `browser-privileged-mfa`]), com config `customer-phone-otp` (valores acima, explícitos);
   - ligado **só** a `pede-aqui-web` e `pede-aqui-mobile` (`authenticationFlowBindingOverrides.browser`); `delivery-app` e `pede-aqui-courier-mobile` continuam no fluxo do realm;
   - user profile: atributos `phone_e164` (validação E.164) e `phone_verified`, visíveis ao utilizador, **editáveis só por admin**; `email` deixa de ser obrigatório (clientes só com telefone). O registo no backend continua a exigir email;
   - mappers nos dois clientes: `phone_number` (string) e `phone_number_verified` (boolean), claims OIDC standard.
3. **Imagem Keycloak**: `keycloak/Dockerfile` (multi-stage: Maven constrói o jar → `quay.io/keycloak/keycloak:26.8.1` + `/opt/keycloak/providers`); o `docker-compose.yml` constrói-a.
4. **Backend**: `V063` acrescenta a `app_user_profiles` `phone_e164` (E.164, `CHECK`, **único por tenant**, nullable) e `phone_verified_at`. O `TenantRequestFilter` (já chamado em cada pedido de cliente) passa as claims a `CustomerProfileProvisioningService.syncVerifiedPhone`: só `phone_number_verified=true` + E.164 válido; número já ligado a outro perfil do tenant → WARN mascarado, pedido segue. `GET /me` expõe `phoneE164` e `phoneVerifiedAt`. O backend **não** gera nem valida códigos.

## Rationale

O Keycloak já é dono de sessões, rotação de refresh tokens, SSO e logout; pôr o OTP lá dentro mantém uma só superfície de autenticação e não acrescenta nenhum cliente com poder de emitir tokens. O override por cliente isola o método nos dois clientes de cliente e mantém TOTP para os privilegiados mesmo quando entram por esses clientes com palavra-passe. A interface `sms-sender` como SPI deixa o fornecedor real ser um jar separado, escolhido por configuração.

## Consequences

### Positive

- Login/registo por telemóvel na web e na app sem alterar código das apps (o ecrã aparece na página do Keycloak já usada por PKCE).
- O backend passa a ter o telemóvel verificado do cliente (único por tenant), útil para entregas e notificações.
- O código nunca é guardado em claro nem registado em log (exceto opt-in explícito em `start-dev`).

### Negative

- Imagem Keycloak própria (build Maven no `docker compose up` inicial; produção precisa do mesmo pipeline e de `kc.sh build`).
- Código contra SPIs do Keycloak (`keycloak-services` é API não-pública): cada upgrade do Keycloak exige recompilar e correr `PhoneOtpKeycloakIT`.
- O realm passa a depender do jar: sem o provider, o login de `pede-aqui-web`/`pede-aqui-mobile` falha ("authenticator not found") — por isso o compose constrói a imagem com o jar.
- Duplicação pequena: a normalização E.164 existe no provider (`PhoneNumbers`) e no backend (`PhoneNumberE164`), em builds separados.

## Risks and Mitigations

| Risco | Mitigação |
|---|---|
| SIM swap / SMS intercetado | Só clientes; `blockedRoles` recusa qualquer staff/privilegiado; papéis privilegiados mantêm palavra-passe + TOTP |
| Bombardeamento de SMS (custo, assédio) | Cooldown 60 s, 3/número e 10/IP por 15 min; bloqueios; WAF rate-based à frente do Keycloak (LAC-11 §1, externo) |
| DoS de bloqueio do número de outra pessoa (pedir código e errar 5 vezes) | Bloqueio limitado (15 min, progressivo até 24 h); o titular pode sempre entrar por email/palavra-passe se o tiver; monitorizar eventos `phone_otp_*`; avaliar CAPTCHA no ecrã do número |
| IP real atrás de proxy/LB | Configurar `proxy-headers` do Keycloak em produção; sem isso todos os pedidos parecem vir do LB e a quota por IP fica global |
| Atributo `phone_e164` não é único no Keycloak | Autenticador recusa números ambíguos; só admin edita o atributo; backend tem índice único por tenant |
| Fornecedor de SMS indisponível | Erro genérico ao utilizador, evento `phone_otp_sms_failed`; alternativa palavra-passe na mesma página |
| Segredo HMAC diferente entre nós / rodado | Códigos pendentes deixam de validar (TTL 5 min, impacto curto); documentado como segredo partilhado |

## Migration Implications

- **Flyway V063** (aditiva, nullable; ver cabeçalho da migração para locking/rollback).
- **Realm**: só entra em vigor num import novo (`docker compose rm -sf keycloak && docker compose up -d --build keycloak`). Em ambientes existentes aplicar via Admin API/console (fluxo, config, override nos 2 clientes, mappers, user profile).
- **API**: `MeResponse` ganha `phoneE164`, `phoneVerifiedAt` (aditivo; `docs/api/API-MIGRATION.md`).
- **Apps**: nenhuma alteração obrigatória. Opcional: a app Flutter pode passar `login_hint=+258…` para pré-preencher o número.

## Validation Criteria

- Unit (provider): `PhoneOtpEngineTest`, `PhoneNumbersTest`, `OtpPolicyAndSenderTest` — TTL, 5 tentativas → bloqueio, progressão, cooldown, quotas por número/IP, hash ligado a challenge+número, código ausente dos logs do stub.
- Integração (provider): `PhoneOtpKeycloakIT` — Keycloak 26.8.1 real com o jar e o realm do repositório: cliente novo entra por telefone e recebe `phone_number`/`phone_number_verified`/`CUSTOMER`; segundo login reutiliza a conta; 5 códigos errados bloqueiam; reenvio em cooldown recusado; número inválido recusado; utilizador OPS com telefone recusado; "email e palavra-passe" e `login_hint` email caem no formulário de palavra-passe; `delivery-app` sem passo de telefone.
- Backend: `CustomerVerifiedPhoneE2ETest`, `PhoneNumberE164Test`; `mvn clean verify` + `ModularityTest`.
- Critério de aceitação LAC-18: login completo por telemóvel na app cliente; brute-force bloqueado; código ausente dos logs.

## References

- `docs/gaps/REGISTO-LACUNAS.md` §LAC-18
- `keycloak/extensions/sms-otp/` (provider), `keycloak/Dockerfile`, `keycloak/delivery-realm.json`
- `backend/src/main/resources/db/migration/V063__app_user_profiles_verified_phone.sql`
- Keycloak Server Developer Guide — Authentication SPI, Service Provider Interfaces
