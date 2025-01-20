#!/bin/bash
set -e

# Check AWS credentials
if ! aws sts get-caller-identity &> /dev/null; then
    echo "AWS credentials not configured. Please run 'aws configure' first."
    exit 1
fi

echo "Tearing down infrastructure..."

# Run terraform destroy
cd "$(dirname "$0")"
terraform destroy -auto-approve

echo "Infrastructure teardown complete!" 