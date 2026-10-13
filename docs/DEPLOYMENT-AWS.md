# Deploying MockHub to AWS ECS

The Terraform module in `deploy/terraform` creates:

| Resource | Why |
|---|---|
| ECS cluster + Fargate service (1 task, ARM64) | runs `abhaypratap/mockhub` |
| EFS file system + access point (uid 65532), encrypted, backups on | `/data`, so projects and stubs survive restarts, redeploys and task replacement. Protected from `terraform destroy`. |
| Application Load Balancer, target group on `/__admin/health` | one URL for the UI, the API and every project's mocks |
| Optional HTTPS listener with HTTP→HTTPS redirect | pass an ACM certificate |
| Security groups | ALB ← your CIDR; task ← ALB only; EFS ← task only |
| CloudWatch log group (30 days) | container logs |

## Before you start

- A VPC with at least two private subnets in different AZs, able to pull from Docker Hub (NAT gateway). If it can't,
  mirror the image to ECR and pass that URI as `image`.
- Two subnets for the load balancer: private for an `internal` ALB (the default, for test environments), public for
  `internet-facing`.
- Optional: an ACM certificate for your hostname, e.g. `mocks.acme.internal`.

## 1. Store the users (optional)

```bash
aws secretsmanager create-secret --name mockhub/users \
  --secret-string 'lead:CHANGE-ME:admin,qa:CHANGE-ME:editor,dev:CHANGE-ME:viewer'
```

Keep the returned ARN. Passwords can be stored as `sha256:<hex>` instead of plain text.

## 2. Deploy

```bash
cd deploy/terraform
cp terraform.tfvars.example terraform.tfvars   # fill in VPC, subnets, certificate, secret
terraform init
terraform plan
terraform apply
terraform output url
```

Point a DNS record (e.g. `mocks.acme.internal`) at `load_balancer_dns_name`. For Route 53, use an alias record with
`load_balancer_zone_id`.

Use a remote backend for shared state, e.g. add a `backend.tf`:

```hcl
terraform {
  backend "s3" {
    bucket         = "acme-terraform-state"
    key            = "mockhub/terraform.tfstate"
    region         = "eu-west-1"
    dynamodb_table = "terraform-locks"
    encrypt        = true
  }
}
```

The directory can also be used as a module:

```hcl
module "mockhub" {
  source = "git::https://github.com/monk-developer-tools/mockhub.git//deploy/terraform?ref=v1.0.0"

  region                   = "eu-west-1"
  vpc_id                   = module.vpc.vpc_id
  load_balancer_subnet_ids = module.vpc.private_subnets
  service_subnet_ids       = module.vpc.private_subnets
  image                    = "abhaypratap/mockhub:1.0.0"
}
```

(When used as a module, remove the `provider "aws"` block from `versions.tf` or pass providers explicitly.)

## Variables

| Variable | Default | |
|---|---|---|
| `region` | | AWS region |
| `vpc_id`, `load_balancer_subnet_ids`, `service_subnet_ids` | | Network placement |
| `internal` | `true` | Internal or internet-facing ALB |
| `allowed_cidrs` | `["10.0.0.0/8"]` | Who may reach the ALB |
| `image` | `abhaypratap/mockhub:latest` | Pin a version in real environments |
| `certificate_arn` | `null` | Enables HTTPS and the HTTP→HTTPS redirect |
| `public_url` | `null` | Sets `MOCKHUB_PUBLIC_URL` |
| `users_secret_arn` | `null` | Sets `MOCKHUB_USERS` from Secrets Manager |
| `cpu`, `memory` | `512`, `1024` | Task size |
| `cpu_architecture` | `ARM64` | `ARM64` (Graviton) or `X86_64` |
| `extra_environment` | `{}` | e.g. `{ WIREMOCK_OPTIONS = "--async-response-enabled true" }` |

## 3. Upgrade

Change `image` and run `terraform apply`. The service stops the old task before starting the new one
(`deployment_minimum_healthy_percent = 0`), so two tasks never write to `/data` at the same time. Expect a few
seconds of downtime. A failed deployment rolls back automatically.

## Testing the module

```bash
cd deploy/terraform
terraform init -backend=false
terraform validate
terraform test        # runs against a mocked AWS provider, no credentials needed
```

## Using it from services in the test environment

Set the dependency's base URL in the service's test configuration to the project's mock URL:

```
PAYMENT_API_URL=https://mocks.acme.internal/payment-service
CRM_SOAP_ENDPOINT=https://mocks.acme.internal/legacy-crm/ws/customers
```

## Single sign-on instead of passwords

To use your identity provider (Okta, Azure AD, Cognito), add an `authenticate-oidc` action to the HTTPS listener
rule (an `aws_lb_listener_rule`) for `/__admin/*` and leave `users_secret_arn` unset. Mocks stay open because they're outside `/__admin`.

## Backups

- EFS backups are on (AWS Backup default plan).
- **Server → Download backup** in the UI, or `GET /__admin/mockhub/api/backup`, gives one JSON file that can be
  restored into any MockHub.

## Sizing

The default of 0.5 vCPU and 1 GB is enough for typical test environments. Raise `Cpu`/`Memory` for heavy
performance tests. The JVM uses 75% of the task memory.
