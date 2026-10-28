"use client";

import { useMemo } from "react";
import { useSearchParams } from "next/navigation";
import { Loader2 } from "lucide-react";
import { ScreenCard } from "./screen-card";
import { useScreensQuery } from "./use-screens";
import { useAppSelector } from "@/store/hooks";

export function ScreenList() {
  const params = useSearchParams();
  const group = params.get("group");
  const query = useAppSelector((state) => state.ui.searchQuery).trim().toLowerCase();
  const { data, isLoading, error } = useScreensQuery();

  const screens = useMemo(() => {
    return (data ?? []).filter((screen) => {
      const matchesGroup = group ? screen.group === group : true;
      const haystack = `${screen.title} ${screen.folderTitle} ${screen.group} ${screen.slug}`.toLowerCase();
      const matchesQuery = query ? haystack.includes(query) : true;
      return matchesGroup && matchesQuery;
    });
  }, [data, group, query]);

  if (isLoading) {
    return (
      <div className="flex h-64 items-center justify-center text-on-surface-variant">
        <Loader2 className="mr-2 h-5 w-5 animate-spin" /> A carregar ecras...
      </div>
    );
  }

  if (error) {
    return <div className="rounded-2xl border border-error-container bg-white p-6 text-error">Falha ao carregar ecras.</div>;
  }

  return (
    <div className="grid grid-cols-1 gap-5 md:grid-cols-2 xl:grid-cols-3">
      {screens.map((screen) => (
        <ScreenCard key={screen.slug} screen={screen} />
      ))}
    </div>
  );
}
