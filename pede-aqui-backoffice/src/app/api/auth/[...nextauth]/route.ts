// Auth.js endpoints (sign-in redirect, Keycloak callback, session, csrf) — see src/auth.ts.
import { handlers } from "@/auth";

export const { GET, POST } = handlers;
