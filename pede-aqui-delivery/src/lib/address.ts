import type { CustomerAddress, CustomerAddressRequest } from './api/types'

/** Checkout "new address" form state; mapped to the backend address shape by {@link toAddressRequest}. */
export interface AddressForm {
  label: string
  recipientName: string
  phone: string
  street: string
  number: string
  neighbourhood: string
  city: string
  notes: string
  latitude: number | null
  longitude: number | null
}

export type AddressFormErrors = Partial<Record<keyof AddressForm, string>>

export function emptyAddressForm(recipientName = ''): AddressForm {
  return {
    label: 'Casa', recipientName, phone: '', street: '', number: '', neighbourhood: '', city: 'Maputo', notes: '',
    latitude: null, longitude: null,
  }
}

const REQUIRED: (keyof AddressForm)[] = ['label', 'recipientName', 'street', 'number', 'neighbourhood', 'city']

/** Client-side checks mirroring the backend (required text fields; GPS pin optional but complete). */
export function validateAddressForm(form: AddressForm): AddressFormErrors {
  const errors: AddressFormErrors = {}
  for (const field of REQUIRED) {
    if (!String(form[field] ?? '').trim()) errors[field] = 'Obrigatório'
  }
  if ((form.latitude === null) !== (form.longitude === null)) errors.latitude = 'Localização incompleta'
  return errors
}

const blankToNull = (v: string) => (v.trim() ? v.trim() : null)

/** Street + number form line 1, the neighbourhood (bairro) line 2 — the usual Mozambican address shape. */
export function toAddressRequest(form: AddressForm): CustomerAddressRequest {
  return {
    label: form.label.trim(),
    recipientName: form.recipientName.trim(),
    phone: blankToNull(form.phone),
    line1: `${form.street.trim()} ${form.number.trim()}`.trim(),
    line2: blankToNull(form.neighbourhood),
    city: form.city.trim(),
    state: null,
    postalCode: null,
    latitude: form.latitude,
    longitude: form.longitude,
    deliveryInstructions: blankToNull(form.notes),
  }
}

/** One-line summary for address pickers. */
export function formatAddress(a: Pick<CustomerAddress, 'line1' | 'line2' | 'city'>): string {
  return [a.line1, a.line2, a.city].filter(Boolean).join(', ')
}
