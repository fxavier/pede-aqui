# Pede Aqui Backoffice — Architecture Notes

## Frontend architecture

The app is intentionally simple and production-oriented:

- `src/app`: Next.js App Router pages and providers.
- `src/components/ui`: shadcn-style reusable UI primitives.
- `src/components/layout`: backoffice shell and navigation.
- `src/features/screens`: imported screens, route rendering, React Query hooks and screen service.
- `src/store`: Redux Toolkit global UI/auth state.
- `src/lib`: utilities and HTTP client prepared for future API integration.

## Current data strategy

The first release uses mock/static data generated from the uploaded ZIP. React Query is already used around screen loading so that future replacement with real API calls does not require rewriting the UI layer.

## Future API integration

1. Set `NEXT_PUBLIC_ENABLE_MOCKS=false`.
2. Set `NEXT_PUBLIC_API_BASE_URL` to the backend/API gateway URL.
3. Replace the placeholder comments in `src/features/screens/screen-service.ts` with calls through `src/lib/http-client.ts`.
4. Add authentication token injection from the auth state or NextAuth/Keycloak session.

## Security notes

Imported screens are sanitized by removing script tags and inline event handler attributes before being rendered as static templates. In a real multi-user system, do not render untrusted HTML from an API. Convert templates into typed React components or use a strict allow-list sanitizer at the API boundary.
