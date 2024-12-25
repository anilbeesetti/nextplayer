# frozen_string_literal: true

require_relative 'jwa'

# JWT::Encode module
module JWT
  # Encoding logic for JWT
  class Encode
    def initialize(options)
      @payload          = options[:payload]
      @key              = options[:key]
      @algorithm        = JWA.resolve(options[:algorithm])
      @headers          = options[:headers].transform_keys(&:to_s)
    end

    def segments
      validate_claims!
      combine(encoded_header_and_payload, encoded_signature)
    end

    private

    def encoded_header
      @encoded_header ||= encode_header
    end

    def encoded_payload
      @encoded_payload ||= encode_payload
    end

    def encoded_signature
      @encoded_signature ||= encode_signature
    end

    def encoded_header_and_payload
      @encoded_header_and_payload ||= combine(encoded_header, encoded_payload)
    end

    def encode_header
      encode_data(@headers.merge(@algorithm.header(signing_key: @key)))
    end

    def encode_payload
      encode_data(@payload)
    end

    def signature
      @algorithm.sign(data: encoded_header_and_payload, signing_key: @key)
    end

    def validate_claims!
      return unless @payload.is_a?(Hash)

      Claims.verify_payload!(@payload, :numeric)
    end

    def encode_signature
      ::JWT::Base64.url_encode(signature)
    end

    def encode_data(data)
      ::JWT::Base64.url_encode(JWT::JSON.generate(data))
    end

    def combine(*parts)
      parts.join('.')
    end
  end
end
