variable "environment" {
  description = "Environment name (e.g. prod, staging)"
  type        = string
  default     = "prod"
}

variable "bucket_name" {
  description = "Name of the S3 bucket for static site hosting"
  type        = string
}

variable "domain_name" {
  description = "Domain name for the website (optional)"
  type        = string
  default     = ""
}

data "aws_region" "current" {} 