# -*- encoding: utf-8 -*-
# stub: word_wrap 1.0.0 ruby lib

Gem::Specification.new do |s|
  s.name = "word_wrap".freeze
  s.version = "1.0.0".freeze

  s.required_rubygems_version = Gem::Requirement.new(">= 0".freeze) if s.respond_to? :required_rubygems_version=
  s.require_paths = ["lib".freeze]
  s.authors = ["Radek Pazdera".freeze]
  s.date = "2015-04-21"
  s.description = "As simple as it gets CLI tool for word-wrapping\n                          plain-text. You can also use the library in your\n                          Ruby scripts. Check out the sources for details.".freeze
  s.email = ["radek@pazdera.co.uk".freeze]
  s.executables = ["ww".freeze]
  s.files = ["bin/ww".freeze]
  s.homepage = "https://github.com/pazdera/word_wrap".freeze
  s.licenses = ["MIT".freeze]
  s.rubygems_version = "3.5.3".freeze
  s.summary = "Simple tool for word-wrapping text".freeze

  s.installed_by_version = "3.5.3".freeze if s.respond_to? :installed_by_version

  s.specification_version = 4

  s.add_development_dependency(%q<bundler>.freeze, ["~> 1.5".freeze])
  s.add_development_dependency(%q<rake>.freeze, ["~> 0".freeze])
  s.add_development_dependency(%q<rspec>.freeze, ["~> 0".freeze])
end
