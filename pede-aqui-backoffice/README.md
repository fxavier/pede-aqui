# Pede Aqui Backoffice

Generated Next.js application from the uploaded `pede-aqui-backoffice.zip` design package.

## Stack

- Next.js App Router
- TypeScript
- Tailwind CSS
- shadcn-style UI primitives
- Redux Toolkit
- React Query
- Mock data with API-ready service boundaries
- Custom CSS for imported screen fidelity and modern dashboard polish

## Included screens

The app imports **32 screens** from the ZIP and exposes each one as a dedicated Next.js route.

Main routes:

- `/` — modern command center / overview
- `/screens` — all imported screens grouped by module
- `/screens/<slug>` — individual imported screen

## Requirements

Use Node.js 20+ and npm 10+.

```bash
nvm install 20
nvm use 20
node -v
npm -v
```

The project includes `.nvmrc`, so `nvm use` is enough after Node 20 is installed.

## Run locally

Prefer `npm ci` because the project includes a lockfile.
Do not use `npm install --force` unless you are intentionally bypassing peer dependency checks.

```bash
rm -rf node_modules .next
npm cache verify
npm ci
npm run dev
```

Open `http://localhost:3000`.

## Validate

```bash
npm run validate
```

This runs:

- TypeScript typecheck
- ESLint
- Screen coverage validation

## Build

```bash
npm run build
npm start
```

## Troubleshooting npm

If npm fails with `Exit handler never called`, clean the local install state and reinstall from the lockfile:

```bash
rm -rf node_modules .next package-lock.json
npm cache clean --force
npm install
```

If the problem continues, switch to Node.js 20 LTS:

```bash
nvm install 20
nvm use 20
rm -rf node_modules .next package-lock.json
npm cache clean --force
npm install
```

## API readiness

The current implementation uses mock/static data. To connect a future backend:

1. Copy `.env.example` to `.env.local`.
2. Set `NEXT_PUBLIC_ENABLE_MOCKS=false`.
3. Set `NEXT_PUBLIC_API_BASE_URL=http://localhost:8080/api` or your deployed API Gateway URL.
4. Implement the service calls in `src/features/screens/screen-service.ts` using `src/lib/http-client.ts`.

## Notes

- The original PNG references are copied to `public/reference-screens` for visual comparison.
- Imported HTML templates are sanitized and rendered statically to preserve the look of the provided designs.
- For a production implementation, gradually convert each imported screen into typed React components backed by API DTOs.

## Screen coverage

Every visual screen from the uploaded ZIP is available as a dedicated Next.js route under `src/app/screens/<screen-slug>/page.tsx`.

Use this command to validate coverage after changing or adding screens:

```bash
npm run validate:screens
```

See `docs/SCREEN_ROUTE_MAP.md` for the full route mapping.
