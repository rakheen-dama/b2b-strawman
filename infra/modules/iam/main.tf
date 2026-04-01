# -----------------------------------------------------------------------------
# ECS Task Execution Role — shared by both services
# Permissions: ECR pull, CloudWatch logs, Secrets Manager read
# -----------------------------------------------------------------------------

data "aws_iam_policy_document" "ecs_assume_role" {
  statement {
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["ecs-tasks.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "execution" {
  name               = "${var.project}-${var.environment}-ecs-execution"
  assume_role_policy = data.aws_iam_policy_document.ecs_assume_role.json
}

data "aws_iam_policy_document" "execution_policy" {
  # ECR: GetAuthorizationToken is account-level (must use "*")
  statement {
    sid       = "ECRAuth"
    actions   = ["ecr:GetAuthorizationToken"]
    resources = ["*"]
  }

  # ECR: Pull images from specific repositories
  statement {
    sid = "ECRPull"
    actions = [
      "ecr:BatchCheckLayerAvailability",
      "ecr:GetDownloadUrlForLayer",
      "ecr:BatchGetImage",
    ]
    resources = var.ecr_repo_arns
  }

  # CloudWatch: Write logs to specific log groups
  statement {
    sid = "CloudWatchLogs"
    actions = [
      "logs:CreateLogStream",
      "logs:PutLogEvents",
    ]
    resources = [
      "${var.frontend_log_group_arn}:*",
      "${var.backend_log_group_arn}:*",
      "${var.gateway_log_group_arn}:*",
      "${var.portal_log_group_arn}:*",
      "${var.keycloak_log_group_arn}:*",
    ]
  }

  # Secrets Manager: Read specific secrets
  statement {
    sid       = "SecretsRead"
    actions   = ["secretsmanager:GetSecretValue"]
    resources = var.secret_arns
  }
}

resource "aws_iam_role_policy" "execution" {
  name   = "${var.project}-${var.environment}-ecs-execution"
  role   = aws_iam_role.execution.id
  policy = data.aws_iam_policy_document.execution_policy.json
}

# -----------------------------------------------------------------------------
# Backend Task Role — S3 access for document storage
# -----------------------------------------------------------------------------

resource "aws_iam_role" "backend_task" {
  name               = "${var.project}-${var.environment}-backend-task"
  assume_role_policy = data.aws_iam_policy_document.ecs_assume_role.json
}

data "aws_iam_policy_document" "backend_task_policy" {
  # S3: Read/write/delete objects in the app bucket
  statement {
    sid = "S3Objects"
    actions = [
      "s3:GetObject",
      "s3:PutObject",
      "s3:DeleteObject",
    ]
    resources = ["${var.s3_bucket_arn}/*"]
  }

  # S3: Bucket-level operations
  statement {
    sid = "S3Bucket"
    actions = [
      "s3:GetBucketLocation",
      "s3:ListBucket",
    ]
    resources = [var.s3_bucket_arn]
  }
}

resource "aws_iam_role_policy" "backend_task" {
  name   = "${var.project}-${var.environment}-backend-task"
  role   = aws_iam_role.backend_task.id
  policy = data.aws_iam_policy_document.backend_task_policy.json
}

# -----------------------------------------------------------------------------
# Frontend Task Role — minimal (ECS requires a task role)
# -----------------------------------------------------------------------------

resource "aws_iam_role" "frontend_task" {
  name               = "${var.project}-${var.environment}-frontend-task"
  assume_role_policy = data.aws_iam_policy_document.ecs_assume_role.json
}

# -----------------------------------------------------------------------------
# Gateway Task Role — minimal (network access via SGs only)
# -----------------------------------------------------------------------------

resource "aws_iam_role" "gateway_task" {
  name               = "${var.project}-${var.environment}-gateway-task"
  assume_role_policy = data.aws_iam_policy_document.ecs_assume_role.json
}

# -----------------------------------------------------------------------------
# Portal Task Role — minimal (network access via SGs only)
# -----------------------------------------------------------------------------

resource "aws_iam_role" "portal_task" {
  name               = "${var.project}-${var.environment}-portal-task"
  assume_role_policy = data.aws_iam_policy_document.ecs_assume_role.json
}

# -----------------------------------------------------------------------------
# Keycloak Task Role — minimal (DB access via SGs, SMTP via network)
# -----------------------------------------------------------------------------

resource "aws_iam_role" "keycloak_task" {
  name               = "${var.project}-${var.environment}-keycloak-task"
  assume_role_policy = data.aws_iam_policy_document.ecs_assume_role.json
}

# -----------------------------------------------------------------------------
# Data sources for scoping IAM policies to this account/region
# -----------------------------------------------------------------------------

data "aws_caller_identity" "current" {}
data "aws_region" "current" {}

# -----------------------------------------------------------------------------
# GitHub OIDC Provider — enables GitHub Actions to assume AWS roles
# Thumbprint: Required by AWS but no longer validated for GitHub OIDC
# -----------------------------------------------------------------------------

resource "aws_iam_openid_connect_provider" "github" {
  url             = "https://token.actions.githubusercontent.com"
  client_id_list  = ["sts.amazonaws.com"]
  thumbprint_list = ["6938fd4d98bab03faadb97b34396831e3780aea1"]
}

# -----------------------------------------------------------------------------
# GitHub Actions Role — assumed by CI/CD workflows via OIDC
# -----------------------------------------------------------------------------

resource "aws_iam_role" "github_actions" {
  name = "${var.project}-${var.environment}-github-actions"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Federated = aws_iam_openid_connect_provider.github.arn }
      Action    = "sts:AssumeRoleWithWebIdentity"
      Condition = {
        StringEquals = { "token.actions.githubusercontent.com:aud" = "sts.amazonaws.com" }
        StringLike   = { "token.actions.githubusercontent.com:sub" = "repo:${var.github_repo}:*" }
      }
    }]
  })
}

