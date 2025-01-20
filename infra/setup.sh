#!/bin/bash
set -e

# Check if AWS CLI is installed
if ! command -v aws &> /dev/null; then
    echo "AWS CLI is not installed. Please install it first."
    exit 1
fi

# Check if Terraform is installed
if ! command -v terraform &> /dev/null; then
    echo "Terraform is not installed. Please install it first."
    exit 1
fi

# Check AWS credentials
if ! aws sts get-caller-identity &> /dev/null; then
    echo "AWS credentials not configured. Please run 'aws configure' first."
    exit 1
fi

echo "Setting up DynamoDB tables..."

# Initialize and apply Terraform
cd "$(dirname "$0")"
terraform init
terraform apply -auto-approve

echo "Infrastructure setup complete!"

# Output the table names for reference
echo "DynamoDB tables created:"
echo "- balls"
echo "- ball_history"

echo "You can now update state.js to use the DynamoDBAdapter" 