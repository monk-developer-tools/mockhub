output "url" {
  description = "MockHub UI. Point a DNS record at load_balancer_dns_name for a friendly hostname."
  value       = "${local.https ? "https" : "http"}://${aws_lb.this.dns_name}/__admin/mockhub/ui/"
}

output "load_balancer_dns_name" {
  value = aws_lb.this.dns_name
}

output "load_balancer_zone_id" {
  description = "For a Route 53 alias record."
  value       = aws_lb.this.zone_id
}

output "file_system_id" {
  description = "EFS file system holding projects and stubs. Protected from terraform destroy."
  value       = aws_efs_file_system.data.id
}

output "cluster_name" {
  value = aws_ecs_cluster.this.name
}

output "service_name" {
  value = aws_ecs_service.this.name
}
