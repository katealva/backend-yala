#!/usr/bin/env bash
# Provisiona ALB + HTTPS + dominio para el backend de Yala (sobre la infra base
# creada por setup-aws.sh). Idempotente.
#
# Requiere: setup-aws.sh ya corrido (ECS service yala-svc activo).
# Resultado: https://yala.dpdns.org termina TLS en el ALB y forwarda a Fargate.
#
# Pasos manuales:
#   1. Si la hosted zone es nueva, el script imprime los 4 NS y pausa hasta
#      que pegues esos NS en el panel de digitalplat.org para `yala`.
#   2. Continuas y el script espera a que ACM valide y emite el cert.

set -euo pipefail

REGION="us-east-1"
DOMAIN="yala.dpdns.org"
ALB_NAME="yala-alb"
TG_NAME="yala-tg"
ALB_SG_NAME="yala-alb-sg"
ECS_SG_NAME="yala-ecs-sg"
ECS_CLUSTER="yala"
ECS_SERVICE="yala-svc"

ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
echo "==> AWS Account: $ACCOUNT_ID, Region: $REGION, Domain: $DOMAIN"

VPC_ID=$(aws ec2 describe-vpcs --filters Name=is-default,Values=true \
  --query 'Vpcs[0].VpcId' --output text --region "$REGION")
SUBNET_IDS=$(aws ec2 describe-subnets --filters Name=vpc-id,Values="$VPC_ID" \
  Name=map-public-ip-on-launch,Values=true \
  --query 'Subnets[].SubnetId' --output text --region "$REGION")
ECS_SG=$(aws ec2 describe-security-groups --filters Name=vpc-id,Values="$VPC_ID" \
  Name=group-name,Values="$ECS_SG_NAME" --query 'SecurityGroups[0].GroupId' \
  --output text --region "$REGION")
echo "==> VPC: $VPC_ID  ECS SG: $ECS_SG"

# -----------------------------------------------------------------------------
# 1) Route 53 hosted zone para $DOMAIN
# -----------------------------------------------------------------------------
ZONE_ID=$(aws route53 list-hosted-zones-by-name --dns-name "$DOMAIN" \
  --query "HostedZones[?Name=='${DOMAIN}.'].Id" --output text --region "$REGION" | head -1)

if [[ -z "$ZONE_ID" || "$ZONE_ID" == "None" ]]; then
  ZONE_ID=$(aws route53 create-hosted-zone --name "$DOMAIN" \
    --caller-reference "yala-$(date +%s)" \
    --hosted-zone-config Comment="Yala backend public zone" \
    --region "$REGION" --query 'HostedZone.Id' --output text)
  echo "==> Hosted zone creada: $ZONE_ID"
  echo "==> Pega estos NS en el panel de digitalplat.org para el host 'yala':"
  aws route53 get-hosted-zone --id "$ZONE_ID" \
    --query 'DelegationSet.NameServers' --output text | tr '\t' '\n'
  read -rp "Cuando hayas pegado los NS, presiona ENTER para continuar..."
fi
ZONE_ID="${ZONE_ID##*/}"
echo "==> Hosted zone: $ZONE_ID"

# -----------------------------------------------------------------------------
# 2) ACM certificate (DNS validation)
# -----------------------------------------------------------------------------
CERT_ARN=$(aws acm list-certificates --region "$REGION" \
  --query "CertificateSummaryList[?DomainName=='${DOMAIN}'].CertificateArn | [0]" \
  --output text)

if [[ -z "$CERT_ARN" || "$CERT_ARN" == "None" ]]; then
  CERT_ARN=$(aws acm request-certificate --domain-name "$DOMAIN" \
    --validation-method DNS --key-algorithm RSA_2048 \
    --region "$REGION" --query 'CertificateArn' --output text)
  echo "==> Cert solicitado: $CERT_ARN"
  sleep 5
fi

VAL_NAME=$(aws acm describe-certificate --certificate-arn "$CERT_ARN" \
  --region "$REGION" \
  --query 'Certificate.DomainValidationOptions[0].ResourceRecord.Name' --output text)
VAL_VALUE=$(aws acm describe-certificate --certificate-arn "$CERT_ARN" \
  --region "$REGION" \
  --query 'Certificate.DomainValidationOptions[0].ResourceRecord.Value' --output text)

