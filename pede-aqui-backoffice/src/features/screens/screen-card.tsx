"use client";

import Link from "next/link";
import { ArrowUpRight, Image as ImageIcon } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import type { ScreenSummary } from "./screen-service";

export function ScreenCard({ screen }: { screen: ScreenSummary }) {
  return (
    <Link href={screen.route} className="group block focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary">
      <Card className="h-full overflow-hidden transition-all duration-200 hover:-translate-y-1 hover:shadow-soft">
        <CardHeader className="pb-4">
          <div className="flex items-start justify-between gap-3">
            <div>
              <Badge variant="secondary">{screen.group}</Badge>
              <CardTitle className="mt-3 line-clamp-2">{screen.title}</CardTitle>
            </div>
            <div className="rounded-xl bg-surface-container p-2 text-primary transition-transform group-hover:translate-x-1 group-hover:-translate-y-1">
              <ArrowUpRight className="h-5 w-5" />
            </div>
          </div>
          <CardDescription>{screen.folderTitle}</CardDescription>
        </CardHeader>
        <CardContent>
          <div className="flex items-center justify-between rounded-xl bg-surface-container-low p-3 text-xs font-bold uppercase tracking-wide text-on-surface-variant">
            <span className="flex items-center gap-2"><ImageIcon className="h-4 w-4" /> Ecra visual importado</span>
            <span className="text-secondary">Pronto</span>
          </div>
        </CardContent>
      </Card>
    </Link>
  );
}
