# frozen_string_literal: true

require 'openssl'

begin
  require 'rbnacl'
rescue LoadError
  raise if defined?(RbNaCl)
end

require_relative 'jwa/compat'
require_relative 'jwa/signing_algorithm'
require_relative 'jwa/ecdsa'
require_relative 'jwa/hmac'
require_relative 'jwa/none'
require_relative 'jwa/ps'
require_relative 'jwa/rsa'
require_relative 'jwa/unsupported'
require_relative 'jwa/wrapper'

if JWT.rbnacl?
  require_relative 'jwa/eddsa'
end

if JWT.rbnacl_6_or_greater?
  require_relative 'jwa/hmac_rbnacl'
elsif JWT.rbnacl?
  require_relative 'jwa/hmac_rbnacl_fixed'
end

module JWT
  module JWA
    class << self
      def resolve(algorithm)
        return find(algorithm) if algorithm.is_a?(String) || algorithm.is_a?(Symbol)

        unless algorithm.is_a?(SigningAlgorithm)
          Deprecations.warning('Custom algorithms are required to include JWT::JWA::SigningAlgorithm. Custom algorithms that do not include this module may stop working in the next major version of ruby-jwt.')
          return Wrapper.new(algorithm)
        end

        algorithm
      end

      def create(algorithm)
        resolve(algorithm)
      end
    end
  end
end
