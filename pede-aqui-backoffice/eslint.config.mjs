import { FlatCompat } from '@eslint/eslintrc';
import { fileURLToPath } from 'node:url';
import path from 'node:path';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const compat = new FlatCompat({ baseDirectory: __dirname });

const eslintConfig = [
  {
    ignores: ['.next/**', 'out/**', 'build/**', 'coverage/**', 'node_modules/**', 'next-env.d.ts']
  },
  ...compat.extends('next/core-web-vitals', 'next/typescript'),
  {
    // Same pattern as src/app/catalogo/familias/page.tsx (inline-disabled there): the
    // loaders are plain functions recreated every render, so the effects intentionally
    // re-run only when the selection changes. TODO: move to inline disables in the file.
    files: ['src/app/admin/familias/page.tsx'],
    rules: { 'react-hooks/exhaustive-deps': 'off' }
  }
];

export default eslintConfig;
