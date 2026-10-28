import axios from 'axios'

/**
 * Client-side mirror of the Keycloak realm password policy (keycloak/delivery-realm.json) and of the
 * backend @PasswordPolicy constraint: min 10 characters, one digit, one upper-case and one lower-case
 * letter, not equal to the e-mail. The backend stays authoritative.
 */
export const PASSWORD_MIN_LENGTH = 10

export const PASSWORD_POLICY_HINT =
  'Mínimo 10 caracteres, com pelo menos um número, uma maiúscula e uma minúscula (diferente do email).'

/** Returns the unmet rules (in Portuguese); empty when the password complies. */
export function passwordPolicyErrors(password: string, email?: string): string[] {
  const errors: string[] = []
  if (password.length < PASSWORD_MIN_LENGTH) errors.push(`mínimo ${PASSWORD_MIN_LENGTH} caracteres`)
  if (!/\d/.test(password)) errors.push('pelo menos um número')
  if (!/[A-Z]/.test(password)) errors.push('pelo menos uma letra maiúscula')
  if (!/[a-z]/.test(password)) errors.push('pelo menos uma letra minúscula')
  if (email && password.toLowerCase() === email.trim().toLowerCase()) errors.push('diferente do email')
  return errors
}

interface ApiErrorBody {
  code?: string
  message?: string
  fieldErrors?: { field: string; message: string }[]
}

/** Turns a failed POST /customers/register into a user-facing message (handles 400 and 409 bodies). */
export function registrationErrorMessage(err: unknown): string {
  const body: ApiErrorBody | undefined = axios.isAxiosError(err) ? err.response?.data : undefined
  const status = axios.isAxiosError(err) ? err.response?.status : undefined
  if (body?.code === 'email_exists' || status === 409) return 'Este email já está registado. Tenta entrar.'
  if (body?.code === 'password_policy_violation') {
    return `A palavra-passe não cumpre a política de segurança. ${PASSWORD_POLICY_HINT}`
  }
  if (body?.code === 'validation_failed' && body.fieldErrors?.length) {
    const passwordErrors = body.fieldErrors.filter((f) => f.field === 'password')
    if (passwordErrors.length) {
      return `A palavra-passe não cumpre a política de segurança. ${PASSWORD_POLICY_HINT}`
    }
    return body.fieldErrors.map((f) => `${f.field}: ${f.message}`).join('; ')
  }
  if (body?.message) return body.message
  return (err instanceof Error && err.message) || 'Erro ao criar conta. Tenta novamente.'
}
