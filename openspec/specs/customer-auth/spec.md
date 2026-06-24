# Customer Auth

## Purpose

Defines authentication, session management, and access control for customers of the Pede Aqui delivery web app.

## Requirements

### Requirement: Customer can log in via Keycloak PKCE
The system SHALL authenticate customers using the Keycloak Authorization Code + PKCE flow against the `delivery` realm using the `pede-aqui-web` public client. No client secret is used. The token (access + refresh) SHALL be stored in `sessionStorage` after successful exchange.

#### Scenario: Successful login redirect
- **WHEN** an unauthenticated customer clicks "Entrar"
- **THEN** the app redirects to the Keycloak login page with a PKCE challenge

#### Scenario: Successful token exchange after redirect
- **WHEN** Keycloak redirects back to `/auth/callback` with an authorization code
- **THEN** the app exchanges the code for tokens and redirects the customer to the home page

#### Scenario: Login failure
- **WHEN** the token exchange fails (network error or invalid code)
- **THEN** the app displays an error message and returns the customer to the login page

---

### Requirement: Customer session persists across page refresh
The system SHALL rehydrate auth state from `sessionStorage` on app load. If the access token is expired but a refresh token is available, the app SHALL silently refresh before rendering protected routes.

#### Scenario: Valid session on reload
- **WHEN** a customer reloads the page and a valid access token exists in sessionStorage
- **THEN** the app renders the home page without a login redirect

#### Scenario: Expired access token with valid refresh token
- **WHEN** the app loads and the access token is expired but the refresh token is valid
- **THEN** the app silently exchanges the refresh token for new tokens and continues

#### Scenario: Fully expired session
- **WHEN** both tokens are expired or missing
- **THEN** the app redirects the customer to the login page

---

### Requirement: Customer can log out
The system SHALL clear all tokens from sessionStorage and redirect to Keycloak logout endpoint, which then returns the customer to the app home page.

#### Scenario: Logout clears session
- **WHEN** a customer clicks "Sair"
- **THEN** tokens are removed from sessionStorage and the customer is redirected to the Keycloak logout URL

---

### Requirement: Protected routes require authentication
The system SHALL redirect unauthenticated customers attempting to access protected routes (cart, checkout, orders) to the login page, preserving the intended destination as a redirect parameter.

#### Scenario: Unauthenticated access to protected route
- **WHEN** an unauthenticated customer navigates to `/checkout`
- **THEN** the app redirects to `/login?redirect=/checkout`

#### Scenario: Post-login redirect
- **WHEN** a customer logs in after being redirected from a protected route
- **THEN** the app returns the customer to the originally intended route
