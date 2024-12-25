## 1.0.8 (2024-12-05)

- `Cookie#expires=` accepts `DateTime` objects. (#52) @luke-hill @flavorjones


## 1.0.7 (2024-06-06)

- Explicitly require "cgi" to avoid `NameError` in some applications. (#50 by @flavorjones)


## 1.0.6 (2024-06-01)

- Fix error formatting bug in HTTP::CookieJar::AbstractStore (#42 by @andrelaszlo)
- Allow non-RFC 3986-compliant URLs (#45 by @c960657)
- Add coverage for Ruby 3.2 and 3.3 (#46 by @flavorjones)
- Quash ruby 3.4 warnings (#47 by @flavorjones)

## 1.0.5 (2022-05-25)

- Silence SQLite3 warnings

## 1.0.4 (2021-06-07)

- Support Mozilla's cookie storage format up to version 7.

- Fix the time representation with creationTime and lastAccessed in
  MozillaStore. (#8)

## 1.0.3 (2016-09-30)

- Treat comma as normal character in HTTP::Cookie.cookie_value_to_hash
  instead of key-value pair separator.  This should fix the problem
  described in CVE-2016-7401.

## 1.0.2 (2013-09-10)

  - Fix HTTP::Cookie.parse so that it does not raise ArgumentError
    when it finds a bad name or value that is parsable but considered
    invalid.

## 1.0.1 (2013-04-21)

  - Minor error handling improvements and documentation updates.

  - Argument error regarding specifying store/saver classes no longer
    raises IndexError, but either ArgumentError or TypeError.

## 1.0.0 (2013-04-17)

  - Initial Release.
