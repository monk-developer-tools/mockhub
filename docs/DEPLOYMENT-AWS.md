# Deploying MockHub to AWS ECS

The template `deploy/aws/mockhub-ecs.yaml` creates:

| Resource | Why |
|---|---|
| ECS cluster + Fargate service (1 task, ARM64) | runs `abhaypratap/mockhub` |
| EFS file system + access point (uid 65532), encrypted, backups on | `/data`, so projects and stubs survive restarts, redeploys and task replacement. Kept if the stack is deleted. |
| Application Load Balancer, target group on `/__admin/health` | one URL for the UI, the API and every project's mocks |
| Optional HTTPS listener with HTTP→HTTPS redirect | pass an ACM certificate |
| Security groups | ALB ← your CIDR; task ← ALB only; EFS ← task only |
| CloudWatch log group (30 days) | container logs |

## Before you start

- A VPC with at least two private subnets in different AZs, able to pull from Docker Hub (NAT gateway). If it can't,
  mirror the image to ECR and pass that URI as `Image`.
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
aws cloudformation deploy \
  --stack-name mockhub \
  --template-file deploy/aws/mockhub-ecs.yaml \
  --capabilities CAPABILITY_IAM \
  --parameter-overrides \
      VpcId=vpc-0123 \
      LoadBalancerSubnets=subnet-a,subnet-b \
      ServiceSubnets=subnet-c,subnet-d \
      LoadBalancerScheme=internal \
      AllowedCidr=10.0.0.0/8 \
      Image=abhaypratap/mockhub:1.0.0 \
      CertificateArn=arn:aws:acm:eu-west-1:123456789012:certificate/... \
      PublicUrl=https://mocks.acme.internal \
      UsersSecretArn=arn:aws:secretsmanager:eu-west-1:123456789012:secret:mockhub/users-AbCdEf

aws cloudformation describe-stacks --stack-name mockhub --query "Stacks[0].Outputs"
```

Point a DNS record (e.g. `mocks.acme.internal`) at `LoadBalancerDnsName`.

## 3. Upgrade

Deploy again with a new `Image` tag. The service stops the old task before starting the new one
(`MinimumHealthyPercent: 0`), so two tasks never write to `/data` at the same time. Expect a few seconds of
downtime. A failed deployment rolls back automatically.

## Using it from services in the test environment

Set the dependency's base URL in the service's test configuration to the project's mock URL:

```
PAYMENT_API_URL=https://mocks.acme.internal/payment-service
CRM_SOAP_ENDPOINT=https://mocks.acme.internal/legacy-crm/ws/customers
```

## Single sign-on instead of passwords

To use your identity provider (Okta, Azure AD, Cognito), add an `authenticate-oidc` action to the HTTPS listener
rule for `/__admin/*` and leave `UsersSecretArn` empty. Mocks stay open because they're outside `/__admin`.

## Backups

- EFS backups are on (AWS Backup default plan).
- **Server → Download backup** in the UI, or `GET /__admin/mockhub/api/backup`, gives one JSON file that can be
  restored into any MockHub.

## Sizing

The default of 0.5 vCPU and 1 GB is enough for typical test environments. Raise `Cpu`/`Memory` for heavy
performance tests. The JVM uses 75% of the task memory.
