# [FASE 2] App Cliente — Integração de catálogo, carrinho e checkout

## Objectivo

Conectar os ecrãs `HomeScreen`, `StoreScreen`, `CartScreen` e `CheckoutScreen` ao backend real, substituindo todos os dados mock.

**Spec de referência**: spec:9dbc5b64-42c8-4468-94c6-81606f0dc063/`[spec-delivery]`

## Ficheiros principais

- file:pede_aqui_delivery_app/lib/features/catalog/
- file:pede_aqui_delivery_app/lib/features/cart/
- file:pede_aqui_delivery_app/lib/features/checkout/
- file:pede_aqui_delivery_app/lib/core/di/service_locator.dart

## Trabalho necessário

### `ApiCatalogRepository`

- `getVendors()` → `GET /api/v1/search/vendors?available=true`
- `searchVendors(query)` → `GET /api/v1/search/vendors?q={query}`
- `getProducts(vendorId)` → `GET /api/v1/catalog/vendors/{vendorId}/products`
- Mapear `SearchVendorResponse` e `ProductResponse` para modelos de domínio

### `HomeScreen`

- Mostrar nome real do utilizador (do `AuthState`)
- Carregar vendedores via `CatalogCubit.loadVendors()`
- Pesquisa com debounce 300ms
- Estados: carregamento (skeleton), erro (retry), vazio ("Nenhum vendedor disponível.")

### `StoreScreen`

- Carregar produtos via `CatalogCubit.loadProducts(vendorId)`
- Botão "Adicionar" → `CartCubit.addItem(vendorId, skuId, quantity)`
- Aviso de carrinho multi-vendor: "O seu carrinho tem itens de outro vendedor. Deseja limpar e continuar?"

### `ApiCartRepository`

- `getCart(customerId)` → `GET /api/v1/customers/{customerId}/cart/pricing`
- `addItem(customerId, vendorId, skuId, quantity)` → `POST /api/v1/customers/{customerId}/cart/items`
- `updateQuantity(customerId, itemId, quantity)` → `PATCH /api/v1/customers/{customerId}/cart/items/{itemId}`

### `CartScreen`

- Carregar carrinho real ao entrar no ecrã
- Botões +/- chamam `CartCubit.updateQuantity()`
- Formatação: `1 250,00 MT` em todos os valores
- Estado vazio: "O seu carrinho está vazio. Explore os nossos vendedores!"

### `CheckoutScreen`

- Carregar itens do carrinho (não hardcoded)
- Selecção de método de pagamento funcional (M-Pesa / Dinheiro)
- Botão "Fazer encomenda":
  1. `POST /api/v1/checkout` → recebe `orderId` e `paymentId`
  2. `POST /api/v1/payments/{paymentId}/confirm`
  3. Navegar para `/order-tracking?orderId={id}`
- Idempotency key gerado automaticamente (UUID v4)
- Código promocional: campo presente mas marcar como "Em breve" se API não suportar

## Critérios de Aceitação

HomeScreen mostra vendedores reais da APIPesquisa de vendedores funciona com debounceStoreScreen mostra produtos reais do vendedorAdicionar produto ao carrinho chama APICartScreen mostra itens reais com preços em MTActualizar quantidade chama APICheckoutScreen mostra itens reais do carrinho"Fazer encomenda" cria encomenda e confirma pagamentoTodos os estados (carregamento, erro, vazio) implementados em PTSem dados hardcoded em nenhum ecrã