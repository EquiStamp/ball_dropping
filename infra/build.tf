resource "null_resource" "build" {
  triggers = {
    always_run = timestamp()
  }

  provisioner "local-exec" {
    working_dir = "${path.module}/.."
    command = <<-EOT
      npm install
      npx shadow-cljs release app
    EOT
  }
}

resource "null_resource" "deploy" {
  triggers = {
    build_done = null_resource.build.id
  }

  provisioner "local-exec" {
    working_dir = "${path.module}/.."
    command = <<-EOT
      # Deploy static assets with long cache
      aws s3 sync public/ s3://${aws_s3_bucket.website.id} \
        --delete \
        --cache-control "max-age=31536000,public" \
        --exclude "*.html" \
        --exclude "*.json"

      # Deploy HTML/JSON with no cache
      aws s3 sync public/ s3://${aws_s3_bucket.website.id} \
        --delete \
        --cache-control "no-cache,no-store,must-revalidate" \
        --include "*.html" \
        --include "*.json"

      # Invalidate CloudFront
      aws cloudfront create-invalidation \
        --distribution-id ${aws_cloudfront_distribution.website.id} \
        --paths "/*"
    EOT
  }

  depends_on = [
    aws_s3_bucket.website,
    aws_cloudfront_distribution.website,
    aws_s3_bucket_policy.website
  ]
} 