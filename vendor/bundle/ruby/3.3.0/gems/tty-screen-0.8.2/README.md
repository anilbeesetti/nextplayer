<div align="center">
  <a href="https://ttytoolkit.org"><img width="130" src="https://github.com/piotrmurach/tty/raw/master/images/tty.png" alt="TTY Toolkit logo"/></a>
</div>

# TTY::Screen

[![Gem Version](https://badge.fury.io/rb/tty-screen.svg)][gem]
[![Actions CI](https://github.com/piotrmurach/tty-screen/actions/workflows/ci.yml/badge.svg)][gh_actions_ci]
[![Build status](https://ci.appveyor.com/api/projects/status/myjv8kahk1iwrlha?svg=true)][appveyor]
[![Code Climate](https://codeclimate.com/github/piotrmurach/tty-screen/badges/gpa.svg)][codeclimate]
[![Coverage Status](https://coveralls.io/repos/piotrmurach/tty-screen/badge.svg)][coverage]

[gitter]: https://gitter.im/piotrmurach/tty
[gem]: http://badge.fury.io/rb/tty-screen
[gh_actions_ci]: https://github.com/piotrmurach/tty-screen/actions/workflows/ci.yml
[appveyor]: https://ci.appveyor.com/project/piotrmurach/tty-screen
[codeclimate]: https://codeclimate.com/github/piotrmurach/tty-screen
[coverage]: https://coveralls.io/r/piotrmurach/tty-screen

> Terminal screen size detection that works on Linux, macOS and Windows systems
  and supports Ruby MRI, JRuby, TruffleRuby and Rubinius interpreters.

**TTY::Screen** provides a terminal screen size detection component for the
[TTY](https://github.com/piotrmurach/tty) toolkit.

## Installation

Add this line to your application's Gemfile:

```ruby
gem "tty-screen"
```

And then execute:

    $ bundle

Or install it yourself as:

    $ gem install tty-screen

## 1. Usage

Use the `size` method to detect terminal screen size. It will result in
a `[height, width]` array:

```ruby
TTY::Screen.size  # => [51, 280]
```

Use the `width`, `columns` or `cols` method to detect terminal screen width:

```ruby
TTY::Screen.width    # => 280
TTY::Screen.columns  # => 280
TTY::Screen.cols     # => 280
```

Use the `height`, `lines` or `rows` method to detect terminal screen height:

```ruby
TTY::Screen.height  # => 51
TTY::Screen.lines   # => 51
TTY::Screen.rows    # => 51
```

## Development

After checking out the repo, run `bin/setup` to install dependencies.
Then, run `rake spec` to run the tests. You can also run `bin/console`
for an interactive prompt that will allow you to experiment.

## Contributing

1. Fork it ( https://github.com/piotrmurach/tty-screen/fork )
2. Create your feature branch (`git checkout -b my-new-feature`)
3. Commit your changes (`git commit -am 'Add some feature'`)
4. Push to the branch (`git push origin my-new-feature`)
5. Create a new Pull Request

## License

The gem is available as open source under the terms of the
[MIT License](https://opensource.org/licenses/MIT).

## Code of Conduct

Everyone interacting in the TTY::Screen project's codebases, issue trackers,
chat rooms and mailing lists is expected to follow the
[code of conduct](https://github.com/piotrmurach/tty-screen/blob/master/CODE_OF_CONDUCT.md).

## Copyright

Copyright (c) 2014 Piotr Murach. See
[LICENSE.txt](https://github.com/piotrmurach/tty-screen/blob/master/LICENSE.txt)
for further details.
