# frozen_string_literal: true

source 'https://rubygems.org'

# Specify your gem's dependencies in nanaimo.gemspec
gemspec

group :development do
  gem 'rake', '~> 12.0'
  gem 'rspec'

  install_if Gem.ruby_version >= Gem::Version.new('2.6') do
    gem 'rubocop'
    gem 'rubocop-rake', '~> 0.6.0'
    gem 'rubocop-rspec', '~> 2.11'
  end
end
