import { existsSync, readdirSync, readFileSync } from "node:fs";

const manifest = readFileSync("src/features/screens/generated-screens.ts", "utf8");
const slugs = [...manifest.matchAll(/"slug": "([^"]+)"/g)].map((match) => match[1]);
const errors = [];

for (const slug of slugs) {
  const checks = [
    [`src/app/screens/${slug}/page.tsx`, "Next.js page route"],
    [`public/imported-screens/${slug}.html`, "imported HTML template"],
    [`public/reference-screens/${slug}.png`, "reference PNG"],
  ];

  for (const [path, label] of checks) {
    if (!existsSync(path)) {
      errors.push(`${slug}: missing ${label} at ${path}`);
    }
  }
}

const routeDirs = readdirSync("src/app/screens", { withFileTypes: true })
  .filter((entry) => entry.isDirectory() && entry.name !== "[slug]")
  .map((entry) => entry.name);

for (const routeDir of routeDirs) {
  if (!slugs.includes(routeDir)) {
    errors.push(`${routeDir}: route directory exists but is not registered in generated-screens.ts`);
  }
}

if (errors.length > 0) {
  console.error("Screen coverage validation failed:");
  for (const error of errors) console.error(`- ${error}`);
  process.exit(1);
}

console.log(`Screen coverage OK: ${slugs.length} screens have explicit routes, templates and reference images.`);
