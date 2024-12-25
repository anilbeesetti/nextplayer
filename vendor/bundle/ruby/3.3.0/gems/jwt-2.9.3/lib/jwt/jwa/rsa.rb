# frozen_string_literal: true

module JWT
  module JWA
    class Rsa
      include JWT::JWA::SigningAlgorithm

      def initialize(alg)
        @alg = alg
        @digest = OpenSSL::Digest.new(alg.sub('RS', 'SHA'))
      end

      def sign(data:, signing_key:)
        unless signing_key.is_a?(OpenSSL::PKey::RSA)
          raise_sign_error!("The given key is a #{signing_key.class}. It has to be an OpenSSL::PKey::RSA instance")
        end

        signing_key.sign(digest, data)
      end

      def verify(data:, signature:, verification_key:)
        verification_key.verify(digest, signature, data)
      rescue OpenSSL::PKey::PKeyError
        raise JWT::VerificationError, 'Signature verification raised'
      end

      register_algorithm(new('RS256'))
      register_algorithm(new('RS384'))
      register_algorithm(new('RS512'))

      private

      attr_reader :digest
    end
  end
end
