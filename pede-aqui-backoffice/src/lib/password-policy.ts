/**
 * Client-side mirror of the Keycloak realm password policy (keycloak/delivery-realm.json) and of the
 * backend @PasswordPolicy constraint: min 10 characters, one digit, one upper-case and one lower-case
 * letter, not equal to the e-mail. The backend stays authoritative (400 validation_failed /
 * password_policy_violation).
 */
export const PASSWORD_MIN_LENGTH = 10;

export const PASSWORD_POLICY_HINT =
  'Mínimo 10 caracteres, com pelo menos um número, uma maiúscula e uma minúscula (diferente do email).';

/** Returns the unmet rules (in Portuguese); empty when the password complies. */
export function passwordPolicyErrors(password: string, email?: string): string[] {
  const errors: string[] = [];
  if (password.length < PASSWORD_MIN_LENGTH) errors.push(`mínimo ${PASSWORD_MIN_LENGTH} caracteres`);
  if (!/\d/.test(password)) errors.push('pelo menos um número');
  if (!/[A-Z]/.test(password)) errors.push('pelo menos uma letra maiúscula');
  if (!/[a-z]/.test(password)) errors.push('pelo menos uma letra minúscula');
  if (email && password.toLowerCase() === email.trim().toLowerCase()) errors.push('diferente do email');
  return errors;
}
