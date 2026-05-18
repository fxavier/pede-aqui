import type { ReactNode } from "react";
import "./styles.css";

export const metadata = {
  title: "Pede Aqui Backoffice",
  description: "Backoffice do marketplace de entregas para Mocambique"
};

export default function RootLayout({ children }: { children: ReactNode }) {
  return (
    <html lang="pt">
      <body>{children}</body>
    </html>
  );
}
