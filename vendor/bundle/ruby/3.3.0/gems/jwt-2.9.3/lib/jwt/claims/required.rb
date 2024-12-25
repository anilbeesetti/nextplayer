# frozen_string_literal: true

module JWT
  module Claims
    class Required
      def initialize(required_claims:)
        @required_claims = required_claims
      end

      def verify!(context:, **_args)
        required_claims.each do |required_claim|
          next if context.payload.is_a?(Hash) && context.payload.key?(required_claim)

          raise JWT::MissingRequiredClaim, "Missing required claim #{required_claim}"
        end
      end

      private

      attr_reader :required_claims
    end
  end
end
