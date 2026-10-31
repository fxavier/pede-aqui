# Proteção de dados pessoais — Pede Aqui

> **Estado:** controlos técnicos implementados em W7-C (LAC-19, LAC-63), 2026-10-31.
> **Aviso:** a Lei de Proteção de Dados Pessoais de Moçambique ainda é uma **proposta** (situação em 2026). Este documento
> segue princípios equivalentes aos de leis de referência. Tudo o que está marcado **⚖ a validar com jurista** — bases
> legais, prazos, textos de consentimento — é um valor por defeito técnico, configurável por ambiente, e tem de ser
> revisto por um jurista antes da produção e outra vez quando a lei for aprovada.

## 1. Âmbito e responsáveis

- **Responsável pelo tratamento:** o operador do marketplace (tenant operador, ADR-0001). ⚖ a validar com jurista
  (identificação legal, contacto do encarregado de proteção de dados).
- **Subcontratantes** (⚖ contratos a rever quando a lei entrar em vigor): AWS (alojamento, `af-south-1`), fornecedor
  de pagamentos (PSP, ainda não integrado), gateway de SMS, Firebase Cloud Messaging / APNs (push), fornecedor de
  e-mail.
- **Transferência internacional:** os dados ficam na região AWS `af-south-1` (África do Sul). ⚖ a validar com jurista.

## 2. Inventário de dados

| Categoria | Onde (tabela / sistema) | Titulares | Finalidade | Base legal (⚖ a validar com jurista) | Quem acede |
|---|---|---|---|---|---|
| Identidade e contacto: nome, e-mail, telefone verificado, idioma | `app_user_profiles`; Keycloak (utilizador, atributos `phone_e164`, `locale`) | clientes, estafetas, staff | conta, autenticação, notificações | execução do contrato | o próprio; SUPPORT/ADMIN (perfil) |
| Dados de perfil alargado: nome completo, NIF, data de nascimento, morada livre, avatar | `app_user_profiles` (V012) | sobretudo staff/comerciantes | faturação e verificação | obrigação legal / contrato | ADMIN |
| Moradas de entrega (texto, coordenadas, instruções) | `customer_addresses` | clientes | entregar encomendas | execução do contrato | o próprio; o estafeta atribuído só durante a entrega (LAC-20) |
| Snapshot de morada na encomenda | `orders.delivery_*` (V047) | clientes | prova da entrega, reclamações | execução do contrato / interesse legítimo | o cliente; estafeta durante a entrega; ops vê só a cidade |
| Encomendas, valores, itens | `orders`, `order_items`, `payments`, `refunds`, razão (`ledger_*`) | clientes | contabilidade, obrigações fiscais | obrigação legal | FINANCE/ADMIN/OPS; comerciante (os seus pedidos, PII mascarada) |
| Lojas favoritas | `customer_favorite_vendors` | clientes | conveniência | consentimento implícito / contrato | o próprio |
| Tokens de push e preferências de notificação | `device_tokens`, `user_notification_preferences` | todos | enviar notificações | contrato / consentimento (marketing) | o sistema (token nunca devolvido nem exportado) |
| Notificações enviadas | `notifications` (caixa in-app), `notification_deliveries` (registo, destinatário mascarado) | todos | informar sobre pedidos; prova de envio | contrato / interesse legítimo | o próprio (caixa); SUPPORT (registo mascarado) |
| Localização do estafeta | `courier_last_locations`, `courier_location_history` | estafetas | despacho e auditoria de percurso | consentimento (versão em `couriers.location_consent_*`) | OPS/ADMIN |
| Auditoria | `audit_logs` (ator = id Keycloak, IP, user-agent) | todos | segurança, responsabilização | obrigação legal / interesse legítimo | ADMIN |
| Histórico de estados | `order_status_history`, `payment_status_history` (ator = id Keycloak ou job) | clientes, staff | rastreabilidade | interesse legítimo / obrigação legal | ADMIN/OPS/SUPPORT |
| Pedidos de eliminação | `account_deletion_requests` (V071) | clientes | cumprir o direito ao apagamento | obrigação legal | sistema; o próprio |
| Consentimentos | `consents` (V071) | todos | prova do consentimento | obrigação legal | o próprio; ADMIN |
| Pedidos de cancelamento, tickets, avaliações (texto livre) | `cancellation_requests.reason`, `support_tickets`, `ratings.comment` | clientes | serviço ao cliente | contrato / interesse legítimo | OPS/SUPPORT; comerciante (avaliações) |
| Provisionamento M2M | `provisioning_requests` (e-mail do dono) | comerciantes | idempotência do provisionamento | contrato | sistema |
| Logs de aplicação | stdout → CloudWatch | todos (indiretamente) | operação | interesse legítimo | equipa técnica |

