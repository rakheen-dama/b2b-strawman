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
    resources = [
      var.frontend_ecr_repo_arn,
      var.backend_ecr_repo_arn,
    ]
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
  # S3: Read/write objects in the app bucket
  statement {
    sid = "S3Objects"
    actions = [
      "s3:GetObject",
      "s3:PutObject",
    ]
    resources = ["${var.s3_bucket_arn}/*"]
  }

  # S3: Bucket-level operations
  statement {
    sid       = "S3Bucket"
    actions   = ["s3:GetBucketLocation"]
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
