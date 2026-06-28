#!/usr/bin/env bash
# Provisiona la infra mínima de Yala en AWS (us-east-1):
#   ECR + RDS + ECS Fargate + IAM (incluido OIDC para GitHub) + SSM secrets.
# Idempotente: chequea cada recurso antes de crearlo.
# Requisitos: AWS CLI v2 configurado con permisos de admin.

set -euo pipefail

REGION="us-east-1"
ECR_REPO="yala-backend"
ECS_CLUSTER="yala"
ECS_SERVICE="yala-svc"
TASK_FAMILY="yala-backend"
LOG_GROUP="/ecs/yala-backend"
DB_INSTANCE="yala-db"
DB_NAME="yala"
DB_USER="yala_admin"
GITHUB_REPO="katealva/backend-yala"

ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
echo "==> AWS Account: $ACCOUNT_ID, Region: $REGION"

# -----------------------------------------------------------------------------
# 1) ECR repo + lifecycle (max 5 imágenes)
# -----------------------------------------------------------------------------
echo "==> ECR repo: $ECR_REPO"
aws ecr describe-repositories --repository-names "$ECR_REPO" --region "$REGION" >/dev/null 2>&1 \
  || aws ecr create-repository --repository-name "$ECR_REPO" --region "$REGION" \
       --image-scanning-configuration scanOnPush=true >/dev/null

aws ecr put-lifecycle-policy --repository-name "$ECR_REPO" --region "$REGION" \
  --lifecycle-policy-text '{"rules":[{"rulePriority":1,"description":"keep 5 latest","selection":{"tagStatus":"any","countType":"imageCountMoreThan","countNumber":5},"action":{"type":"expire"}}]}' >/dev/null

# -----------------------------------------------------------------------------
# 2) Default VPC + subnets + security groups
# -----------------------------------------------------------------------------
VPC_ID=$(aws ec2 describe-vpcs --filters Name=is-default,Values=true \
  --query 'Vpcs[0].VpcId' --output text --region "$REGION")
SUBNET_IDS=$(aws ec2 describe-subnets --filters Name=vpc-id,Values="$VPC_ID" \
  Name=map-public-ip-on-launch,Values=true \
  --query 'Subnets[].SubnetId' --output text --region "$REGION")
echo "==> VPC: $VPC_ID  Subnets: $SUBNET_IDS"

create_sg () {
  local name="$1" desc="$2"
  local id
  id=$(aws ec2 describe-security-groups --filters Name=vpc-id,Values="$VPC_ID" \
       Name=group-name,Values="$name" --query 'SecurityGroups[0].GroupId' \
       --output text --region "$REGION" 2>/dev/null || true)
  if [[ -z "$id" || "$id" == "None" ]]; then
    id=$(aws ec2 create-security-group --group-name "$name" --description "$desc" \
         --vpc-id "$VPC_ID" --query 'GroupId' --output text --region "$REGION")
  fi
  echo "$id"
}

ECS_SG=$(create_sg yala-ecs-sg "Yala ECS task SG")
RDS_SG=$(create_sg yala-rds-sg "Yala RDS SG")
echo "==> SG ECS: $ECS_SG  SG RDS: $RDS_SG"

aws ec2 authorize-security-group-ingress --group-id "$ECS_SG" --protocol tcp \
  --port 8081 --cidr 0.0.0.0/0 --region "$REGION" >/dev/null 2>&1 || true

aws ec2 authorize-security-group-ingress --group-id "$RDS_SG" --protocol tcp \
  --port 5432 --source-group "$ECS_SG" --region "$REGION" >/dev/null 2>&1 || true

