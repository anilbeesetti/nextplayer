# -*- encoding: utf-8 -*-
# stub: commander 4.6.0 ruby lib

Gem::Specification.new do |s|
  s.name = "commander".freeze
  s.version = "4.6.0".freeze

  s.required_rubygems_version = Gem::Requirement.new(">= 0".freeze) if s.respond_to? :required_rubygems_version=
  s.metadata = { "bug_tracker_uri" => "https://github.com/commander-rb/commander/issues", "changelog_uri" => "https://github.com/commander-rb/commander/blob/master/History.rdoc", "documentation_uri" => "https://www.rubydoc.info/gems/commander/4.6.0", "homepage_uri" => "https://github.com/commander-rb/commander", "source_code_uri" => "https://github.com/commander-rb/commander/tree/v4.6.0" } if s.respond_to? :metadata=
  s.require_paths = ["lib".freeze]
  s.authors = ["TJ Holowaychuk".freeze, "Gabriel Gilder".freeze]
  s.date = "2021-04-09"
  s.description = "The complete solution for Ruby command-line executables. Commander bridges the gap between other terminal related libraries you know and love (OptionParser, HighLine), while providing many new features, and an elegant API.".freeze
  s.email = ["gabriel@gabrielgilder.com".freeze]
  s.executables = ["commander".freeze]
  s.files = ["bin/commander".freeze]
  s.homepage = "https://github.com/commander-rb/commander".freeze
  s.licenses = ["MIT".freeze]
  s.required_ruby_version = Gem::Requirement.new(">= 2.4".freeze)
  s.rubygems_version = "3.5.3".freeze
  s.summary = "The complete solution for Ruby command-line executables".freeze

  s.installed_by_version = "3.5.3".freeze if s.respond_to? :installed_by_version

  s.specification_version = 4

  s.add_runtime_dependency(%q<highline>.freeze, ["~> 2.0.0".freeze])
  s.add_development_dependency(%q<rake>.freeze, [">= 0".freeze])
  s.add_development_dependency(%q<rspec>.freeze, ["~> 3.2".freeze])
  s.add_development_dependency(%q<rubocop>.freeze, ["~> 1.12.1".freeze])
  s.add_development_dependency(%q<simplecov>.freeze, [">= 0".freeze])
end
