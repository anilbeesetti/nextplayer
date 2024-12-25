Faraday Middleware
==================
<a href="https://badge.fury.io/rb/faraday_middleware"><img src="https://badge.fury.io/rb/faraday_middleware@2x.png" alt="Gem Version" height="20"></a>
[![CI](https://github.com/lostisland/faraday_middleware/actions/workflows/ci.yml/badge.svg)](https://github.com/lostisland/faraday_middleware/actions/workflows/ci.yml)

A collection of useful [Faraday][] middleware. [See the documentation][docs].

    gem install faraday_middleware

## ⚠️ DEPRECATION WARNING ⚠️

As highlighted in Faraday's [UPGRADING](https://github.com/lostisland/faraday/blob/main/UPGRADING.md) guide, `faraday_middleware` is DEPRECATED, and will not be updated to support Faraday 2.0.
If you rely on `faraday_middleware` in your project and would like to support Faraday 2.0:
* The `json` middleware (request and response) are now both bundled with Faraday 🙌
* The `instrumentation` middleware is bundled with Faraday
* All other middlewares, they'll be re-released as independent gems compatible with both Faraday v1 and v2, look for [`awesome-faraday`](https://github.com/lostisland/awesome-faraday)

Most of the middlewares are up for adoption, contributors that would like to maintain them.
If you'd like to maintain any middleware, have any question or need any help, we're here!
Please reach out opening an issue or a discussion.


Dependencies
------------

Ruby >= 2.3.0

#### As of v0.16.0, `faraday` and `faraday_middleware` no longer officially support JRuby or Rubinius.

Some dependent libraries are needed only when using specific middleware:

| Middleware                  | Library        | Notes |
| --------------------------- | -------------- | ----- |
| [FaradayMiddleware::Instrumentation](https://github.com/lostisland/faraday_middleware/blob/main/lib/faraday_middleware/instrumentation.rb) | [`activesupport`](https://rubygems.org/gems/activesupport) |       |
| [FaradayMiddleware::OAuth](https://github.com/lostisland/faraday_middleware/blob/main/lib/faraday_middleware/request/oauth.rb)    | [`simple_oauth`](https://rubygems.org/gems/simple_oauth) |       |
| [FaradayMiddleware::ParseXml](https://github.com/lostisland/faraday_middleware/blob/main/lib/faraday_middleware/response/parse_xml.rb) | [`multi_xml`](https://rubygems.org/gems/multi_xml)    |       |
| [FaradayMiddleware::ParseYaml](https://github.com/lostisland/faraday_middleware/blob/main/lib/faraday_middleware/response/parse_yaml.rb)  | [`safe_yaml`](https://rubygems.org/gems/safe_yaml)     | Not backwards compatible with versions of this middleware prior to `faraday_middleware` v0.12. See code comments for alternatives. |
| [FaradayMiddleware::Mashify](https://github.com/lostisland/faraday_middleware/blob/main/lib/faraday_middleware/response/mashify.rb)  | [`hashie`](https://rubygems.org/gems/hashie)       |       |
| [FaradayMiddleware::Rashify](https://github.com/lostisland/faraday_middleware/blob/main/lib/faraday_middleware/response/rashify.rb)  | [`rash_alt`](https://rubygems.org/gems/rash_alt)     | Make sure to uninstall original `rash` gem to avoid conflict. |

Examples
--------

``` rb
require 'faraday_middleware'

connection = Faraday.new 'http://example.com/api' do |conn|
  conn.request :oauth2, 'TOKEN'
  conn.request :json

  conn.response :xml,  content_type: /\bxml$/
  conn.response :json, content_type: /\bjson$/

  conn.use :instrumentation
  conn.adapter Faraday.default_adapter
end
```


  [faraday]: https://github.com/lostisland/faraday#readme
  [docs]: https://github.com/lostisland/faraday_middleware/wiki