## 3. Minimização e mascaramento

- Telefone e morada só para quem precisa e durante a janela necessária (LAC-20, `CustomerPiiMasking`).
- O telefone usado em notificações é apenas o **verificado** (LAC-18); os registos guardam o destinatário mascarado.
- **Logs sem PII:** o layout JSON estruturado (LAC-50) passa todos os campos de texto por `PiiMasker`
  (`PiiMaskingJsonMembersCustomizer`, `logging.structured.json.customizer`): e-mails → `a***@***`, telefones E.164 e
  móveis moçambicanos → só os 2 últimos dígitos, JWT → `[jwt]`, `Bearer …` → `Bearer [redacted]`. É uma rede de
  segurança — o código continua proibido de registar PII. Moradas em texto livre não são reconhecíveis por padrão:
  nunca as registar. O formato legível do perfil `dev` não é mascarado (só local).
- Mensagens de log com e-mail removidas do `KeycloakAdminService` (W7-C).

## 4. Retenção

Aplicada pelo job `retention-cleaner` (diário, 05:15 Africa/Maputo; ShedLock), em lotes de 5000 linhas (máx. 200
lotes por classe e execução), uma transação curta por lote. Tabelas append-only só são purgadas pela via sancionada
(`SET LOCAL app.audit_logs_purge = 'on'` — V053; `SET LOCAL app.status_history_purge = 'on'` — V058), dentro da
transação do lote. Cada classe regista uma linha em `data_retention_runs` e as métricas
`retention_rows_deleted_total{data_class}` e `retention_class_runs_total{data_class,result}`.

Todos os prazos são **valores por defeito configuráveis** (`app.privacy.retention.classes.<classe>.retention`,
variável indicada) — **⚖ a validar com jurista**.

| Dado | Classe / mecanismo | Prazo por defeito | Variável de ambiente | Notas |
|---|---|---|---|---|
| Auditoria (`audit_logs`) | `audit-logs` | 24 meses (P730D) | `APP_PRIVACY_RETENTION_AUDIT_LOGS` | purga com `app.audit_logs_purge` |
| Registo de envios (`notification_deliveries`) | `notification-deliveries` | 180 dias | `APP_PRIVACY_RETENTION_NOTIFICATION_DELIVERIES` | nunca linhas `QUEUED` |
| Caixa in-app (`notifications`) | `notifications` | 180 dias | `APP_PRIVACY_RETENTION_NOTIFICATIONS` | |
| Histórico de estados de encomenda | `order-status-history` | 10 anos (P3650D) | `APP_PRIVACY_RETENTION_ORDER_STATUS_HISTORY` | alinhado com o prazo fiscal (LAC-38); purga com `app.status_history_purge` |
| Histórico de estados de pagamento | `payment-status-history` | 10 anos | `APP_PRIVACY_RETENTION_PAYMENT_STATUS_HISTORY` | idem |
| Idempotência do provisionamento (`provisioning_requests`) | `provisioning-requests` | 24 h após terminar | `APP_PRIVACY_RETENTION_PROVISIONING_REQUESTS` | pedidos em curso nunca são apagados |
| Execuções de retenção (`data_retention_runs`) | `data-retention-runs` | 12 meses | `APP_PRIVACY_RETENTION_DATA_RETENTION_RUNS` | |
| GPS de entregas (`courier_location_history`) | job `courier-location-retention` (W5-B) | 30 dias | `app.courier-location.history-retention` | já existia; não duplicado |
| Última posição do estafeta | apagada ao sair de serviço (W5-B) | — | — | |
| Cotações de checkout (`checkout_quotes`) | job `checkout-quote-cleanup` (W6-A) | expiradas + 1 dia | `app.jobs.checkout-quote-cleanup.grace` | já existia |
| Eventos entregues (`event_publication`) | job `event-publication-cleanup` | 7 dias | `app.jobs.event-publication-cleanup.retention` | já existia |
| Logs de aplicação | CloudWatch Logs (infraestrutura, LAC-50) | 30 dias | — (fora da aplicação) | a configurar na conta AWS |
| Encomendas, pagamentos, reembolsos, razão, comissões | **não purgados** | prazo legal fiscal (⚖ a confirmar, LAC-38) | — | ficam pseudonimizados após eliminação de conta |
| Contas eliminadas (perfil pseudonimizado) | mantido para ligar registos fiscais | enquanto houver registos fiscais | — | ⚖ a validar |
| Pedidos de eliminação, consentimentos | mantidos como prova | ⚖ a definir | — | sem prazo automático nesta versão |
| Tickets de suporte, avaliações | **sem prazo automático** | ⚖ a definir | — | trabalho futuro |

