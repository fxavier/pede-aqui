# [FASE 2] App Cliente — Rastreamento de encomenda com polling

## Objectivo

Conectar o `OrderTrackingScreen` ao backend real com actualização automática do estado da encomenda.

**Spec de referência**: spec:9dbc5b64-42c8-4468-94c6-81606f0dc063/`[spec-delivery]`

## Ficheiros principais

- file:pede_aqui_delivery_app/lib/features/orders/

## Trabalho necessário

### `ApiOrderRepository`

- `getTracking(orderId)` → `GET /api/v1/orders/{orderId}/tracking`
- Mapear `TrackingResponse` para `OrderTrackingState`

### `OrderTrackingCubit`

- Polling a cada 15 segundos enquanto ecrã activo
- Parar polling quando `status == DELIVERED` ou `CANCELLED`
- Cancelar timer no `close()`

### `OrderTrackingScreen`

- Receber `orderId` como argumento de rota (não hardcoded)
- Mostrar referência real (ex: `PA-2026-00891`)
- Mostrar código de entrega real (6 dígitos da API)
- Mostrar nome real do estafeta
- Mostrar tempo estimado real
- Timeline com estados em PT:

| Status API | Label PT |
| --- | --- |
| `PAYMENT_CONFIRMED` | Pagamento confirmado |
| `ACCEPTED_BY_VENDOR` | Aceite pelo vendedor |
| `PREPARING` | Em preparo |
| `READY_FOR_PICKUP` | Pronto para recolha |
| `ASSIGNED_TO_COURIER` | Estafeta atribuído |
| `PICKED_UP` | Recolhido |
| `DELIVERING` | Em entrega |
| `DELIVERED` | Entregue ✓ |

### Lacuna de API

`GET /api/v1/orders/mine` não existe. Proposta de contrato para listar encomendas do cliente:

```
GET /api/v1/orders/mine
Response: OrderResponse[] (filtrado pelo JWT)
```

Marcar como em falta e usar `orderId` passado pelo checkout até ser implementado.

## Critérios de Aceitação

orderId passado como argumento de rota desde o checkoutReferência, código e estafeta mostrados com dados reaisTimeline actualiza automaticamente a cada 15 segundosPolling para quando encomenda entregue ou canceladaEstado DELIVERED: mostrar mensagem "Entregue com sucesso!" e botão "Avaliar"Estado CANCELLED: mostrar motivo e botão "Contactar suporte"Código de entrega em formato de 6 caixas individuais (já implementado visualmente)