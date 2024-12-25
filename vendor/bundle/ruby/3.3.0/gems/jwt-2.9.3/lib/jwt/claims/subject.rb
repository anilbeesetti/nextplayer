# frozen_string_literal: true

module JWT
  module Claims
    class Subject
      def initialize(expected_subject:)
        @expected_subject = expected_subject.to_s
      end

      def verify!(context:, **_args)
        sub = context.payload['sub']
        raise(JWT::InvalidSubError, "Invalid subject. Expected #{expected_subject}, received #{sub || '<none>'}") unless sub.to_s == expected_subject
      end

      private

      attr_reader :expected_subject
    end
  end
end
