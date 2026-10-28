"use client";

import { useQuery } from "@tanstack/react-query";
import { getScreenTemplate, listScreens } from "./screen-service";

export const screenQueryKeys = {
  all: ["screens"] as const,
  detail: (slug: string) => ["screens", slug] as const,
};

export function useScreensQuery() {
  return useQuery({
    queryKey: screenQueryKeys.all,
    queryFn: listScreens,
  });
}

export function useScreenQuery(slug: string) {
  return useQuery({
    queryKey: screenQueryKeys.detail(slug),
    queryFn: () => getScreenTemplate(slug),
  });
}
