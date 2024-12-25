# frozen_string_literal: true

require_relative 'error'

module JWT
  class Verify
    DEFAULTS = { leeway: 0 }.freeze
    METHODS = %w[verify_aud verify_expiration verify_iat verify_iss verify_jti verify_not_before verify_sub verify_required_claims].freeze

    class << self
      METHODS.each do |method_name|
        define_method(method_name) do |payload, options|
          new(payload, options).send(method_name)
        end
      end

      def verify_claims(payload, options)
        ::JWT::Claims.verify!(payload, options)
        true
      end
    end

    def initialize(payload, options)
      @payload = payload
      @options = DEFAULTS.merge(options)
    end

    METHODS.each do |method_name|
      define_method(method_name) do
        ::JWT::Claims.verify!(@payload, @options.merge(method_name => true))
      end
    end
  end
end
