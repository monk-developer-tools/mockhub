variable "region" {
  description = "AWS region to deploy into."
  type        = string
}

variable "name" {
  description = "Name prefix for every resource."
  type        = string
  default     = "mockhub"
}

variable "vpc_id" {
  description = "VPC to deploy into."
  type        = string
}

variable "load_balancer_subnet_ids" {
  description = "At least two subnets in different AZs for the load balancer (private for an internal ALB, public for internet-facing)."
  type        = list(string)

  validation {
    condition     = length(var.load_balancer_subnet_ids) >= 2
    error_message = "The load balancer needs at least two subnets in different availability zones."
  }
}

variable "service_subnet_ids" {
  description = "Private subnets for the task and the EFS mount targets. They need a NAT gateway or VPC endpoints to pull the image."
  type        = list(string)

  validation {
    condition     = length(var.service_subnet_ids) >= 1
    error_message = "Provide at least one service subnet."
  }
}

variable "internal" {
  description = "Create an internal load balancer, reachable only inside your network."
  type        = bool
  default     = true
}

variable "allowed_cidrs" {
  description = "CIDR blocks allowed to reach the load balancer."
  type        = list(string)
  default     = ["10.0.0.0/8"]
}

variable "image" {
  description = "Container image to run."
  type        = string
  default     = "abhaypratap/mockhub:latest"
}

variable "certificate_arn" {
  description = "ACM certificate for HTTPS. Leave null for HTTP only."
  type        = string
  default     = null
}

variable "public_url" {
  description = "URL people use to reach MockHub, e.g. https://mocks.example.com. Used in Postman/Bruno exports."
  type        = string
  default     = null
}

variable "users_secret_arn" {
  description = "Secrets Manager secret whose value is MOCKHUB_USERS (user:password:role,...). Leave null to disable sign-in."
  type        = string
  default     = null
}

variable "cpu" {
  description = "Task CPU units."
  type        = number
  default     = 512
}

variable "memory" {
  description = "Task memory in MiB."
  type        = number
  default     = 1024
}

variable "cpu_architecture" {
  description = "ARM64 (Graviton, cheaper) or X86_64. The published image supports both."
  type        = string
  default     = "ARM64"

  validation {
    condition     = contains(["ARM64", "X86_64"], var.cpu_architecture)
    error_message = "cpu_architecture must be ARM64 or X86_64."
  }
}

variable "log_retention_days" {
  description = "CloudWatch log retention."
  type        = number
  default     = 30
}

variable "extra_environment" {
  description = "Extra environment variables for the container, e.g. { WIREMOCK_OPTIONS = \"--async-response-enabled true\" }."
  type        = map(string)
  default     = {}
}

variable "tags" {
  description = "Tags added to every resource."
  type        = map(string)
  default     = {}
}
