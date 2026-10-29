/**
 * Backoffice role model and route-access table (LAC-10). Shared by the middleware (server-side guard)
 * and the AppShell navigation so the two can never drift. Role names are the Keycloak realm roles verbatim.
 */
export const MARKETPLACE_ROLES = [
  "ADMIN",
  "OPS",
  "FINANCE",
  "SUPPORT",
  "VENDOR_ADMIN",
  "VENDOR_STAFF",
  "COURIER",
  "CUSTOMER",
] as const;

export type MarketplaceRole = (typeof MARKETPLACE_ROLES)[number];

/** Keeps only the realm roles the marketplace knows (drops default-roles-*, offline_access, ...). */
export function toMarketplaceRoles(realmRoles: unknown): MarketplaceRole[] {
  if (!Array.isArray(realmRoles)) return [];
  return MARKETPLACE_ROLES.filter((role) => realmRoles.includes(role));
}

/** Most privileged role first — used as the single "display" role for pages that still read one role. */
export function primaryRole(roles: readonly string[]): string {
  return MARKETPLACE_ROLES.find((role) => roles.includes(role)) ?? "";
}

export function hasAnyRole(roles: readonly string[], allowed: readonly string[]): boolean {
  return allowed.length === 0 || allowed.some((role) => roles.includes(role));
}

/** Platform super-admin: ADMIN without a tenant_id claim (mirrors backend TenantContext.isPlatformAdmin). */
export function isPlatformAdmin(roles: readonly string[], tenantId: string | null | undefined): boolean {
  return roles.includes("ADMIN") && !tenantId;
}

type RouteRule = { prefix: string; roles: readonly MarketplaceRole[]; platformAdminOnly?: boolean };

/** Route table from CLAUDE.md "Backoffice Architecture > Routes". Empty roles = any authenticated user. */
export const ROUTE_RULES: readonly RouteRule[] = [
  { prefix: "/platform", roles: ["ADMIN"], platformAdminOnly: true },
  { prefix: "/empresa", roles: ["VENDOR_ADMIN", "ADMIN"] },
  { prefix: "/admin", roles: ["ADMIN"] },
  { prefix: "/catalogo", roles: ["ADMIN", "VENDOR_ADMIN", "OPS"] },
  { prefix: "/vendors", roles: ["ADMIN", "VENDOR_ADMIN", "OPS"] },
  { prefix: "/users", roles: ["ADMIN"] },
  { prefix: "/orders", roles: ["ADMIN", "VENDOR_ADMIN", "OPS", "SUPPORT"] },
  { prefix: "/sales", roles: ["ADMIN", "VENDOR_ADMIN", "OPS", "FINANCE", "SUPPORT"] },
  { prefix: "/couriers", roles: ["ADMIN", "OPS"] },
  { prefix: "/finance", roles: ["ADMIN", "FINANCE"] },
  { prefix: "/reports", roles: ["ADMIN", "VENDOR_ADMIN", "OPS", "FINANCE"] },
  { prefix: "/support", roles: ["ADMIN", "SUPPORT"] },
  { prefix: "/marketing", roles: ["ADMIN", "OPS", "VENDOR_ADMIN"] },
  { prefix: "/screens", roles: [] },
];

function matches(pathname: string, prefix: string): boolean {
  return pathname === prefix || pathname.startsWith(prefix + "/");
}

/** Longest matching rule for a path, or undefined (then any authenticated user may enter). */
export function ruleFor(pathname: string): RouteRule | undefined {
  return ROUTE_RULES.filter((rule) => matches(pathname, rule.prefix)).sort((a, b) => b.prefix.length - a.prefix.length)[0];
}

/** True when a user with these roles/tenant may open the path. */
export function canAccess(pathname: string, roles: readonly string[], tenantId: string | null | undefined): boolean {
  const rule = ruleFor(pathname);
  if (!rule) return true;
  if (rule.platformAdminOnly && !isPlatformAdmin(roles, tenantId)) return false;
  return hasAnyRole(roles, rule.roles);
}
