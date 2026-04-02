variable "project" {
  description = "Project name"
  type        = string
  default     = "kazi"
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
  description = "Root domain name (e.g., heykazi.com). Wildcard cert covers *.heykazi.com."
  type        = string
  default     = "heykazi.com"
}

variable "hosted_zone_id" {
  description = "Route 53 hosted zone ID for DNS validation and alias records"
  type        = string
  default     = ""
}

variable "alb_dns_name" {
  description = "DNS name of the public ALB (for alias records)"
  type        = string
  default     = ""
}

variable "alb_zone_id" {
  description = "Zone ID of the public ALB (for alias records — this is the ALB's own hosted zone ID, not the heykazi.com zone)"
  type        = string
  default     = ""
}
