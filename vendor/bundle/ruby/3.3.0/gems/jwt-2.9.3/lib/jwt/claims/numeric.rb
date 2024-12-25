# frozen_string_literal: true

module JWT
  module Claims
    class Numeric
      class Compat
        def initialize(payload)
          @payload = payload
        end

        def verify!
          JWT::Claims.verify_payload!(@payload, :numeric)
        end
      end

      NUMERIC_CLAIMS = %i[
        exp
        iat
        nbf
      ].freeze

      def self.new(*args)
        return super if args.empty?

        Compat.new(*args)
      end

      def verify!(context:)
        validate_numeric_claims(context.payload)
      end

      def self.verify!(payload:, **_args)
        JWT::Claims.verify_payload!(payload, :numeric)
      end

      private

      def validate_numeric_claims(payload)
        NUMERIC_CLAIMS.each do |claim|
          validate_is_numeric(payload, claim)
        end
      end

      def validate_is_numeric(payload, claim)
        return unless payload.is_a?(Hash)
        return unless payload.key?(claim) ||
                      payload.key?(claim.to_s)

        return if payload[claim].is_a?(::Numeric) || payload[claim.to_s].is_a?(::Numeric)

        raise InvalidPayload, "#{claim} claim must be a Numeric value but it is a #{(payload[claim] || payload[claim.to_s]).class}"
      end
    end
  end
end
