locals {
  container_name = "mockhub"
  container_port = 8080
  data_uid       = 65532
  https          = var.certificate_arn != null

  environment = merge(
    { MOCKHUB_DATA_DIR = "/data" },
    var.public_url == null ? {} : { MOCKHUB_PUBLIC_URL = var.public_url },
    var.extra_environment,
  )
}

resource "aws_ecs_cluster" "this" {
  name = var.name

  setting {
    name  = "containerInsights"
    value = "enabled"
  }
}

resource "aws_cloudwatch_log_group" "this" {
  name              = "/ecs/${var.name}"
  retention_in_days = var.log_retention_days
}

resource "aws_ecs_task_definition" "this" {
  family                   = var.name
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = var.cpu
  memory                   = var.memory
  execution_role_arn       = aws_iam_role.execution.arn
  task_role_arn            = aws_iam_role.task.arn

  runtime_platform {
    cpu_architecture        = var.cpu_architecture
    operating_system_family = "LINUX"
  }

  volume {
    name = "data"

    efs_volume_configuration {
      file_system_id     = aws_efs_file_system.data.id
      transit_encryption = "ENABLED"

      authorization_config {
        access_point_id = aws_efs_access_point.data.id
        iam             = "ENABLED"
      }
    }
  }

  container_definitions = jsonencode([
    {
      name         = local.container_name
      image        = var.image
      essential    = true
      portMappings = [{ containerPort = local.container_port, protocol = "tcp" }]
      mountPoints  = [{ sourceVolume = "data", containerPath = "/data" }]
      environment  = [for k, v in local.environment : { name = k, value = v }]
      secrets      = var.users_secret_arn == null ? [] : [{ name = "MOCKHUB_USERS", valueFrom = var.users_secret_arn }]
      healthCheck = {
        command     = ["CMD", "/opt/java/bin/java", "-XX:TieredStopAtLevel=1", "-cp", "/opt/mockhub/mockhub.jar", "io.mockhub.HealthCheck"]
        interval    = 15
        timeout     = 5
        retries     = 3
        startPeriod = 30
      }
      logConfiguration = {
        logDriver = "awslogs"
        options = {
          awslogs-group         = aws_cloudwatch_log_group.this.name
          awslogs-region        = var.region
          awslogs-stream-prefix = "mockhub"
        }
      }
    }
  ])
}

resource "aws_ecs_service" "this" {
  name                               = var.name
  cluster                            = aws_ecs_cluster.this.id
  task_definition                    = aws_ecs_task_definition.this.arn
  launch_type                        = "FARGATE"
  desired_count                      = 1
  deployment_minimum_healthy_percent = 0
  deployment_maximum_percent         = 100
  health_check_grace_period_seconds  = 60
  enable_execute_command             = false

  deployment_circuit_breaker {
    enable   = true
    rollback = true
  }

  network_configuration {
    subnets          = var.service_subnet_ids
    security_groups  = [aws_security_group.service.id]
    assign_public_ip = false
  }

  load_balancer {
    target_group_arn = aws_lb_target_group.this.arn
    container_name   = local.container_name
    container_port   = local.container_port
  }

  depends_on = [aws_lb_listener.http, aws_efs_mount_target.data]
}
