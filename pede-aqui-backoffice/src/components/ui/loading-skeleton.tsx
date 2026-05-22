import { cn } from "@/lib/utils";

interface LoadingSkeletonProps {
  className?: string;
  count?: number;
}

export function LoadingSkeleton({ className, count = 1 }: LoadingSkeletonProps) {
  return (
    <>
      {Array.from({ length: count }).map((_, i) => (
        <div key={i} className={cn("animate-pulse space-y-3 rounded-2xl bg-white p-6 shadow-card", className)}>
          <div className="h-4 w-1/3 rounded bg-surface-container-high" />
          <div className="h-8 w-1/2 rounded bg-surface-container-high" />
          <div className="h-3 w-2/3 rounded bg-surface-container-high" />
          <div className="flex gap-2 pt-2">
            <div className="h-3 w-16 rounded bg-surface-container-high" />
            <div className="h-3 w-16 rounded bg-surface-container-high" />
          </div>
        </div>
      ))}
    </>
  );
}

export function TableSkeleton({ rows = 5 }: { rows?: number }) {
  return (
    <div className="animate-pulse space-y-3">
      <div className="flex gap-4 border-b border-outline-variant pb-3">
        <div className="h-4 w-24 rounded bg-surface-container-high" />
        <div className="h-4 w-32 rounded bg-surface-container-high" />
        <div className="h-4 w-20 rounded bg-surface-container-high" />
        <div className="h-4 w-16 rounded bg-surface-container-high" />
        <div className="h-4 w-28 rounded bg-surface-container-high" />
      </div>
      {Array.from({ length: rows }).map((_, i) => (
        <div key={i} className="flex gap-4 py-3">
          <div className="h-4 w-24 rounded bg-surface-container-high" />
          <div className="h-4 w-32 rounded bg-surface-container-high" />
          <div className="h-4 w-20 rounded bg-surface-container-high" />
          <div className="h-6 w-16 rounded-full bg-surface-container-high" />
          <div className="h-4 w-28 rounded bg-surface-container-high" />
        </div>
      ))}
    </div>
  );
}
