# frozen_string_literal: true

module JWT
  module Claims
    class Issuer
      def initialize(issuers:)
        @issuers = Array(issuers).map { |item| item.is_a?(Symbol) ? item.to_s : item }
      end

      def verify!(context:, **_args)
        case (iss = context.payload['iss'])
        when *issuers
          nil
        else
          raise JWT::InvalidIssuerError, "Invalid issuer. Expected #{issuers}, received #{iss || '<none>'}"
        end
      end

      private

      attr_reader :issuers
    end
  end
end
