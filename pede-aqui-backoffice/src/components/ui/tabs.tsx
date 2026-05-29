"use client";

import * as React from "react";
import { cn } from "@/lib/utils";

interface TabsContextValue {
  value: string;
  onValueChange: (v: string) => void;
}

const TabsContext = React.createContext<TabsContextValue>({ value: "", onValueChange: () => {} });

interface TabsProps {
  defaultValue?: string;
  value?: string;
  onValueChange?: (v: string) => void;
  children: React.ReactNode;
  className?: string;
}

export function Tabs({ defaultValue = "", value, onValueChange, children, className }: TabsProps) {
  const [internal, setInternal] = React.useState(defaultValue);
  const active = value ?? internal;
  const setActive = onValueChange ?? setInternal;
  return (
    <TabsContext.Provider value={{ value: active, onValueChange: setActive }}>
      <div className={cn("space-y-2", className)}>{children}</div>
    </TabsContext.Provider>
  );
}

export function TabsList({ children, className }: { children: React.ReactNode; className?: string }) {
  return (
    <div className={cn("flex gap-1 border-b border-outline-variant pb-0", className)}>
      {children}
    </div>
  );
}

export function TabsTrigger({ value, children, className }: { value: string; children: React.ReactNode; className?: string }) {
  const ctx = React.useContext(TabsContext);
  const active = ctx.value === value;
  return (
    <button
      type="button"
      onClick={() => ctx.onValueChange(value)}
      className={cn(
        "px-4 py-2 text-sm font-medium transition-colors border-b-2 -mb-px",
        active
          ? "border-primary text-primary"
          : "border-transparent text-on-surface-variant hover:text-on-surface",
        className,
      )}
    >
      {children}
    </button>
  );
}

export function TabsContent({ value, children, className }: { value: string; children: React.ReactNode; className?: string }) {
  const ctx = React.useContext(TabsContext);
  if (ctx.value !== value) return null;
  return <div className={className}>{children}</div>;
}