cat > /tmp/yala-acm-val.json <<EOF
{
  "Changes": [{
    "Action": "UPSERT",
    "ResourceRecordSet": {
      "Name": "${VAL_NAME}",
      "Type": "CNAME",
      "TTL": 300,
      "ResourceRecords": [{"Value": "${VAL_VALUE}"}]
    }
  }]
}
EOF
aws route53 change-resource-record-sets --hosted-zone-id "$ZONE_ID" \
  --change-batch file:///tmp/yala-acm-val.json --region "$REGION" >/dev/null

echo "==> Esperando que ACM emita el cert (puede tardar 5-30 min)..."
aws acm wait certificate-validated --certificate-arn "$CERT_ARN" --region "$REGION"
echo "==> Cert ISSUED"

# -----------------------------------------------------------------------------
# 3) SG del ALB (inbound 80 y 443)
# -----------------------------------------------------------------------------
ALB_SG=$(aws ec2 describe-security-groups --filters Name=vpc-id,Values="$VPC_ID" \
  Name=group-name,Values="$ALB_SG_NAME" --query 'SecurityGroups[0].GroupId' \
  --output text --region "$REGION" 2>/dev/null || true)
if [[ -z "$ALB_SG" || "$ALB_SG" == "None" ]]; then
  ALB_SG=$(aws ec2 create-security-group --group-name "$ALB_SG_NAME" \
    --description "Yala ALB SG" --vpc-id "$VPC_ID" \
    --query 'GroupId' --output text --region "$REGION")
fi
aws ec2 authorize-security-group-ingress --group-id "$ALB_SG" --protocol tcp \
  --port 80 --cidr 0.0.0.0/0 --region "$REGION" >/dev/null 2>&1 || true
aws ec2 authorize-security-group-ingress --group-id "$ALB_SG" --protocol tcp \
  --port 443 --cidr 0.0.0.0/0 --region "$REGION" >/dev/null 2>&1 || true
echo "==> ALB SG: $ALB_SG"

# -----------------------------------------------------------------------------
# 4) Target group (type=ip, port 8081)
# -----------------------------------------------------------------------------
TG_ARN=$(aws elbv2 describe-target-groups --names "$TG_NAME" --region "$REGION" \
  --query 'TargetGroups[0].TargetGroupArn' --output text 2>/dev/null || true)
if [[ -z "$TG_ARN" || "$TG_ARN" == "None" ]]; then
  TG_ARN=$(aws elbv2 create-target-group --name "$TG_NAME" \
    --protocol HTTP --port 8081 --vpc-id "$VPC_ID" --target-type ip \
    --health-check-protocol HTTP --health-check-path "/v3/api-docs" \
    --health-check-interval-seconds 30 --health-check-timeout-seconds 10 \
    --healthy-threshold-count 2 --unhealthy-threshold-count 3 \
    --matcher HttpCode=200 --region "$REGION" \
    --query 'TargetGroups[0].TargetGroupArn' --output text)
fi
echo "==> Target group: $TG_ARN"

# -----------------------------------------------------------------------------
# 5) ALB
# -----------------------------------------------------------------------------
ALB_ARN=$(aws elbv2 describe-load-balancers --names "$ALB_NAME" --region "$REGION" \
  --query 'LoadBalancers[0].LoadBalancerArn' --output text 2>/dev/null || true)
if [[ -z "$ALB_ARN" || "$ALB_ARN" == "None" ]]; then
  ALB_ARN=$(aws elbv2 create-load-balancer --name "$ALB_NAME" \
    --type application --scheme internet-facing --ip-address-type ipv4 \
    --subnets $SUBNET_IDS --security-groups "$ALB_SG" \
    --region "$REGION" \
    --query 'LoadBalancers[0].LoadBalancerArn' --output text)
fi
ALB_DNS=$(aws elbv2 describe-load-balancers --load-balancer-arns "$ALB_ARN" \
  --region "$REGION" --query 'LoadBalancers[0].DNSName' --output text)
ALB_ZONE=$(aws elbv2 describe-load-balancers --load-balancer-arns "$ALB_ARN" \
  --region "$REGION" --query 'LoadBalancers[0].CanonicalHostedZoneId' --output text)
echo "==> ALB: $ALB_DNS (zone $ALB_ZONE)"

# -----------------------------------------------------------------------------
# 6) Listeners :80 (redirect) y :443 (forward al TG con cert ACM)
# -----------------------------------------------------------------------------
HAS_80=$(aws elbv2 describe-listeners --load-balancer-arn "$ALB_ARN" --region "$REGION" \
  --query "Listeners[?Port==\`80\`].ListenerArn | [0]" --output text)
