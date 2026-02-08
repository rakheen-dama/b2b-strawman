#!/bin/bash
awslocal s3 mb "s3://${S3_BUCKET_NAME}"
echo "Created bucket: ${S3_BUCKET_NAME}"

awslocal s3api put-bucket-cors --bucket "${S3_BUCKET_NAME}" --cors-configuration '{
  "CORSRules": [{
    "AllowedOrigins": ["*"],
    "AllowedMethods": ["PUT", "GET"],
    "AllowedHeaders": ["*"],
    "ExposeHeaders": ["ETag"],
    "MaxAgeSeconds": 3600
  }]
}'
echo "Configured CORS for bucket: ${S3_BUCKET_NAME}"
