# Infra AWS — Yala backend

Setup mínimo para correr el backend en AWS (us-east-1) usando RDS + ECS Fargate + ECR + ALB + Route 53 + ACM.

**URL pública:** https://yala.dpdns.org

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

## HTTPS + dominio (después del primer deploy)

Una vez que `yala-svc` esté corriendo el backend (no el bootstrap nginx), agregar
ALB + Route 53 + ACM con un solo script:

```bash
./infra/setup-https.sh
```

El script:

1. Crea la hosted zone `yala.dpdns.org` en Route 53. Imprime los 4 NS y **pausa
   hasta que pegues esos NS en el panel de digitalplat.org** para el host `yala`.
2. Solicita un cert ACM (us-east-1) con validación DNS y crea el CNAME de
   validación en Route 53. Espera a que el cert pase a `ISSUED` (5–30 min).
3. Crea `yala-alb-sg` (inbound 80/443 público), target group `yala-tg`, ALB
   `yala-alb` internet-facing en las 6 subnets públicas de la default VPC.
4. Crea listener `:80` con redirect 301 → HTTPS y listener `:443` con el cert
   ACM forwardeando al target group.
5. Asocia el ECS service al target group (fuerza redeploy y espera estable).
6. **Cierra el SG del task**: revoca el `0.0.0.0/0` sobre 8081 y autoriza solo
   tráfico desde el SG del ALB.
7. Crea ALIAS A `yala.dpdns.org` → ALB.
8. Actualiza `/yala/prod/app-base-url` a `https://yala.dpdns.org` en SSM (lo
   lee `EventListeners.java` para los links de los emails).

## Verificar deploy

```bash
curl -sI http://yala.dpdns.org/ | head -1          # 301 Moved Permanently
curl -s https://yala.dpdns.org/v3/api-docs         # 200 + JSON
curl -s https://yala.dpdns.org/api/v1/categories   # 200 + JSON
open https://yala.dpdns.org/swagger-ui/index.html  # Swagger UI
```

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
| IPv4 público (task) | ~3.65 |
| ALB (idle + LCU mínimo) | ~19 |
| Route 53 hosted zone | ~0.50 |
| ACM cert | 0 |
| CloudWatch Logs (1 GB) | ~0.50 |
| ECR storage (200 MB) | ~0.02 |
| **Total** | **~57** |

Apagando fuera de demo days (`desiredCount=0` + `stop-db-instance`): ~25 USD/mes
(el ALB sigue cobrando idle aunque no haya targets).

## Limpieza total

```bash
# ALB + TG + zona + cert
aws elbv2 delete-load-balancer --load-balancer-arn $(aws elbv2 describe-load-balancers --names yala-alb --query 'LoadBalancers[0].LoadBalancerArn' --output text) --region us-east-1
aws elbv2 delete-target-group  --target-group-arn  $(aws elbv2 describe-target-groups  --names yala-tg  --query 'TargetGroups[0].TargetGroupArn'  --output text) --region us-east-1
# (la hosted zone y el cert ACM se borran manualmente o con scripts ad-hoc)

# ECS + RDS + ECR
aws ecs update-service --cluster yala --service yala-svc --desired-count 0 --region us-east-1
aws ecs delete-service --cluster yala --service yala-svc --force --region us-east-1
aws ecs delete-cluster --cluster yala --region us-east-1
aws rds delete-db-instance --db-instance-identifier yala-db --skip-final-snapshot --region us-east-1
aws ecr delete-repository --repository-name yala-backend --force --region us-east-1
# Borrar IAM roles / SSM params / security groups manualmente desde consola si querés.
```