if [[ -z "$HAS_80" || "$HAS_80" == "None" ]]; then
  cat > /tmp/yala-l80.json <<'EOF'
[{"Type":"redirect","RedirectConfig":{"Protocol":"HTTPS","Port":"443","Host":"#{host}","Path":"/#{path}","Query":"#{query}","StatusCode":"HTTP_301"}}]
EOF
  aws elbv2 create-listener --load-balancer-arn "$ALB_ARN" \
    --protocol HTTP --port 80 \
    --default-actions file:///tmp/yala-l80.json --region "$REGION" >/dev/null
fi

HAS_443=$(aws elbv2 describe-listeners --load-balancer-arn "$ALB_ARN" --region "$REGION" \
  --query "Listeners[?Port==\`443\`].ListenerArn | [0]" --output text)
if [[ -z "$HAS_443" || "$HAS_443" == "None" ]]; then
  aws elbv2 create-listener --load-balancer-arn "$ALB_ARN" \
    --protocol HTTPS --port 443 \
    --certificates CertificateArn="$CERT_ARN" \
    --ssl-policy ELBSecurityPolicy-TLS13-1-2-2021-06 \
    --default-actions Type=forward,TargetGroupArn="$TG_ARN" \
    --region "$REGION" >/dev/null
fi
echo "==> Listeners :80 (301 -> HTTPS) y :443 (forward TG) listos"

# -----------------------------------------------------------------------------
# 7) Asociar ECS service yala-svc al target group + cerrar SG del task
# -----------------------------------------------------------------------------
LB_COUNT=$(aws ecs describe-services --cluster "$ECS_CLUSTER" --services "$ECS_SERVICE" \
  --region "$REGION" --query 'services[0].loadBalancers | length(@)' --output text)
if [[ "$LB_COUNT" == "0" ]]; then
  aws ecs update-service --cluster "$ECS_CLUSTER" --service "$ECS_SERVICE" \
    --load-balancers "targetGroupArn=${TG_ARN},containerName=app,containerPort=8081" \
    --health-check-grace-period-seconds 120 --force-new-deployment \
    --region "$REGION" >/dev/null
  echo "==> ECS service asociado al TG, esperando rollout..."
  aws ecs wait services-stable --cluster "$ECS_CLUSTER" --services "$ECS_SERVICE" --region "$REGION"
fi

aws ec2 revoke-security-group-ingress --group-id "$ECS_SG" --protocol tcp \
  --port 8081 --cidr 0.0.0.0/0 --region "$REGION" >/dev/null 2>&1 || true
aws ec2 authorize-security-group-ingress --group-id "$ECS_SG" --protocol tcp \
  --port 8081 --source-group "$ALB_SG" --region "$REGION" >/dev/null 2>&1 || true
echo "==> SG ECS cerrado: 8081 ahora solo desde $ALB_SG"

# -----------------------------------------------------------------------------
# 8) ALIAS record $DOMAIN -> ALB
# -----------------------------------------------------------------------------
cat > /tmp/yala-alias.json <<EOF
{
  "Changes": [{
    "Action": "UPSERT",
    "ResourceRecordSet": {
      "Name": "${DOMAIN}.",
      "Type": "A",
      "AliasTarget": {
        "HostedZoneId": "${ALB_ZONE}",
        "DNSName": "${ALB_DNS}.",
        "EvaluateTargetHealth": true
      }
    }
  }]
}
EOF
aws route53 change-resource-record-sets --hosted-zone-id "$ZONE_ID" \
  --change-batch file:///tmp/yala-alias.json --region "$REGION" >/dev/null
echo "==> A ALIAS ${DOMAIN} -> ${ALB_DNS}"

# -----------------------------------------------------------------------------
# 9) SSM app-base-url
# -----------------------------------------------------------------------------
aws ssm put-parameter --name "/yala/prod/app-base-url" --type String \
  --value "https://${DOMAIN}" --overwrite --region "$REGION" >/dev/null
echo "==> SSM /yala/prod/app-base-url = https://${DOMAIN}"

cat <<EOF

============================================================
  Setup HTTPS completo
============================================================
  Domain        : https://${DOMAIN}
  ALB DNS       : ${ALB_DNS}
  Cert ARN      : ${CERT_ARN}
  Target Group  : ${TG_ARN}

  --> Verificar:
      curl -sI http://${DOMAIN}/  | head -1   # 301 a https
      curl -s  https://${DOMAIN}/v3/api-docs  # 200
      curl -s  https://${DOMAIN}/api/v1/categories  # 200 + json
============================================================
EOF
