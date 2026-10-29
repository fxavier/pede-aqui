/// Client-side mirror of the Keycloak realm `passwordPolicy` and the backend
/// `@PasswordPolicy` rule: >= 10 chars, one digit, one upper-case, one
/// lower-case, not equal to the e-mail. Returns the first unmet rule, or null.
String? passwordPolicyError(String password, {required String email}) {
  if (password.length < 10) {
    return 'A palavra-passe deve ter pelo menos 10 caracteres.';
  }
  if (!RegExp(r'\d').hasMatch(password)) {
    return 'A palavra-passe deve conter pelo menos um algarismo.';
  }
  if (!RegExp('[A-Z]').hasMatch(password)) {
    return 'A palavra-passe deve conter pelo menos uma letra maiúscula.';
  }
  if (!RegExp('[a-z]').hasMatch(password)) {
    return 'A palavra-passe deve conter pelo menos uma letra minúscula.';
  }
  if (password.toLowerCase() == email.trim().toLowerCase()) {
    return 'A palavra-passe não pode ser igual ao email.';
  }
  return null;
}
