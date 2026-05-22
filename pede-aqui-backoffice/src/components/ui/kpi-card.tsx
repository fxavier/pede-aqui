"use client";

import { cn } from "@/lib/utils";
import { Card, CardContent } from "./card";

interface KpiCardProps {
  title: string;
  value: string;
  subtitle?: string;
  icon?: React.ReactNode;
  trend?: { value: string; positive: boolean };
  className?: string;
  loading?: boolean;
}

export function KpiCard({ title, value, subtitle, icon, trend, className, loading }: KpiCardProps) {
  if (loading) {
    return (
      <Card className={cn("animate-pulse", className)}>
        <CardContent className="space-y-3 p-6">
          <div className="h-4 w-24 rounded bg-surface-container-high" />
          <div className="h-8 w-32 rounded bg-surface-container-high" />
          <div className="h-3 w-20 rounded bg-surface-container-high" />
        </CardContent>
      </Card>
    );
  }

  return (
    <Card className={cn("animate-fade-in", className)}>
      <CardContent className="p-6">
        <div className="flex items-start justify-between">
          <div className="space-y-1">
            <p className="text-sm font-bold text-on-surface-variant">{title}</p>
            <p className="font-display text-3xl text-on-surface">{value}</p>
            {subtitle && (
              <p className="text-xs text-on-surface-variant">{subtitle}</p>
            )}
          </div>
          {icon && (
            <div className="flex h-12 w-12 items-center justify-center rounded-2xl bg-primary-fixed text-primary">
              {icon}
            </div>
          )}
        </div>
        {trend && (
          <div className="mt-4 flex items-center gap-1.5 border-t border-outline-variant pt-3">
            <span
              className={cn(
                "text-xs font-bold",
                trend.positive ? "text-secondary" : "text-error",
              )}
            >
              {trend.positive ? "+" : ""}{trend.value}
            </span>
            <span className="text-xs text-on-surface-variant">em relação ao mês anterior</span>
          </div>
        )}
      </CardContent>
    </Card>
  );
}
