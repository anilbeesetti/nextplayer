# -*- encoding: utf-8 -*-
# stub: google-apis-core 0.11.3 ruby lib

Gem::Specification.new do |s|
  s.name = "google-apis-core".freeze
  s.version = "0.11.3".freeze

  s.required_rubygems_version = Gem::Requirement.new(">= 0".freeze) if s.respond_to? :required_rubygems_version=
  s.metadata = { "bug_tracker_uri" => "https://github.com/googleapis/google-api-ruby-client/issues", "changelog_uri" => "https://github.com/googleapis/google-api-ruby-client/tree/main/google-apis-core/CHANGELOG.md", "documentation_uri" => "https://googleapis.dev/ruby/google-apis-core/v0.11.3", "source_code_uri" => "https://github.com/googleapis/google-api-ruby-client/tree/main/google-apis-core" } if s.respond_to? :metadata=
  s.require_paths = ["lib".freeze]
  s.authors = ["Google LLC".freeze]
  s.date = "2024-01-17"
  s.email = "googleapis-packages@google.com".freeze
  s.homepage = "https://github.com/google/google-api-ruby-client".freeze
  s.licenses = ["Apache-2.0".freeze]
  s.required_ruby_version = Gem::Requirement.new(">= 2.5".freeze)
  s.rubygems_version = "3.5.3".freeze
  s.summary = "Common utility and base classes for legacy Google REST clients".freeze

  s.installed_by_version = "3.5.3".freeze if s.respond_to? :installed_by_version

  s.specification_version = 4

  s.add_runtime_dependency(%q<representable>.freeze, ["~> 3.0".freeze])
  s.add_runtime_dependency(%q<retriable>.freeze, [">= 2.0".freeze, "< 4.a".freeze])
  s.add_runtime_dependency(%q<addressable>.freeze, ["~> 2.5".freeze, ">= 2.5.1".freeze])
  s.add_runtime_dependency(%q<mini_mime>.freeze, ["~> 1.0".freeze])
  s.add_runtime_dependency(%q<googleauth>.freeze, [">= 0.16.2".freeze, "< 2.a".freeze])
  s.add_runtime_dependency(%q<httpclient>.freeze, [">= 2.8.1".freeze, "< 3.a".freeze])
  s.add_runtime_dependency(%q<rexml>.freeze, [">= 0".freeze])
end
