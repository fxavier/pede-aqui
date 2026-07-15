import type { Product, Sku } from "@/lib/api/types";

/**
 * Contract gap (found by Lane F2 — report to whoever owns src/lib/api/types.ts):
 * the backend `SkuResponse`/`ProductResponse` DTOs
 * (backend/src/main/java/com/delivery/catalog/dto/{SkuResponse,ProductResponse}.java)
 * already return `pendingPrice` (per-SKU, non-null while a price change awaits
 * moderation) and `primaryImageUrl` (a resolvable image URL), but the shared frontend
 * `Sku`/`Product` types in src/lib/api/types.ts have not been extended to declare them.
 * Lane F2 owns only src/app/catalogo/** and src/components/catalogo/**, so these fields
 * are read here via a runtime-safe accessor instead of editing the canonical types.
 * Recommended fix: add `pendingPrice: number | null` to `Sku` and
 * `primaryImageUrl?: string | null` to `Product` in src/lib/api/types.ts.
 */
export function skuPendingPrice(sku: Sku): number | null {
  const raw = (sku as unknown as { pendingPrice?: number | null }).pendingPrice;
  return raw ?? null;
}

export function productImageUrl(product: Product): string | null {
  const raw = (product as unknown as { primaryImageUrl?: string | null }).primaryImageUrl;
  return raw ?? null;
}

/** The single active SKU a product's attribute/price edit flow targets (per AC-2.1). */
export function activeSku(product: Product): Sku | null {
  return product.skus.find((s) => s.active) ?? product.skus[0] ?? null;
}

/** First SKU on the product carrying a non-null pending price, if any. */
export function productPendingPrice(product: Product): { sku: Sku; pendingPrice: number } | null {
  for (const sku of product.skus) {
    const pending = skuPendingPrice(sku);
    if (pending !== null) return { sku, pendingPrice: pending };
  }
  return null;
}

export function deltaPercent(current: number, next: number): number {
  if (current === 0) return next === 0 ? 0 : 100;
  return ((next - current) / current) * 100;
}

export function extractErrorMessage(err: unknown, fallback: string): string {
  if (err && typeof err === "object" && "message" in err) {
    const message = (err as { message?: unknown }).message;
    if (typeof message === "string" && message.trim().length > 0) return message;
  }
  if (err instanceof Error && err.message) return err.message;
  return fallback;
}

export function errorCode(err: unknown): string | undefined {
  if (err && typeof err === "object" && "code" in err) {
    const code = (err as { code?: unknown }).code;
    if (typeof code === "string") return code;
  }
  return undefined;
}
