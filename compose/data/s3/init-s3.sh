#!/bin/bash
awslocal s3 mb "s3://${S3_BUCKET_NAME}"
echo "Created bucket: ${S3_BUCKET_NAME}"
