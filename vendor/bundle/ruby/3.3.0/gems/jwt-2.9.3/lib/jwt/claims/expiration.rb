# frozen_string_literal: true

module JWT
  module Claims
    class Expiration
      def initialize(leeway:)
        @leeway = leeway || 0
      end

      def verify!(context:, **_args)
        return unless context.payload.is_a?(Hash)
        return unless context.payload.key?('exp')

        raise JWT::ExpiredSignature, 'Signature has expired' if context.payload['exp'].to_i <= (Time.now.to_i - leeway)
      end

      private

      attr_reader :leeway
    end
  end
end
