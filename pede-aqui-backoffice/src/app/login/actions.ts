"use server";

import { signIn } from "@/auth";

/** Starts the Keycloak Authorization Code + PKCE sign-in (LAC-10); returns to a same-origin path afterwards. */
export async function signInWithKeycloak(formData: FormData): Promise<void> {
  const raw = String(formData.get("callbackUrl") ?? "/");
  const redirectTo = raw.startsWith("/") && !raw.startsWith("//") && !raw.startsWith("/\\") ? raw : "/";
  await signIn("keycloak", { redirectTo });
}
