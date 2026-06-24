# Pede Aqui — Delivery Web App

React + Vite SPA for customers to browse vendors, place orders, and track deliveries.

## Setup

```bash
cd pede-aqui-delivery
npm install
cp .env.local .env.local   # already included, edit as needed
npm run dev                 # http://localhost:5173
```

## Environment variables (`.env.local`)

| Variable | Default | Description |
|---|---|---|
| `VITE_API_BASE_URL` | `http://localhost:8080/api/v1` | Spring Boot backend |
| `VITE_KEYCLOAK_URL` | `http://localhost:8081` | Keycloak server |
| `VITE_KEYCLOAK_REALM` | `delivery` | Keycloak realm |
| `VITE_KEYCLOAK_CLIENT_ID` | `pede-aqui-web` | Keycloak public client |

## Prerequisites

- Infrastructure running: `docker compose up -d postgres redis keycloak minio` (from repo root)
- Backend running: `cd backend && mvn spring-boot:run`
- Keycloak realm has the `pede-aqui-web` public client (already added to `keycloak/delivery-realm.json`)

## Tech stack

- React 18 + Vite + TypeScript
- Tailwind CSS v3 + shadcn/ui components
- Redux Toolkit (auth + cart state)
- TanStack Query (server state)
- oidc-client-ts (Keycloak PKCE)
- React Router v6

## Auth flow

1. Customer clicks "Entrar" → redirected to Keycloak
2. After login, Keycloak redirects to `/auth/callback`
3. Token stored in `sessionStorage`; cart persisted in `localStorage`

## Limitations (MVP)

- No customer order history endpoint on backend — orders are stored in `localStorage` after checkout
- Cart is server-side; adding items requires authentication
- No real-time WebSocket tracking; polls every 30 seconds for active orders
