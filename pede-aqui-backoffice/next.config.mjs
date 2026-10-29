// Security headers (LAC-10). Evaluated at build time: set AUTH_KEYCLOAK_ISSUER / AUTH_URL / CSP_UPLOAD_ORIGINS
// in the build environment for each deployment.
const isProd = process.env.NODE_ENV === "production";
const isHttps = (process.env.AUTH_URL ?? "").startsWith("https://");

function originOf(url, fallback) {
  try {
    return new URL(url).origin;
  } catch {
    return fallback;
  }
}

// Keycloak: the logout form POST is redirected there (end_session_endpoint), which form-action governs.
const keycloakOrigin = originOf(process.env.AUTH_KEYCLOAK_ISSUER, "http://localhost:8081");
// Presigned-URL uploads/downloads go straight from the browser to object storage (MinIO in dev, S3 in prod).
const uploadOrigins = (process.env.CSP_UPLOAD_ORIGINS ?? "http://localhost:9000 https://*.amazonaws.com").trim();

const csp = [
  "default-src 'self'",
  // Next.js App Router injects inline bootstrap scripts; dev (React Refresh) also needs eval.
  `script-src 'self' 'unsafe-inline'${isProd ? "" : " 'unsafe-eval'"}`,
  "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com",
  "font-src 'self' data: https://fonts.gstatic.com",
  `img-src 'self' data: blob: https: ${uploadOrigins}`,
  `connect-src 'self' ${uploadOrigins}${isProd ? "" : " ws: wss:"}`,
  "frame-ancestors 'none'",
  `form-action 'self' ${keycloakOrigin}`,
  "base-uri 'self'",
  "object-src 'none'",
  ...(isHttps ? ["upgrade-insecure-requests"] : []),
].join("; ");

const securityHeaders = [
  { key: "Content-Security-Policy", value: csp },
  { key: "X-Frame-Options", value: "DENY" },
  { key: "X-Content-Type-Options", value: "nosniff" },
  { key: "Referrer-Policy", value: "strict-origin-when-cross-origin" },
  { key: "Permissions-Policy", value: "camera=(), microphone=(), geolocation=(), payment=()" },
  ...(isProd ? [{ key: "Strict-Transport-Security", value: "max-age=63072000; includeSubDomains" }] : []),
];

/** @type {import('next').NextConfig} */
const nextConfig = {
  reactStrictMode: true,
  poweredByHeader: false,
  async headers() {
    return [{ source: "/:path*", headers: securityHeaders }];
  },
};

export default nextConfig;
