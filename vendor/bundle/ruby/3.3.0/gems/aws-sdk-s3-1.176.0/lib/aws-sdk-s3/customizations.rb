# frozen_string_literal: true

module Aws
  module S3
    # utility classes
    autoload :BucketRegionCache, 'aws-sdk-s3/bucket_region_cache'
    autoload :Encryption, 'aws-sdk-s3/encryption'
    autoload :EncryptionV2, 'aws-sdk-s3/encryption_v2'
    autoload :FilePart, 'aws-sdk-s3/file_part'
    autoload :FileUploader, 'aws-sdk-s3/file_uploader'
    autoload :FileDownloader, 'aws-sdk-s3/file_downloader'
    autoload :LegacySigner, 'aws-sdk-s3/legacy_signer'
    autoload :MultipartFileUploader, 'aws-sdk-s3/multipart_file_uploader'
    autoload :MultipartStreamUploader, 'aws-sdk-s3/multipart_stream_uploader'
    autoload :MultipartUploadError, 'aws-sdk-s3/multipart_upload_error'
    autoload :ObjectCopier, 'aws-sdk-s3/object_copier'
    autoload :ObjectMultipartCopier, 'aws-sdk-s3/object_multipart_copier'
    autoload :PresignedPost, 'aws-sdk-s3/presigned_post'
    autoload :Presigner, 'aws-sdk-s3/presigner'

    # s3 express session auth
    autoload :ExpressCredentials, 'aws-sdk-s3/express_credentials'
    autoload :ExpressCredentialsProvider, 'aws-sdk-s3/express_credentials_provider'

    # s3 access grants auth

    autoload :AccessGrantsCredentials, 'aws-sdk-s3/access_grants_credentials'
    autoload :AccessGrantsCredentialsProvider, 'aws-sdk-s3/access_grants_credentials_provider'
  end
end
