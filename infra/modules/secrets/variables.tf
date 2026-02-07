variable "project" {
  description = "Project name"
  type        = string
}

variable "environment" {
  description = "Environment name"
  type        = string
}

variable "recovery_window_in_days" {
  description = "Recovery window for secret deletion (0 for immediate, 7-30 for production)"
  type        = number
  default     = 7
}
