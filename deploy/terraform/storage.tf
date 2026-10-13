resource "aws_efs_file_system" "data" {
  encrypted        = true
  performance_mode = "generalPurpose"
  throughput_mode  = "elastic"

  tags = { Name = "${var.name}-data" }

  lifecycle {
    prevent_destroy = true
  }
}

resource "aws_efs_backup_policy" "data" {
  file_system_id = aws_efs_file_system.data.id

  backup_policy {
    status = "ENABLED"
  }
}

resource "aws_efs_mount_target" "data" {
  for_each = toset(var.service_subnet_ids)

  file_system_id  = aws_efs_file_system.data.id
  subnet_id       = each.value
  security_groups = [aws_security_group.efs.id]
}

resource "aws_efs_access_point" "data" {
  file_system_id = aws_efs_file_system.data.id

  posix_user {
    uid = local.data_uid
    gid = local.data_uid
  }

  root_directory {
    path = "/mockhub"

    creation_info {
      owner_uid   = local.data_uid
      owner_gid   = local.data_uid
      permissions = "0750"
    }
  }
}
