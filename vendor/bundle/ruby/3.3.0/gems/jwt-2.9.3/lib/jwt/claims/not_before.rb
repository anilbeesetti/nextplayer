# frozen_string_literal: true

module JWT
  module Claims
    class NotBefore
      def initialize(leeway:)
        @leeway = leeway || 0
      end

      def verify!(context:, **_args)
        return unless context.payload.is_a?(Hash)
        return unless context.payload.key?('nbf')

        raise JWT::ImmatureSignature, 'Signature nbf has not been reached' if context.payload['nbf'].to_i > (Time.now.to_i + leeway)
      end

      private

      attr_reader :leeway
    end
  end
end
