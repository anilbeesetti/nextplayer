# frozen_string_literal: true

module JWT
  module Claims
    class IssuedAt
      def verify!(context:, **_args)
        return unless context.payload.is_a?(Hash)
        return unless context.payload.key?('iat')

        iat = context.payload['iat']
        raise(JWT::InvalidIatError, 'Invalid iat') if !iat.is_a?(::Numeric) || iat.to_f > Time.now.to_f
      end
    end
  end
end