DEV_IP=$(curl -fsS https://checkip.amazonaws.com 2>/dev/null | tr -d '\n' || true)
if [[ -n "$DEV_IP" ]]; then
  aws ec2 authorize-security-group-ingress --group-id "$RDS_SG" --protocol tcp \
    --port 5432 --cidr "$DEV_IP/32" --region "$REGION" >/dev/null 2>&1 || true
  echo "==> Whitelisted dev IP $DEV_IP/32 on RDS SG"
fi

# -----------------------------------------------------------------------------
# 3) RDS PostgreSQL (db.t4g.micro, 20GB, single AZ, public access)
# -----------------------------------------------------------------------------
echo "==> RDS: $DB_INSTANCE"
if ! aws rds describe-db-instances --db-instance-identifier "$DB_INSTANCE" \
     --region "$REGION" >/dev/null 2>&1; then
  DB_PASS=$(openssl rand -base64 24 | tr -d '/=+\n' | cut -c1-24)
  aws rds create-db-instance \
    --db-instance-identifier "$DB_INSTANCE" \
    --db-instance-class db.t4g.micro \
    --engine postgres --engine-version 16.4 \
    --master-username "$DB_USER" --master-user-password "$DB_PASS" \
    --allocated-storage 20 --storage-type gp3 \
    --db-name "$DB_NAME" --vpc-security-group-ids "$RDS_SG" \
    --publicly-accessible --backup-retention-period 0 \
    --no-multi-az --no-deletion-protection \
    --region "$REGION" >/dev/null
  echo "==> RDS creandose (5-10 min)... guardando password en SSM"
  aws ssm put-parameter --name /yala/prod/db-password --type SecureString \
    --value "$DB_PASS" --overwrite --region "$REGION" >/dev/null
  aws rds wait db-instance-available --db-instance-identifier "$DB_INSTANCE" --region "$REGION"
fi

DB_HOST=$(aws rds describe-db-instances --db-instance-identifier "$DB_INSTANCE" \
  --query 'DBInstances[0].Endpoint.Address' --output text --region "$REGION")
echo "==> RDS endpoint: $DB_HOST"

# -----------------------------------------------------------------------------
# 4) SSM Parameter Store (secrets para la task)
# -----------------------------------------------------------------------------
put_param () {
  aws ssm put-parameter --name "$1" --type "$2" --value "$3" --overwrite \
    --region "$REGION" >/dev/null
}
put_param /yala/prod/db-host        String       "$DB_HOST"
put_param /yala/prod/db-port        String       "5432"
put_param /yala/prod/db-name        String       "$DB_NAME"
put_param /yala/prod/db-user        String       "$DB_USER"
put_param /yala/prod/aws-s3-bucket  String       "yala-bucket-prod"
put_param /yala/prod/app-base-url   String       "http://localhost:8081"
put_param /yala/prod/app-web-url    String       "https://main.d3lg9y66c0qtho.amplifyapp.com"

prompt_secret () {
  local key="$1" label="$2" existing
  existing=$(aws ssm get-parameter --name "$key" --with-decryption \
             --query 'Parameter.Value' --output text --region "$REGION" 2>/dev/null || echo "")
  if [[ -z "$existing" || "$existing" == "None" ]]; then
    read -rsp "  $label: " val; echo
    put_param "$key" SecureString "$val"
  fi
}
prompt_secret /yala/prod/jwt-secret       "JWT secret (>= 32 chars)"
prompt_secret /yala/prod/resend-api-key   "Resend API key"
prompt_secret /yala/prod/resend-from      "Resend from address"
prompt_secret /yala/prod/mp-access-token  "MercadoPago access token"
prompt_secret /yala/prod/mp-public-key    "MercadoPago public key"
prompt_secret /yala/prod/livekit-url        "LiveKit URL (wss://...)"
prompt_secret /yala/prod/livekit-api-key    "LiveKit API key"
prompt_secret /yala/prod/livekit-api-secret "LiveKit API secret"
prompt_secret /yala/prod/jsonpe-api-key      "JSON.pe API key (validación DNI)"
prompt_secret /yala/prod/didit-api-key       "Didit API key (KYC vendedores)"
prompt_secret /yala/prod/didit-webhook-secret "Didit webhook secret"

# -----------------------------------------------------------------------------
# 5) CloudWatch log group
# -----------------------------------------------------------------------------
aws logs describe-log-groups --log-group-name-prefix "$LOG_GROUP" --region "$REGION" \
  --query 'logGroups[0].logGroupName' --output text 2>/dev/null | grep -q "$LOG_GROUP" \
  || aws logs create-log-group --log-group-name "$LOG_GROUP" --region "$REGION"
aws logs put-retention-policy --log-group-name "$LOG_GROUP" --retention-in-days 7 \
  --region "$REGION" >/dev/null

# -----------------------------------------------------------------------------
# 6) IAM roles
# -----------------------------------------------------------------------------
ECS_TRUST='{"Version":"2012-10-17","Statement":[{"Effect":"Allow","Principal":{"Service":"ecs-tasks.amazonaws.com"},"Action":"sts:AssumeRole"}]}'

create_role () {
  local name="$1" trust="$2"
  aws iam get-role --role-name "$name" >/dev/null 2>&1 \
    || aws iam create-role --role-name "$name" --assume-role-policy-document "$trust" >/dev/null
}

create_role yala-ecs-task-execution-role "$ECS_TRUST"
aws iam attach-role-policy --role-name yala-ecs-task-execution-role \
  --policy-arn arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy >/dev/null 2>&1 || true

SSM_READ_POLICY=$(cat <<EOF
{"Version":"2012-10-17","Statement":[
  {"Effect":"Allow","Action":["ssm:GetParameter","ssm:GetParameters"],"Resource":"arn:aws:ssm:${REGION}:${ACCOUNT_ID}:parameter/yala/prod/*"}
]}
EOF
)
aws iam put-role-policy --role-name yala-ecs-task-execution-role \
  --policy-name yala-ssm-read --policy-document "$SSM_READ_POLICY" >/dev/null

create_role yala-ecs-task-role "$ECS_TRUST"
S3_POLICY=$(cat <<EOF
{"Version":"2012-10-17","Statement":[
  {"Effect":"Allow","Action":["s3:PutObject","s3:GetObject","s3:DeleteObject"],"Resource":"arn:aws:s3:::yala-bucket-prod/*"},
  {"Effect":"Allow","Action":["s3:ListBucket"],"Resource":"arn:aws:s3:::yala-bucket-prod"}
]}
EOF
)
aws iam put-role-policy --role-name yala-ecs-task-role \
  --policy-name yala-s3 --policy-document "$S3_POLICY" >/dev/null

# OIDC provider para GitHub Actions
OIDC_ARN="arn:aws:iam::${ACCOUNT_ID}:oidc-provider/token.actions.githubusercontent.com"
aws iam get-open-id-connect-provider --open-id-connect-provider-arn "$OIDC_ARN" >/dev/null 2>&1 \
  || aws iam create-open-id-connect-provider \
       --url https://token.actions.githubusercontent.com \
       --client-id-list sts.amazonaws.com \
       --thumbprint-list 6938fd4d98bab03faadb97b34396831e3780aea1 >/dev/null

TRUST_RENDERED=$(sed "s/__ACCOUNT_ID__/${ACCOUNT_ID}/g" "$(dirname "$0")/github-oidc-trust-policy.json")
create_role yala-github-actions-deploy-role "$TRUST_RENDERED"
aws iam update-assume-role-policy --role-name yala-github-actions-deploy-role \
  --policy-document "$TRUST_RENDERED" >/dev/null

DEPLOY_POLICY=$(cat <<EOF
{"Version":"2012-10-17","Statement":[
  {"Effect":"Allow","Action":["ecr:GetAuthorizationToken"],"Resource":"*"},
  {"Effect":"Allow","Action":["ecr:BatchCheckLayerAvailability","ecr:CompleteLayerUpload","ecr:InitiateLayerUpload","ecr:PutImage","ecr:UploadLayerPart","ecr:BatchGetImage"],"Resource":"arn:aws:ecr:${REGION}:${ACCOUNT_ID}:repository/${ECR_REPO}"},
  {"Effect":"Allow","Action":["ecs:DescribeServices","ecs:UpdateService","ecs:RegisterTaskDefinition","ecs:DescribeTaskDefinition","ecs:ListTasks","ecs:DescribeTasks"],"Resource":"*"},
  {"Effect":"Allow","Action":["ec2:DescribeNetworkInterfaces"],"Resource":"*"},
  {"Effect":"Allow","Action":["iam:PassRole"],"Resource":["arn:aws:iam::${ACCOUNT_ID}:role/yala-ecs-task-execution-role","arn:aws:iam::${ACCOUNT_ID}:role/yala-ecs-task-role"]}
]}
EOF
)
aws iam put-role-policy --role-name yala-github-actions-deploy-role \
  --policy-name yala-deploy --policy-document "$DEPLOY_POLICY" >/dev/null

# -----------------------------------------------------------------------------
# 7) ECS cluster + task definition inicial + service
# -----------------------------------------------------------------------------
aws ecs describe-clusters --clusters "$ECS_CLUSTER" --region "$REGION" \
  --query 'clusters[0].status' --output text 2>/dev/null | grep -q ACTIVE \
  || aws ecs create-cluster --cluster-name "$ECS_CLUSTER" --region "$REGION" >/dev/null

# Renderiza task-definition.json (placeholder de imagen = nginx para bootstrap)
TMPL="$(dirname "$0")/task-definition.json"
RENDERED=$(sed -e "s/__ACCOUNT_ID__/${ACCOUNT_ID}/g" \
               -e "s|__IMAGE_URI__|public.ecr.aws/nginx/nginx:stable|g" "$TMPL")
TASK_ARN=$(aws ecs register-task-definition --cli-input-json "$RENDERED" \
  --query 'taskDefinition.taskDefinitionArn' --output text --region "$REGION")
echo "==> Bootstrap task definition: $TASK_ARN"

SUBNET_CSV=$(echo "$SUBNET_IDS" | tr '\t' ',' | tr ' ' ',')
if aws ecs describe-services --cluster "$ECS_CLUSTER" --services "$ECS_SERVICE" \
     --region "$REGION" --query 'services[0].status' --output text 2>/dev/null | grep -q ACTIVE; then
  echo "==> ECS service ya existe ($ECS_SERVICE)"
else
  aws ecs create-service --cluster "$ECS_CLUSTER" --service-name "$ECS_SERVICE" \
    --task-definition "$TASK_ARN" --desired-count 1 --launch-type FARGATE \
    --network-configuration "awsvpcConfiguration={subnets=[$SUBNET_CSV],securityGroups=[$ECS_SG],assignPublicIp=ENABLED}" \
    --region "$REGION" >/dev/null
fi

# -----------------------------------------------------------------------------
# Resumen
# -----------------------------------------------------------------------------
ROLE_ARN="arn:aws:iam::${ACCOUNT_ID}:role/yala-github-actions-deploy-role"
cat <<EOF

============================================================
  Setup AWS completo
============================================================
  Region            : $REGION
  ECR repo          : ${ACCOUNT_ID}.dkr.ecr.${REGION}.amazonaws.com/${ECR_REPO}
  ECS cluster       : $ECS_CLUSTER
  ECS service       : $ECS_SERVICE
  RDS endpoint      : $DB_HOST
  Log group         : $LOG_GROUP

  --> Configura este secret en GitHub:
      AWS_DEPLOY_ROLE_ARN = $ROLE_ARN

  --> Primer deploy real: merge develop -> main para disparar CD.

  --> Para ver la URL pública de la app:
      aws ecs list-tasks --cluster $ECS_CLUSTER --service-name $ECS_SERVICE \\
        --region $REGION
      aws ecs describe-tasks --cluster $ECS_CLUSTER --tasks <task-arn> \\
        --region $REGION --query 'tasks[0].attachments[0].details'

  --> Apagar para ahorrar:
      aws ecs update-service --cluster $ECS_CLUSTER --service $ECS_SERVICE --desired-count 0
      aws rds stop-db-instance --db-instance-identifier $DB_INSTANCE

============================================================
EOF
