# frozen_string_literal: true

module Aws
  module S3
    module Plugins
      # @api private
      class ExpressSessionAuth < Seahorse::Client::Plugin
        # This should be s3_disable_express_auth instead
        # But this is not a built in. We're overwriting the generated value
        option(:disable_s3_express_session_auth,
          default: false,
          doc_type: 'Boolean',
          docstring: <<-DOCS) do |cfg|
When `true`, S3 Express session authentication is disabled.
          DOCS
          resolve_disable_s3_express_session_auth(cfg)
        end

        option(:express_credentials_provider,
          doc_type: 'Aws::S3::ExpressCredentialsProvider',
          rbs_type: 'untyped',
          docstring: <<-DOCS) do |_cfg|
Credential Provider for S3 Express endpoints. Manages credentials
for different buckets.
          DOCS
          Aws::S3::ExpressCredentialsProvider.new
        end

        # @api private
        class Handler < Seahorse::Client::Handler
          def call(context)
            if (props = context[:endpoint_properties])
              # S3 Express endpoint - turn off md5 and enable crc32 default
              if props['backend'] == 'S3Express'
                if context.operation_name == :put_object || checksum_required?(context)
                  context[:default_request_checksum_algorithm] = 'CRC32'
                end
                context[:s3_express_endpoint] = true
              end

              # if s3 express auth, use new credentials and sign additional header
              if context[:auth_scheme]['name'] == 'sigv4-s3express' &&
                 !context.config.disable_s3_express_session_auth
                bucket = context.params[:bucket]
                credentials_provider = context.config.express_credentials_provider
                credentials = credentials_provider.express_credentials_for(bucket)
                context[:sigv4_credentials] = credentials # Sign will use this
              end
            end
            with_metric(credentials) { @handler.call(context) }
          end

          private

          def with_metric(credentials, &block)
            return block.call unless credentials

            Aws::Plugins::UserAgent.metric('S3_EXPRESS_BUCKET', &block)
          end

          def checksum_required?(context)
            context.operation.http_checksum_required ||
              (context.operation.http_checksum &&
                context.operation.http_checksum['requestChecksumRequired'])
          end
        end

        handler(Handler)

        # Optimization - sets this client as the client to create sessions.
        def after_initialize(client)
          provider = client.config.express_credentials_provider
          provider.client = client unless provider.client
        end

        class << self
          private

          def resolve_disable_s3_express_session_auth(cfg)
            value = ENV['AWS_S3_DISABLE_EXPRESS_SESSION_AUTH'] ||
                    Aws.shared_config.s3_disable_express_session_auth(profile: cfg.profile) ||
                    'false'
            value = Aws::Util.str_2_bool(value)
            # Raise if provided value is not true or false
            if value.nil?
              raise ArgumentError,
                    'Must provide either `true` or `false` for the '\
                    '`s3_disable_express_session_auth` profile option or for '\
                    "ENV['AWS_S3_DISABLE_EXPRESS_SESSION_AUTH']."
            end
            value
          end
        end
      end
    end
  end
end
