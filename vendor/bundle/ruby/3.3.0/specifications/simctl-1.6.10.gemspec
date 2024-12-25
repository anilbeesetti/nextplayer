# -*- encoding: utf-8 -*-
# stub: simctl 1.6.10 ruby lib

Gem::Specification.new do |s|
  s.name = "simctl".freeze
  s.version = "1.6.10".freeze

  s.required_rubygems_version = Gem::Requirement.new(">= 0".freeze) if s.respond_to? :required_rubygems_version=
  s.require_paths = ["lib".freeze]
  s.authors = ["Johannes Plunien".freeze]
  s.date = "2023-01-26"
  s.description = "Ruby interface to xcrun simctl".freeze
  s.email = ["plu@pqpq.de".freeze]
  s.homepage = "https://github.com/plu/simctl".freeze
  s.licenses = ["MIT".freeze]
  s.rubygems_version = "3.5.3".freeze
  s.summary = "Ruby interface to xcrun simctl".freeze

  s.installed_by_version = "3.5.3".freeze if s.respond_to? :installed_by_version

  s.specification_version = 4

  s.add_development_dependency(%q<coveralls>.freeze, [">= 0".freeze])
  s.add_development_dependency(%q<irb>.freeze, [">= 0".freeze])
  s.add_development_dependency(%q<rake>.freeze, [">= 0".freeze])
  s.add_development_dependency(%q<rspec>.freeze, [">= 0".freeze])
  s.add_development_dependency(%q<rubocop>.freeze, ["= 0.49.1".freeze])
  s.add_runtime_dependency(%q<CFPropertyList>.freeze, [">= 0".freeze])
  s.add_runtime_dependency(%q<naturally>.freeze, [">= 0".freeze])
end
