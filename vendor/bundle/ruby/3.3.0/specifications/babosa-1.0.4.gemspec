# -*- encoding: utf-8 -*-
# stub: babosa 1.0.4 ruby lib

Gem::Specification.new do |s|
  s.name = "babosa".freeze
  s.version = "1.0.4".freeze

  s.required_rubygems_version = Gem::Requirement.new(">= 0".freeze) if s.respond_to? :required_rubygems_version=
  s.require_paths = ["lib".freeze]
  s.authors = ["Norman Clarke".freeze]
  s.date = "2020-10-06"
  s.description = "    A library for creating slugs. Babosa an extraction and improvement of the\n    string code from FriendlyId, intended to help developers create similar\n    libraries or plugins.\n".freeze
  s.email = "norman@njclarke.com".freeze
  s.homepage = "http://github.com/norman/babosa".freeze
  s.required_ruby_version = Gem::Requirement.new(">= 2.0.0".freeze)
  s.rubygems_version = "3.5.3".freeze
  s.summary = "A library for creating slugs.".freeze

  s.installed_by_version = "3.5.3".freeze if s.respond_to? :installed_by_version

  s.specification_version = 4

  s.add_development_dependency(%q<activesupport>.freeze, [">= 3.2.0".freeze])
  s.add_development_dependency(%q<rspec>.freeze, [">= 3.7.0".freeze])
  s.add_development_dependency(%q<simplecov>.freeze, [">= 0".freeze])
  s.add_development_dependency(%q<rake>.freeze, [">= 0".freeze])
  s.add_development_dependency(%q<unicode>.freeze, [">= 0".freeze])
end
