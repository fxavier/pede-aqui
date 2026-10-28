import { describe, it, expect } from 'vitest'
import { emptyAddressForm, formatAddress, toAddressRequest, validateAddressForm } from './address'

const filled = {
  ...emptyAddressForm('Ana Teste'),
  street: 'Av. Julius Nyerere', number: '1200', neighbourhood: 'Polana', city: 'Maputo', phone: ' ', notes: 'Portão verde',
}

describe('validateAddressForm', () => {
  it('requires the address text fields', () => {
    const errors = validateAddressForm(emptyAddressForm())
    expect(Object.keys(errors).sort()).toEqual(['neighbourhood', 'number', 'recipientName', 'street'])
  })

  it('accepts a complete form without GPS pin', () => {
    expect(validateAddressForm(filled)).toEqual({})
  })

  it('rejects a half GPS pin', () => {
    expect(validateAddressForm({ ...filled, latitude: -25.9, longitude: null }).latitude).toBeDefined()
  })
})

describe('toAddressRequest', () => {
  it('maps street + number to line1, bairro to line2 and blanks to null', () => {
    expect(toAddressRequest({ ...filled, latitude: -25.9692, longitude: 32.5732 })).toEqual({
      label: 'Casa',
      recipientName: 'Ana Teste',
      phone: null,
      line1: 'Av. Julius Nyerere 1200',
      line2: 'Polana',
      city: 'Maputo',
      state: null,
      postalCode: null,
      latitude: -25.9692,
      longitude: 32.5732,
      deliveryInstructions: 'Portão verde',
    })
  })
})

describe('formatAddress', () => {
  it('joins the non-empty parts', () => {
    expect(formatAddress({ line1: 'Rua 1', line2: null, city: 'Maputo' })).toBe('Rua 1, Maputo')
  })
})
