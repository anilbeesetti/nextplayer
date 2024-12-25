# frozen_string_literal: true

module JWT
  module JWA
    class Eddsa
      include JWT::JWA::SigningAlgorithm

      def initialize(alg)
        @alg = alg
      end

      def sign(data:, signing_key:)
        unless signing_key.is_a?(RbNaCl::Signatures::Ed25519::SigningKey)
          raise_encode_error!("Key given is a #{signing_key.class} but has to be an RbNaCl::Signatures::Ed25519::SigningKey")
        end

        signing_key.sign(data)
      end

      def verify(data:, signature:, verification_key:)
        unless verification_key.is_a?(RbNaCl::Signatures::Ed25519::VerifyKey)
          raise_decode_error!("key given is a #{verification_key.class} but has to be a RbNaCl::Signatures::Ed25519::VerifyKey")
        end

        verification_key.verify(signature, data)
      rescue RbNaCl::CryptoError
        false
      end

      register_algorithm(new('ED25519'))
      register_algorithm(new('EdDSA'))
    end
  end
end
