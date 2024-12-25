# frozen_string_literal: true

module JWT
  module Claims
    class JwtId
      def initialize(validator:)
        @validator = validator
      end

      def verify!(context:, **_args)
        jti = context.payload['jti']
        if validator.respond_to?(:call)
          verified = validator.arity == 2 ? validator.call(jti, context.payload) : validator.call(jti)
          raise(JWT::InvalidJtiError, 'Invalid jti') unless verified
        elsif jti.to_s.strip.empty?
          raise(JWT::InvalidJtiError, 'Missing jti')
        end
      end

      private

      attr_reader :validator
    end
  end
end