Para desligar uma classe: `app.privacy.retention.classes.<classe>.enabled=false` (fica registada como `SKIPPED`).

## 5. Direitos do titular

| Direito | Como | Endpoint / fluxo |
|---|---|---|
| Acesso | exportação JSON descarregável (perfil, preferências, moradas, favoritos, resumo das encomendas, consentimentos, pedidos de eliminação; tokens de push nunca) — limite 3/h por conta, auditada `PERSONAL_DATA_EXPORTED` | `GET /api/v1/me/data-export`; web: `/profile` → "Exportar os meus dados" |
| Retificação | nome, idioma e preferências de notificação pelo próprio (sincronizados com o Keycloak); e-mail pela consola de conta do Keycloak (com verificação); telefone pelo login por telefone + SMS (LAC-18) | `PATCH /api/v1/me`; web: `/profile` |
| Apagamento | pedido → **período de reflexão de 30 dias** (cancelável) → pseudonimização pelo job `account-deletion` (§6). Recusado (409 `active_orders_present`) com encomendas em curso; se surgir uma durante a reflexão, a execução é adiada até terminar | `POST/DELETE/GET /api/v1/me/deletion-request`; web: `/profile` → "Eliminar conta" |
| Oposição / retirada do consentimento | marketing e outros consentimentos versionados podem ser retirados; preferências por canal (push/SMS/e-mail) | `PUT/DELETE /api/v1/me/consents/{purpose}`, `PATCH /api/v1/me` |
| Portabilidade | o mesmo JSON da exportação (formato estruturado, `formatVersion`) | idem acesso |

Contas de staff, comerciantes e estafetas não se auto-eliminam (403 `account_deletion_not_supported`): são encerradas
por um administrador (procedimento manual, ⚖ a definir).

Prazo de resposta a pedidos feitos fora da aplicação (e-mail, balcão): ⚖ a validar com jurista.

## 6. Pseudonimização após eliminação de conta — mapa campo a campo

Executada pelo job `account-deletion` (de hora a hora, :20; `AccountErasureService`) para cada pedido vencido, numa
transação por pedido (`FOR UPDATE SKIP LOCKED`): primeiro o utilizador Keycloak é **desativado**; depois, na mesma
transação da base de dados, tudo o que segue; no fim o utilizador Keycloak é **apagado** (um falhanço aqui deixa o
pedido em `ERASED` e é repetido na execução seguinte). Linha de auditoria `ACCOUNT_PSEUDONYMISED` (contagens por tipo
de dado, nunca valores) e `ACCOUNT_DELETION_COMPLETED`.

