resource "null_resource" "build_server" {
  triggers = {
    always_run = timestamp()
  }

  provisioner "local-exec" {
    working_dir = "${path.module}/.."
    command = <<-EOT
      # Build backend uberjar
      clojure -T:build uber
      # Build frontend
      clojure -M:shadow-cljs release app
    EOT
  }
}

# S3 bucket for Lambda code
resource "aws_s3_bucket" "lambda_code" {
  bucket = "${var.bucket_name}-lambda"
  
  tags = {
    Environment = var.environment
    Project     = "ball-dropping"
  }
}

# Upload jar to S3
resource "aws_s3_object" "lambda_code" {
  bucket = aws_s3_bucket.lambda_code.id
  key    = "server.jar"
  source = "${path.module}/../target/server-1.0.0-standalone.jar"

  # Use timestamp to force update
  etag = timestamp()

  depends_on = [null_resource.build_server]
}

# Lambda function for the server
resource "aws_lambda_function" "server" {
  s3_bucket        = aws_s3_bucket.lambda_code.id
  s3_key           = aws_s3_object.lambda_code.key
  function_name    = "ball-dropping-server"
  role            = aws_iam_role.lambda_role.arn
  handler         = "ball_dropping.lambda.Handler::handleRequest"
  runtime         = "java17"
  memory_size     = 1024
  timeout         = 30

  # Use timestamp to force update
  source_code_hash = base64sha256(timestamp())

  environment {
    variables = {
      DYNAMODB_TABLE = aws_dynamodb_table.balls_table.name
      HISTORY_TABLE  = aws_dynamodb_table.history_table.name
    }
  }

  depends_on = [
    aws_s3_object.lambda_code,
    aws_iam_role_policy_attachment.lambda_policy
  ]
}

# API Gateway
resource "aws_apigatewayv2_api" "api" {
  name          = "ball-dropping-api"
  protocol_type = "HTTP"
  cors_configuration {
    allow_origins = ["https://d8e19ls3mncf9.cloudfront.net", "http://localhost:8000"]
    allow_methods = ["GET", "POST", "PUT", "DELETE", "OPTIONS"]
    allow_headers = ["Content-Type", "Accept"]
    allow_credentials = true
    max_age = 300
  }
}

resource "aws_apigatewayv2_stage" "api" {
  api_id = aws_apigatewayv2_api.api.id
  name   = "$default"
  auto_deploy = true
}

resource "aws_apigatewayv2_integration" "api" {
  api_id = aws_apigatewayv2_api.api.id
  integration_type = "AWS_PROXY"
  integration_uri  = aws_lambda_function.server.invoke_arn
  integration_method = "POST"
  payload_format_version = "2.0"
}

resource "aws_apigatewayv2_route" "api_balls_get" {
  api_id = aws_apigatewayv2_api.api.id
  route_key = "GET /api/balls"
  target    = "integrations/${aws_apigatewayv2_integration.api.id}"
}

resource "aws_apigatewayv2_route" "api_balls_post" {
  api_id = aws_apigatewayv2_api.api.id
  route_key = "POST /api/balls"
  target    = "integrations/${aws_apigatewayv2_integration.api.id}"
}

resource "aws_apigatewayv2_route" "api_balls_delete" {
  api_id = aws_apigatewayv2_api.api.id
  route_key = "DELETE /api/balls/{id}"
  target    = "integrations/${aws_apigatewayv2_integration.api.id}"
}

# Lambda permissions
resource "aws_lambda_permission" "apigw" {
  statement_id  = "AllowAPIGatewayInvoke"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.server.function_name
  principal     = "apigateway.amazonaws.com"
  source_arn    = "${aws_apigatewayv2_api.api.execution_arn}/*/*"
}

# IAM role for Lambda
resource "aws_iam_role" "lambda_role" {
  name = "ball-dropping-lambda-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = "sts:AssumeRole"
        Effect = "Allow"
        Principal = {
          Service = "lambda.amazonaws.com"
        }
      }
    ]
  })
}

# IAM policy for Lambda to access DynamoDB
resource "aws_iam_role_policy" "lambda_dynamodb" {
  name = "lambda-dynamodb-policy"
  role = aws_iam_role.lambda_role.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "dynamodb:GetItem",
          "dynamodb:PutItem",
          "dynamodb:UpdateItem",
          "dynamodb:DeleteItem",
          "dynamodb:Query",
          "dynamodb:Scan"
        ]
        Resource = [
          aws_dynamodb_table.balls_table.arn,
          aws_dynamodb_table.history_table.arn
        ]
      }
    ]
  })
}

# IAM policy for Lambda to access S3
resource "aws_iam_role_policy" "lambda_s3" {
  name = "lambda-s3-policy"
  role = aws_iam_role.lambda_role.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "s3:GetObject"
        ]
        Resource = [
          "${aws_s3_bucket.lambda_code.arn}/*"
        ]
      }
    ]
  })
}

# Attach basic Lambda execution policy
resource "aws_iam_role_policy_attachment" "lambda_policy" {
  role       = aws_iam_role.lambda_role.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"
}

resource "null_resource" "update_frontend_api_url" {
  triggers = {
    api_url = aws_apigatewayv2_api.api.api_endpoint
  }

  provisioner "local-exec" {
    working_dir = "${path.module}/.."
    command = <<-EOT
      # Remove the https:// prefix and trailing slash if present
      API_URL=$(echo ${aws_apigatewayv2_api.api.api_endpoint} | sed 's|^https://||' | sed 's|/$||')
      # Update the API URL in the frontend code
      sed -i '' "s|REPLACE_WITH_API_URL|$API_URL|" public/js/main.js
    EOT
  }

  depends_on = [null_resource.build_server]
}

# Output the API URL
output "api_url" {
  value = aws_apigatewayv2_api.api.api_endpoint
  description = "URL of the API Gateway endpoint"
} 