data "aws_iam_policy_document" "github_actions_policy" {
  # ECR: GetAuthorizationToken is account-level (mandatory wildcard)
  statement {
    sid       = "ECRAuth"
    actions   = ["ecr:GetAuthorizationToken"]
    resources = ["*"]
  }

  # ECR: Push and pull images to/from all service repositories
  statement {
    sid = "ECRPushPull"
    actions = [
      "ecr:BatchCheckLayerAvailability",
      "ecr:PutImage",
      "ecr:InitiateLayerUpload",
      "ecr:UploadLayerPart",
      "ecr:CompleteLayerUpload",
      "ecr:BatchGetImage",
      "ecr:GetDownloadUrlForLayer",
    ]
    resources = var.ecr_repo_arns
  }

  # ECS: Manage services and task definitions for all services
  statement {
    sid = "ECSManage"
    actions = [
      "ecs:UpdateService",
      "ecs:DescribeServices",
      "ecs:RegisterTaskDefinition",
      "ecs:DeregisterTaskDefinition",
      "ecs:DescribeTasks",
      "ecs:ListTasks",
    ]
    resources = [
      "arn:aws:ecs:${data.aws_region.current.name}:${data.aws_caller_identity.current.account_id}:service/${var.project}-*",
      "arn:aws:ecs:${data.aws_region.current.name}:${data.aws_caller_identity.current.account_id}:task-definition/${var.project}-*",
      "arn:aws:ecs:${data.aws_region.current.name}:${data.aws_caller_identity.current.account_id}:task/${var.project}-*",
    ]
  }

  # ECS: DescribeTaskDefinition does not support resource-level permissions
  statement {
    sid       = "ECSDescribeTaskDef"
    actions   = ["ecs:DescribeTaskDefinition"]
    resources = ["*"]
  }

  # S3: Terraform state bucket read/write
  statement {
    sid = "TerraformStateObjects"
    actions = [
      "s3:GetObject",
      "s3:PutObject",
      "s3:DeleteObject",
    ]
    resources = ["arn:aws:s3:::${var.terraform_state_bucket_name}/*"]
  }

  statement {
    sid       = "TerraformStateBucket"
    actions   = ["s3:ListBucket"]
    resources = ["arn:aws:s3:::${var.terraform_state_bucket_name}"]
  }

  # DynamoDB: Terraform state locking
  statement {
    sid = "TerraformStateLock"
    actions = [
      "dynamodb:GetItem",
      "dynamodb:PutItem",
      "dynamodb:DeleteItem",
    ]
    resources = ["arn:aws:dynamodb:${data.aws_region.current.name}:${data.aws_caller_identity.current.account_id}:table/${var.terraform_lock_table_name}"]
  }

  # IAM: Pass task/execution roles to ECS when registering task definitions
  statement {
    sid     = "IAMPassRole"
    actions = ["iam:PassRole"]
    resources = [
      aws_iam_role.execution.arn,
      aws_iam_role.backend_task.arn,
      aws_iam_role.frontend_task.arn,
      aws_iam_role.gateway_task.arn,
      aws_iam_role.portal_task.arn,
      aws_iam_role.keycloak_task.arn,
    ]
    condition {
      test     = "StringEquals"
      variable = "iam:PassedToService"
      values   = ["ecs-tasks.amazonaws.com"]
    }
  }
}

resource "aws_iam_role_policy" "github_actions" {
  name   = "${var.project}-${var.environment}-github-actions"
  role   = aws_iam_role.github_actions.id
  policy = data.aws_iam_policy_document.github_actions_policy.json
}
