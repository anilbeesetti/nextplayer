# coding: utf-8
lib = File.expand_path('../lib', __FILE__)
$LOAD_PATH.unshift(lib) unless $LOAD_PATH.include?(lib)
require 'fastlane-sirp/version'

Gem::Specification.new do |spec|
  spec.name          = 'fastlane-sirp'
  spec.version       = SIRP::VERSION
  spec.authors       = ['Glenn Rempe', 'lamikae', 'snatchev', 'joshdholtz']
  spec.email         = ['me@joshholtz.com']

  spec.required_ruby_version = '>= 2.1.0'

  cert = File.expand_path('~/.gem-certs/gem-private_key_grempe.pem')
  if cert && File.exist?(cert)
    spec.signing_key = cert
    spec.cert_chain = ['certs/gem-public_cert_grempe.pem']
  end

  spec.summary       = 'Secure (interoperable) Remote Password Auth (SRP-6a)'
  spec.description   = <<-EOF
    A Ruby implementation of the Secure Remote Password protocol (SRP-6a).
    SiRP is a cryptographically strong authentication protocol for
    password-based, mutual authentication over an insecure network connection.
  EOF

  spec.homepage      = 'https://github.com/grempe/sirp'

  # http://spdx.org/licenses/BSD-3-Clause.html
  spec.license       = 'BSD-3-Clause'

  spec.files         = `git ls-files -z`.split("\x0").reject { |f| f.match(%r{^(test|spec|features)/}) }
  spec.bindir        = 'exe'
  spec.executables   = spec.files.grep(%r{^exe/}) { |f| File.basename(f) }
  spec.require_paths = ['lib']

  # See : https://bugs.ruby-lang.org/issues/9569
  spec.add_runtime_dependency 'sysrandom', '~> 1.0'

  spec.add_development_dependency 'bundler'
  spec.add_development_dependency 'rake'
  spec.add_development_dependency 'rspec', '~> 3.4'
  spec.add_development_dependency 'pry', '~> 0.12'
  spec.add_development_dependency 'coveralls', '~> 0.8'
  spec.add_development_dependency 'coco', '~> 0.15'
  spec.add_development_dependency 'wwtd', '~> 1.3'
end
