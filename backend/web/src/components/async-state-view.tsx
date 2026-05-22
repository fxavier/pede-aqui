"use client";

import type { AsyncState } from "../services/api";
import type { ReactNode } from "react";

export function AsyncStateView<T>({ state, children }: { state: AsyncState<T>; children: (data: T) => ReactNode }) {
  if (state.kind === "loading") return <p>A carregar...</p>;
  if (state.kind === "forbidden") return <p>Sem permissao para este recurso.</p>;
  if (state.kind === "empty") return <p>Sem dados para apresentar.</p>;
  if (state.kind === "error") return <p>Erro: {state.message}</p>;
  return <>{children(state.data)}</>;
}
