variable "project" {
  description = "Project name"
  type        = string
}

variable "environment" {
  description = "Environment name"
  type        = string
}

variable "cors_allowed_origins" {
  description = "Allowed origins for CORS (presigned uploads from browser)"
  type        = list(string)
  default     = ["*"]
}

variable "abort_incomplete_upload_days" {
  description = "Days after which incomplete multipart uploads are aborted"
  type        = number
  default     = 7
}