| Tabela.coluna | Tratamento | Porquê |
|---|---|---|
| `app_user_profiles.display_name` | → `Cliente removido` | NOT NULL; mantém legíveis os registos fiscais |
| `app_user_profiles.email` | → `deleted-<profileId>@deleted.invalid` | NOT NULL; domínio `.invalid` nunca entregável; liberta o e-mail para novo registo |
| `app_user_profiles.phone`, `phone_e164`, `phone_verified_at` | → NULL | |
| `app_user_profiles.full_name`, `nif`, `date_of_birth`, `address`, `avatar_storage_key` | → NULL | ⚖ se forem emitidas faturas com NIF, é a fatura (documento fiscal) que guarda o NIF, não o perfil |
| `app_user_profiles.status` | → `DELETED` | o perfil deixa de ser resolvido pelos endpoints `/me` e nunca recebe telefone sincronizado |
| `app_user_profiles.keycloak_user_id` | **mantido** | id aleatório cujo utilizador Keycloak é apagado; impede que um token ainda válido (≤ 5 min) crie um perfil novo |
| `app_user_profiles.id`, `tenant_id`, `created_at` | mantidos | chave dos registos fiscais |
| `customer_addresses` (todas as linhas do cliente) | **apagadas** | |
| `customer_favorite_vendors` (por id de perfil ou subject Keycloak) | **apagadas** | |
| `device_tokens`, `user_notification_preferences`, `notifications` (caixa in-app) | **apagadas** | |
| `notification_deliveries` | mantidas (destinatário já mascarado); purgadas aos 180 dias | prova de envio |
| `orders.delivery_recipient_name`, `delivery_phone`, `delivery_line1`, `delivery_line2`, `delivery_postal_code`, `delivery_latitude`, `delivery_longitude`, `delivery_instructions`, `delivery_address_id` | → NULL (trigger V047 relaxado só com `SET LOCAL app.personal_data_erasure = 'on'`, V071) | |
| `orders.delivery_city`, `delivery_state` | mantidos | área grosseira para relatórios e jurisdição fiscal ⚖ |
| `orders.reference`, `customer_id`, `vendor_id`, `status`, valores (`subtotal`, `fees`, `taxes`, `discounts`, `discount_total`, `total`), cotação/IVA (`quote_*`), datas | mantidos | obrigação fiscal/financeira (LAC-38) |
| `order_items` (snapshots de produto e preço) | mantidos | idem |
| `payments`, `refunds`, `ledger_*`, `commissions` | mantidos (sem PII direta) | idem |
| `cancellation_requests.reason` (texto do cliente) | → NULL | texto livre pode conter PII |
| `audit_logs.actor_user_id`, `ip`, `user_agent`; `order_status_history.actor_id` | mantidos (append-only) até à purga por retenção (24 meses / 10 anos) | o subject Keycloak deixa de identificar alguém quando o utilizador Keycloak é apagado ⚖ |
| `ratings.comment` (texto do cliente, W7-D) | → NULL (`rating.api.CustomerRatingPrivacy`) | texto livre pode conter PII |
| `ratings` (linha, `stars`, `courier_stars`, `rater_user_id`, `order_id`, datas) | **mantidos** | as estrelas continuam nas médias de loja/estafeta; `rater_user_id` aponta para o perfil pseudonimizado (que nunca é apagado — FK `fk_ratings_rater_user_id`, V072) |
| `ratings.vendor_reply`, `comment_hidden*` | mantidos | resposta da loja e moderação: não são dados do cliente |
| `support_tickets` | **não tratados nesta versão** | trabalho futuro (⚖ decidir anonimização/remoção) |
| Keycloak: utilizador | desativado → apagado | |

## 7. Consentimentos

`consents(subject_profile_id, purpose, version, granted_at, withdrawn_at)` (V071). Finalidades:
`TERMS_OF_SERVICE`, `PRIVACY_POLICY`, `MARKETING`. Versão atual em `app.privacy.consent-versions.*`
(`APP_PRIVACY_TERMS_VERSION`, `APP_PRIVACY_POLICY_VERSION`, `APP_PRIVACY_MARKETING_VERSION`, por defeito `2026-10`);
só a versão atual pode ser aceite (409 `consent_version_outdated`), o que obriga a novo consentimento quando o texto
muda. Retirar mantém a linha como prova. O consentimento de localização do estafeta continua em
`couriers.location_consent_*` (W5-B). ⚖ textos e versões a validar com jurista; as apps ainda não pedem estes
consentimentos no registo (trabalho futuro).

## 8. Segurança (resumo)

Cifra em repouso (RDS, S3 — KMS) e em trânsito (TLS 1.2+), acesso de produção por SSO com MFA, TOTP obrigatório para
ADMIN/FINANCE/OPS (Keycloak), auditoria das ações sensíveis, limitação de taxa (LAC-11). Detalhes no CLAUDE.md e nos
documentos de infraestrutura.

## 9. Trabalho futuro

- Validação jurídica de todas as marcas ⚖ e revisão quando a lei for aprovada.
- Consentimentos no registo (web/apps) e ecrã de gestão de consentimentos.
- Retenção de tickets de suporte e avaliações (o comentário da avaliação já é apagado na eliminação de conta, §6).
- Encerramento de contas de staff/estafeta/comerciante (procedimento de backoffice).
- Hash do MSISDN em repouso (LAC-63 §3) — hoje o telefone verificado é guardado em claro para envio de SMS.
- Auditoria de consultas de dados sensíveis no backoffice.
