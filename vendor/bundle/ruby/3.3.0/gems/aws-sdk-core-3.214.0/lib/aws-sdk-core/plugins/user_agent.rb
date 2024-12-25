# frozen_string_literal: true

module Aws
  module Plugins
    # @api private
    class UserAgent < Seahorse::Client::Plugin
      METRICS = Aws::Json.load(<<-METRICS)
        {
          "RESOURCE_MODEL": "A",
          "WAITER": "B",
          "PAGINATOR": "C",
          "RETRY_MODE_LEGACY": "D",
          "RETRY_MODE_STANDARD": "E",
          "RETRY_MODE_ADAPTIVE": "F",
          "S3_TRANSFER": "G",
          "S3_CRYPTO_V1N": "H",
          "S3_CRYPTO_V2": "I",
          "S3_EXPRESS_BUCKET": "J",
          "S3_ACCESS_GRANTS": "K",
          "GZIP_REQUEST_COMPRESSION": "L",
          "PROTOCOL_RPC_V2_CBOR": "M",
          "ENDPOINT_OVERRIDE": "N",
          "ACCOUNT_ID_ENDPOINT": "O",
          "ACCOUNT_ID_MODE_PREFERRED": "P",
          "ACCOUNT_ID_MODE_DISABLED": "Q",
          "ACCOUNT_ID_MODE_REQUIRED": "R",
          "SIGV4A_SIGNING": "S",
          "RESOLVED_ACCOUNT_ID": "T"
        }
      METRICS

      # @api private
      option(:user_agent_suffix)
      # @api private
      option(:user_agent_frameworks, default: [])

      option(
        :sdk_ua_app_id,
        doc_type: 'String',
        docstring: <<-DOCS) do |cfg|
A unique and opaque application ID that is appended to the
User-Agent header as app/sdk_ua_app_id. It should have a
maximum length of 50. This variable is sourced from environment
variable AWS_SDK_UA_APP_ID or the shared config profile attribute sdk_ua_app_id.
        DOCS
        app_id = ENV['AWS_SDK_UA_APP_ID']
        app_id ||= Aws.shared_config.sdk_ua_app_id(profile: cfg.profile)
        app_id
      end

      # Deprecated - must exist for old service gems
      def self.feature(_feature, &block)
        block.call
      end

      def self.metric(*metrics, &block)
        Thread.current[:aws_sdk_core_user_agent_metric] ||= []
        metrics = metrics.map { |metric| METRICS[metric] }.compact
        Thread.current[:aws_sdk_core_user_agent_metric].concat(metrics)
        block.call
      ensure
        Thread.current[:aws_sdk_core_user_agent_metric].pop(metrics.size)
      end

      # @api private
      class Handler < Seahorse::Client::Handler
        def call(context)
          set_user_agent(context)
          @handler.call(context)
        end

        def set_user_agent(context)
          context.http_request.headers['User-Agent'] = UserAgent.new(context).to_s
        end

        class UserAgent
          def initialize(context)
            @context = context
          end

          def to_s
            ua = "aws-sdk-ruby3/#{CORE_GEM_VERSION}"
            ua += ' ua/2.1'
            if (api_m = api_metadata)
              ua += " #{api_m}"
            end
            ua += " #{os_metadata}"
            ua += " #{language_metadata}"
            if (env_m = env_metadata)
              ua += " #{env_m}"
            end
            if (app_id_m = app_id_metadata)
              ua += " #{app_id_m}"
            end
            if (framework_m = framework_metadata)
              ua += " #{framework_m}"
            end
            if (metric_m = metric_metadata)
              ua += " #{metric_m}"
            end
            if @context.config.user_agent_suffix
              ua += " #{@context.config.user_agent_suffix}"
            end
            ua.strip
          end

          private

          # Used to be gem_name/gem_version
          def api_metadata
            service_id = @context.config.api.metadata['serviceId']
            return unless service_id

            service_id = service_id.gsub(' ', '_').downcase
            gem_version = @context[:gem_version]
            "api/#{service_id}##{gem_version}"
          end

          # Used to be RUBY_PLATFORM
          def os_metadata
            os =
              case RbConfig::CONFIG['host_os']
              when /mac|darwin/
                'macos'
              when /linux|cygwin/
                'linux'
              when /mingw|mswin/
                'windows'
              else
                'other'
              end
            metadata = "os/#{os}"
            local_version = Gem::Platform.local.version
            metadata += "##{local_version}" if local_version
            metadata += " md/#{RbConfig::CONFIG['host_cpu']}"
          end

          # Used to be RUBY_ENGINE/RUBY_VERSION
          def language_metadata
            "lang/#{RUBY_ENGINE}##{RUBY_ENGINE_VERSION} md/#{RUBY_VERSION}"
          end

          def env_metadata
            return unless (execution_env = ENV['AWS_EXECUTION_ENV'])

            "exec-env/#{execution_env}"
          end

          def app_id_metadata
            return unless (app_id = @context.config.sdk_ua_app_id)

            # Sanitize and only allow these characters
            app_id = app_id.gsub(/[^!#$%&'*+\-.^_`|~0-9A-Za-z]/, '-')
            "app/#{app_id}"
          end

          def framework_metadata
            if (frameworks_cfg = @context.config.user_agent_frameworks).empty?
              return
            end

            # Frameworks may be aws-record, aws-sdk-rails, etc.
            regex = /gems\/(?<name>#{frameworks_cfg.join('|')})-(?<version>\d+\.\d+\.\d+)/.freeze
            frameworks = {}
            Kernel.caller.each do |line|
              match = line.match(regex)
              next unless match

              frameworks[match[:name]] = match[:version]
            end
            frameworks.map { |n, v| "lib/#{n}##{v}" }.join(' ')
          end

          def metric_metadata
            if Thread.current[:aws_sdk_core_user_agent_metric].nil? ||
               Thread.current[:aws_sdk_core_user_agent_metric].empty?
              return
            end

            metrics = Thread.current[:aws_sdk_core_user_agent_metric].join(',')
            # Metric metadata is limited to 1024 bytes
            return "m/#{metrics}" if metrics.bytesize <= 1024

            # Removes the last unfinished metric
            "m/#{metrics[0...metrics[0..1024].rindex(',')]}"
          end
        end
      end

      handler(Handler, step: :sign, priority: 97)
    end
  end
end
