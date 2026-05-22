const currency = new Intl.NumberFormat("pt-MZ", {
  style: "currency",
  currency: "MZN",
  maximumFractionDigits: 2
});

export function formatMzn(value: number): string {
  return currency.format(value ?? 0);
}
