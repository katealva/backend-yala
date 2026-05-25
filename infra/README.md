# Infra AWS — Yala backend

Setup mínimo para correr el backend en AWS (us-east-1) usando RDS + ECS Fargate + ECR.

## Pre-requisitos

- AWS CLI v2 configurado (`aws configure`) con credenciales de admin de la cuenta destino.
- `openssl`, `curl`, `bash`.
- Repo GitHub `katealva/backend-yala` con permiso para escribir secrets.

## Provisión inicial (una sola vez)

```bash
./infra/setup-aws.sh
```

El script es idempotente y crea, si no existen:

| Recurso | Detalle |
|---|---|
| ECR repo | `yala-backend`, lifecycle policy (max 5 imágenes) |
| Security groups | `yala-ecs-sg` (8081 inbound público), `yala-rds-sg` (5432 desde ECS + IP del dev) |
| RDS PostgreSQL | `yala-db`, db.t4g.micro, 20 GB gp3, single AZ, público |
| SSM Parameter Store | `/yala/prod/*` (algunos pedidos interactivamente) |
| CloudWatch log group | `/ecs/yala-backend` (retention 7 días) |
| IAM roles | `yala-ecs-task-execution-role`, `yala-ecs-task-role`, `yala-github-actions-deploy-role` |
| OIDC provider | `token.actions.githubusercontent.com` |
| ECS cluster | `yala` (Fargate only) |
| ECS service | `yala-svc` (desiredCount=1, IP pública, bootstrap con imagen nginx) |

Al final imprime el ARN del rol que GitHub Actions debe asumir.

## Configurar GitHub

### Secret obligatorio para el deploy

1. Repo Settings → Secrets and variables → Actions → New repository secret:
   - **Name**: `AWS_DEPLOY_ROLE_ARN`
   - **Value**: el ARN que imprimió `setup-aws.sh`
2. Mergea `develop` → `main` para disparar el primer deploy real (el workflow
   `.github/workflows/cd.yml` reemplaza la imagen bootstrap por la del backend).

### Secret opcional — auto-update del homepage del repo

El último step del workflow CD intenta actualizar el campo **Website** del repo
(el link clickeable arriba del README) con la URL pública del último deploy. El
`GITHUB_TOKEN` por default no puede modificar settings del repo, así que requiere
un Personal Access Token (PAT) propio:

1. https://github.com/settings/personal-access-tokens → **Generate new token**
   (fine-grained).
2. **Repository access**: Only select repositories → `katealva/backend-yala`.
3. **Repository permissions**: Administration → **Read and write**.
4. Generar y copiar el token.
5. Repo Settings → Secrets and variables → Actions → New repository secret:
   - **Name**: `REPO_HOMEPAGE_TOKEN`
   - **Value**: el token recién creado.

Si el secret no está configurado, el step se salta silenciosamente y el deploy
sigue siendo exitoso — solo se pierde la auto-actualización del homepage.

## Verificar deploy

```bash
TASK=$(aws ecs list-tasks --cluster yala --service-name yala-svc \
  --region us-east-1 --query 'taskArns[0]' --output text)
ENI=$(aws ecs describe-tasks --cluster yala --tasks $TASK --region us-east-1 \
  --query "tasks[0].attachments[0].details[?name=='networkInterfaceId'].value" \
  --output text)
IP=$(aws ec2 describe-network-interfaces --network-interface-ids $ENI \
  --region us-east-1 --query 'NetworkInterfaces[0].Association.PublicIp' --output text)
echo "http://$IP:8081/swagger-ui/index.html"
```

Verifica `http://<ip>:8081/v3/api-docs.yaml` (debería responder 200).

## Apagar para ahorrar

```bash
aws ecs update-service --cluster yala --service yala-svc --desired-count 0 --region us-east-1
aws rds stop-db-instance --db-instance-identifier yala-db --region us-east-1
```

Para volver a encender: `--desired-count 1` y `start-db-instance`.

## Costos estimados (24/7, sin free tier)

| Recurso | USD/mes |
|---|---|
| RDS db.t4g.micro + 20 GB gp3 | ~15 |
| ECS Fargate 0.5 vCPU + 1 GB RAM | ~18 |
| IPv4 público | ~3.65 |
| CloudWatch Logs (1 GB) | ~0.50 |
| ECR storage (200 MB) | ~0.02 |
| **Total** | **~37** |

Apagando fuera de demo days: ~10–15 USD/mes.

## Limpieza total

```bash
aws ecs update-service --cluster yala --service yala-svc --desired-count 0 --region us-east-1
aws ecs delete-service --cluster yala --service yala-svc --force --region us-east-1
aws ecs delete-cluster --cluster yala --region us-east-1
aws rds delete-db-instance --db-instance-identifier yala-db --skip-final-snapshot --region us-east-1
aws ecr delete-repository --repository-name yala-backend --force --region us-east-1
# Borrar IAM roles / SSM params / security groups manualmente desde consola si querés.
```
