# -*- encoding: utf-8 -*-
# stub: nanaimo 0.4.0 ruby lib

Gem::Specification.new do |s|
  s.name = "nanaimo".freeze
  s.version = "0.4.0".freeze

  s.required_rubygems_version = Gem::Requirement.new(">= 0".freeze) if s.respond_to? :required_rubygems_version=
  s.require_paths = ["lib".freeze]
  s.authors = ["Danielle Tomlinson".freeze, "Samuel Giddins".freeze]
  s.bindir = "exe".freeze
  s.date = "2024-10-03"
  s.email = ["dan@tomlinson.io".freeze, "segiddins@segiddins.me".freeze]
  s.homepage = "https://github.com/CocoaPods/Nanaimo".freeze
  s.licenses = ["MIT".freeze]
  s.required_ruby_version = Gem::Requirement.new(">= 2.3.0".freeze)
  s.rubygems_version = "3.5.3".freeze
  s.summary = "A library for (de)serialization of ASCII Plists.".freeze

  s.installed_by_version = "3.5.3".freeze if s.respond_to? :installed_by_version

  s.specification_version = 4

  s.add_development_dependency(%q<bundler>.freeze, ["~> 2.3".freeze])
  s.add_development_dependency(%q<rake>.freeze, ["~> 12.3".freeze])
  s.add_development_dependency(%q<rspec>.freeze, ["~> 3.0".freeze])
end
