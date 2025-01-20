resource "aws_dynamodb_table" "balls_table" {
  name           = "balls"
  billing_mode   = "PAY_PER_REQUEST"  # Serverless pricing
  hash_key       = "id"
  stream_enabled = true
  stream_view_type = "NEW_AND_OLD_IMAGES"  # This allows tracking changes

  attribute {
    name = "id"
    type = "S"
  }

  tags = {
    Environment = var.environment
    Project     = "ball-dropping"
  }
}

resource "aws_dynamodb_table" "history_table" {
  name           = "ball_history"
  billing_mode   = "PAY_PER_REQUEST"
  hash_key       = "id"
  range_key      = "timestamp"

  attribute {
    name = "id"
    type = "S"
  }

  attribute {
    name = "timestamp"
    type = "N"
  }

  tags = {
    Environment = var.environment
    Project     = "ball-dropping"
  }
} 