"use client";

import { CalendarRange } from "lucide-react";
import { Card, CardContent } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import type { ReportInterval, Vendor } from "@/lib/api/types";
import { toInputDate, fromInputDate } from "./date-utils";

const INTERVAL_LABELS: Record<ReportInterval, string> = {
  day: "Diário",
  week: "Semanal",
  month: "Mensal",
};

interface ReportFiltersProps {
  fromDate: Date;
  toDate: Date;
  onFromDateChange: (date: Date) => void;
  onToDateChange: (date: Date) => void;
  interval: ReportInterval;
  onIntervalChange: (interval: ReportInterval) => void;
  canFilterVendor: boolean;
  vendorId: string | undefined;
  onVendorIdChange: (vendorId: string | undefined) => void;
  vendors: Vendor[];
  vendorsLoading: boolean;
  rangeError?: string | null;
}

// Filter bar for the sales reports screen: date range, optional vendor (hidden for
// VENDOR_ADMIN — the backend forces their own vendor server-side regardless), and the
// interval used to bucket the time-series chart.
export function ReportFilters({
  fromDate,
  toDate,
  onFromDateChange,
  onToDateChange,
  interval,
  onIntervalChange,
  canFilterVendor,
  vendorId,
  onVendorIdChange,
  vendors,
  vendorsLoading,
  rangeError,
}: ReportFiltersProps) {
  return (
    <Card>
      <CardContent className="flex flex-col gap-4 p-6 md:flex-row md:flex-wrap md:items-end">
        <div className="flex items-center gap-2 text-on-surface-variant">
          <CalendarRange className="h-5 w-5" />
        </div>

        <div className="space-y-1.5">
          <Label htmlFor="report-from">Data inicial</Label>
          <Input
            id="report-from"
            type="date"
            value={toInputDate(fromDate)}
            max={toInputDate(toDate)}
            onChange={(e) => {
              if (!e.target.value) return;
              onFromDateChange(fromInputDate(e.target.value));
            }}
            className="w-full md:w-44"
          />
        </div>

        <div className="space-y-1.5">
          <Label htmlFor="report-to">Data final</Label>
          <Input
            id="report-to"
            type="date"
            value={toInputDate(toDate)}
            min={toInputDate(fromDate)}
            max={toInputDate(new Date())}
            onChange={(e) => {
              if (!e.target.value) return;
              onToDateChange(fromInputDate(e.target.value));
            }}
            className="w-full md:w-44"
          />
        </div>

        <div className="space-y-1.5">
          <Label htmlFor="report-interval">Intervalo</Label>
          <Select value={interval} onValueChange={(v) => onIntervalChange(v as ReportInterval)}>
            <SelectTrigger id="report-interval" className="w-full md:w-40">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {(Object.keys(INTERVAL_LABELS) as ReportInterval[]).map((key) => (
                <SelectItem key={key} value={key}>
                  {INTERVAL_LABELS[key]}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>

        {canFilterVendor && (
          <div className="space-y-1.5">
            <Label htmlFor="report-vendor">Vendedor</Label>
            <Select
              value={vendorId ?? "ALL"}
              onValueChange={(v) => onVendorIdChange(v === "ALL" ? undefined : v)}
              disabled={vendorsLoading}
            >
              <SelectTrigger id="report-vendor" className="w-full md:w-56">
                <SelectValue placeholder={vendorsLoading ? "A carregar…" : "Todos os vendedores"} />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="ALL">Todos os vendedores</SelectItem>
                {vendors.map((vendor) => (
                  <SelectItem key={vendor.id} value={vendor.id}>
                    {vendor.name}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
        )}

        {rangeError && (
          <p className="text-sm font-medium text-error md:ml-auto">{rangeError}</p>
        )}
      </CardContent>
    </Card>
  );
}
