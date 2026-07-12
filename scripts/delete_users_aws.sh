#!/usr/bin/env bash
set -euo pipefail

export AWS_PROFILE=root-admin   # identidade admin, não os próprios users

USERS=("tms-springboot-dev" "delivery-springboot-dev")

for USER in "${USERS[@]}"; do
  echo "== Processando $USER =="

  if ! aws iam get-user --user-name "$USER" >/dev/null 2>&1; then
    echo "  User $USER não existe, a saltar."
    continue
  fi

  # 1. Desanexar policies geridas
  for policy_arn in $(aws iam list-attached-user-policies --user-name "$USER" \
    --query 'AttachedPolicies[].PolicyArn' --output text); do
    echo "  detach $policy_arn"
    aws iam detach-user-policy --user-name "$USER" --policy-arn "$policy_arn"
  done

  # 2. Apagar policies inline
  for policy_name in $(aws iam list-user-policies --user-name "$USER" \
    --query 'PolicyNames[]' --output text); do
    echo "  delete inline policy $policy_name"
    aws iam delete-user-policy --user-name "$USER" --policy-name "$policy_name"
  done

  # 3. Apagar access keys
  for key_id in $(aws iam list-access-keys --user-name "$USER" \
    --query 'AccessKeyMetadata[].AccessKeyId' --output text); do
    echo "  delete access key $key_id"
    aws iam delete-access-key --user-name "$USER" --access-key-id "$key_id"
  done

  # 4. Remover MFA devices
  for mfa in $(aws iam list-mfa-devices --user-name "$USER" \
    --query 'MFADevices[].SerialNumber' --output text); do
    echo "  deactivate MFA $mfa"
    aws iam deactivate-mfa-device --user-name "$USER" --serial-number "$mfa"
    aws iam delete-virtual-mfa-device --serial-number "$mfa" 2>/dev/null || true
  done

  # 5. Remover de grupos
  for group in $(aws iam list-groups-for-user --user-name "$USER" \
    --query 'Groups[].GroupName' --output text); do
    echo "  remove from group $group"
    aws iam remove-user-from-group --user-name "$USER" --group-name "$group"
  done

  # 6. Login profile (password console), se existir
  aws iam delete-login-profile --user-name "$USER" 2>/dev/null || true

  # 7. Signing certs / SSH keys / service-specific creds (raro, mas seguro verificar)
  for cert in $(aws iam list-signing-certificates --user-name "$USER" \
    --query 'Certificates[].CertificateId' --output text); do
    aws iam delete-signing-certificate --user-name "$USER" --certificate-id "$cert"
  done

  # 8. Finalmente, o user
  aws iam delete-user --user-name "$USER"
  echo "  $USER apagado."
done

echo "Concluído."