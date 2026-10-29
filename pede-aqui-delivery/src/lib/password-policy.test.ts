import { describe, it, expect } from 'vitest'
import { AxiosError, AxiosHeaders } from 'axios'
import { PASSWORD_POLICY_HINT, passwordPolicyErrors, registrationErrorMessage } from './password-policy'

function apiError(status: number, data: unknown) {
  const headers = new AxiosHeaders()
  return new AxiosError('Request failed', 'ERR_BAD_REQUEST', { headers }, null, {
    status, statusText: '', headers: {}, config: { headers }, data,
  })
}

describe('passwordPolicyErrors', () => {
  it('accepts a compliant password', () => {
    expect(passwordPolicyErrors('Segredo1234', 'ana@example.test')).toEqual([])
  })

  it('reports every unmet rule', () => {
    expect(passwordPolicyErrors('password')).toEqual([
      'mínimo 10 caracteres', 'pelo menos um número', 'pelo menos uma letra maiúscula',
    ])
    expect(passwordPolicyErrors('SEGREDO-1234')).toEqual(['pelo menos uma letra minúscula'])
  })

  it('rejects a password equal to the email (case-insensitive)', () => {
    expect(passwordPolicyErrors('Ana1@Example.test', 'ana1@example.test')).toEqual(['diferente do email'])
  })
})

describe('registrationErrorMessage', () => {
  it('maps 400 validation_failed on password to the policy hint', () => {
    const err = apiError(400, { code: 'validation_failed', fieldErrors: [{ field: 'password', message: 'must contain at least one digit' }] })
    expect(registrationErrorMessage(err)).toContain(PASSWORD_POLICY_HINT)
  })

  it('maps 400 password_policy_violation to the policy hint', () => {
    expect(registrationErrorMessage(apiError(400, { code: 'password_policy_violation', message: 'x' }))).toContain(PASSWORD_POLICY_HINT)
  })

  it('maps 409 to email already registered', () => {
    expect(registrationErrorMessage(apiError(409, { code: 'email_exists' }))).toBe('Este email já está registado. Tenta entrar.')
  })

  it('falls back to the API message', () => {
    expect(registrationErrorMessage(apiError(400, { code: 'other', message: 'Pedido inválido' }))).toBe('Pedido inválido')
  })
})
