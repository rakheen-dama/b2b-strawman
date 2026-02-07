variable "project" {
  description = "Project name"
  type        = string
}

variable "environment" {
  description = "Environment name"
  type        = string
}

variable "create_dns" {
  description = "Whether to create DNS and ACM resources"
  type        = bool
  default     = false
}

variable "domain_name" {
  description = "Domain name for the application (e.g., app.docteams.com)"
  type        = string
  default     = ""
}

variable "hosted_zone_id" {
  description = "Route 53 hosted zone ID for DNS validation and alias record"
  type        = string
  default     = ""
}

variable "alb_dns_name" {
  description = "DNS name of the public ALB (for alias record)"
  type        = string
  default     = ""
}

variable "alb_zone_id" {
  description = "Zone ID of the public ALB (for alias record)"
  type        = string
  default     = ""
}
