mock_provider "aws" {
  mock_data "aws_iam_policy_document" {
    defaults = {
      json = "{\"Version\":\"2012-10-17\",\"Statement\":[]}"
    }
  }
}

variables {
  region                   = "eu-west-1"
  vpc_id                   = "vpc-123"
  load_balancer_subnet_ids = ["subnet-a", "subnet-b"]
  service_subnet_ids       = ["subnet-c", "subnet-d"]
}

run "http_only_by_default" {
  command = plan

  assert {
    condition     = length(aws_lb_listener.https) == 0
    error_message = "No HTTPS listener without a certificate."
  }

  assert {
    condition     = aws_lb_listener.http.default_action[0].type == "forward"
    error_message = "HTTP should forward to MockHub when there is no certificate."
  }

  assert {
    condition     = aws_lb.this.internal
    error_message = "The load balancer is internal by default."
  }

  assert {
    condition     = length(aws_iam_role_policy.read_users_secret) == 0
    error_message = "No secret access without a users secret."
  }
}

run "https_redirects_and_users_secret_is_wired" {
  command = plan

  variables {
    certificate_arn  = "arn:aws:acm:eu-west-1:123456789012:certificate/abc"
    users_secret_arn = "arn:aws:secretsmanager:eu-west-1:123456789012:secret:users-AbCdEf"
    public_url       = "https://mocks.example.com"
  }

  assert {
    condition     = length(aws_lb_listener.https) == 1
    error_message = "HTTPS listener expected with a certificate."
  }

  assert {
    condition     = aws_lb_listener.http.default_action[0].type == "redirect"
    error_message = "HTTP should redirect to HTTPS."
  }

  assert {
    condition     = jsondecode(aws_ecs_task_definition.this.container_definitions)[0].secrets[0].name == "MOCKHUB_USERS"
    error_message = "MOCKHUB_USERS must come from Secrets Manager."
  }

  assert {
    condition     = contains([for e in jsondecode(aws_ecs_task_definition.this.container_definitions)[0].environment : e.value if e.name == "MOCKHUB_PUBLIC_URL"], "https://mocks.example.com")
    error_message = "MOCKHUB_PUBLIC_URL must be passed to the container."
  }
}

run "single_writer_deployment_with_efs_per_subnet" {
  command = plan

  assert {
    condition     = aws_ecs_service.this.desired_count == 1 && aws_ecs_service.this.deployment_maximum_percent == 100
    error_message = "Only one task may write to the data volume at a time."
  }

  assert {
    condition     = length(aws_efs_mount_target.data) == 2
    error_message = "One EFS mount target per service subnet."
  }

  assert {
    condition     = aws_efs_access_point.data.posix_user[0].uid == 65532
    error_message = "EFS access point must match the container's non-root user."
  }
}

run "rejects_a_single_load_balancer_subnet" {
  command = plan

  variables {
    load_balancer_subnet_ids = ["subnet-a"]
  }

  expect_failures = [var.load_balancer_subnet_ids]
}
