# frozen_string_literal: true

module JWT
  module Claims
    class Audience
      def initialize(expected_audience:)
        @expected_audience = expected_audience
      end

      def verify!(context:, **_args)
        aud = context.payload['aud']
        raise JWT::InvalidAudError, "Invalid audience. Expected #{expected_audience}, received #{aud || '<none>'}" if ([*aud] & [*expected_audience]).empty?
      end

      private

      attr_reader :expected_audience
    end
  end
end
