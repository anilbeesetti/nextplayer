# frozen_string_literal: true

begin
  require "rbconfig"
rescue LoadError
end

require_relative "screen/version"

module TTY
  # Responsible for detecting terminal screen size
  #
  # @api public
  module Screen
    # The Ruby configuration
    #
    # @return [Hash]
    #
    # @api private
    RUBY_CONFIG = defined?(::RbConfig) ? ::RbConfig::CONFIG : {}
    private_constant :RUBY_CONFIG

    # Define module method as private
    #
    # @return [void]
    #
    # @api private
    def self.private_module_function(name)
      module_function(name)
      private_class_method(name)
    end

    case RUBY_CONFIG["host_os"] || ::RUBY_PLATFORM
    when /mswin|msys|mingw|cygwin|bccwin|wince|emc/
      # Detect Windows system
      #
      # @return [Boolean]
      #
      # @api private
      def windows?; true end
    else
      def windows?; false end
    end
    module_function :windows?

    case RUBY_CONFIG["ruby_install_name"] || ::RUBY_ENGINE
    when /jruby/
      # Detect JRuby
      #
      # @return [Boolean]
      #
      # @api private
      def jruby?; true end
    else
      def jruby?; false end
    end
    module_function :jruby?

    # The default terminal screen size
    #
    # @return [Array(Integer, Integer)]
    #
    # @api private
    DEFAULT_SIZE = [27, 80].freeze

    @env = ENV
    @output = $stderr

    class << self
      # The environment variables
      #
      # @example
      #   TTY::Screen.env
      #
      # @example
      #   TTY::Screen.env = {"ROWS" => "51", "COLUMNS" => "211"}
      #
      # @return [Enumerable]
      #
      # @api public
      attr_accessor :env

      # The output stream with standard error as default
      #
      # @example
      #   TTY::Screen.output
      #
      # @example
      #   TTY::Screen.output = $stdout
      #
      # @return [IO]
      #
      # @api public
      attr_accessor :output
    end

    # Detect terminal screen size
    #
    # @example
    #   TTY::Screen.size # => [51, 211]
    #
    # @return [Array(Integer, Integer)]
    #   the terminal screen size
    #
    # @api public
    def size(verbose: false)
      size_from_java(verbose: verbose) ||
      size_from_win_api(verbose: verbose) ||
      size_from_ioctl ||
      size_from_io_console(verbose: verbose) ||
      size_from_readline(verbose: verbose) ||
      size_from_tput ||
      size_from_stty ||
      size_from_env ||
      size_from_ansicon ||
      size_from_default
    end
    module_function :size

    # Detect terminal screen width
    #
    # @example
    #   TTY::Screen.width # => 211
    #
    # @return [Integer]
    #
    # @api public
    def width
      size[1]
    end
    module_function :width

    alias columns width
    alias cols width
    module_function :columns
    module_function :cols

    # Detect terminal screen height
    #
    # @example
    #   TTY::Screen.height # => 51
    #
    # @return [Integer]
    #
    # @api public
    def height
      size[0]
    end
    module_function :height

    alias rows height
    alias lines height
    module_function :rows
    module_function :lines

    # Detect terminal screen size from default
    #
    # @return [Array(Integer, Integer)]
    #
    # @api private
    def size_from_default
      DEFAULT_SIZE
    end
    module_function :size_from_default

    if windows?
      # The standard output handle
      #
      # @return [Integer]
      #
      # @api private
      STDOUT_HANDLE = 0xFFFFFFF5

      # Detect terminal screen size from Windows API
      #
      # @param [Boolean] verbose
      #   the verbose mode, by default false
      #
      # @return [Array(Integer, Integer), nil]
      #   the terminal screen size or nil
      #
      # @api private
      def size_from_win_api(verbose: false)
        require "fiddle" unless defined?(Fiddle)

        kernel32 = Fiddle::Handle.new("kernel32")
        get_std_handle = Fiddle::Function.new(
          kernel32["GetStdHandle"], [-Fiddle::TYPE_INT], Fiddle::TYPE_INT)
        get_console_buffer_info = Fiddle::Function.new(
          kernel32["GetConsoleScreenBufferInfo"],
          [Fiddle::TYPE_LONG, Fiddle::TYPE_VOIDP], Fiddle::TYPE_INT)

        format = "SSSSSssssSS"
        buffer = ([0] * format.size).pack(format)
        stdout_handle = get_std_handle.(STDOUT_HANDLE)

        get_console_buffer_info.(stdout_handle, buffer)
        _, _, _, _, _, left, top, right, bottom, = buffer.unpack(format)
        size = [bottom - top + 1, right - left + 1]
        size if nonzero_column?(size[1] - 1)
      rescue LoadError
        warn "no native fiddle module found" if verbose
      rescue Fiddle::DLError
        # non windows platform or no kernel32 lib
      end
    else
      def size_from_win_api(verbose: false); nil end
    end
    module_function :size_from_win_api

    if jruby?
      # Detect terminal screen size from Java
      #
      # @param [Boolean] verbose
      #   the verbose mode, by default false
      #
      # @return [Array(Integer, Integer), nil]
      #   the terminal screen size or nil
      #
      # @api private
      def size_from_java(verbose: false)
        require "java"

        java_import "jline.TerminalFactory"
        terminal = TerminalFactory.get
        size = [terminal.get_height, terminal.get_width]
        size if nonzero_column?(size[1])
      rescue
        warn "failed to import java terminal package" if verbose
      end
    else
      def size_from_java(verbose: false); nil end
    end
    module_function :size_from_java

    # Detect terminal screen size from io-console
    #
    # On Windows, the io-console falls back to reading environment
    # variables. This means any user changes to the terminal screen
    # size will not be reflected in the runtime of the Ruby application.
    #
    # @param [Boolean] verbose
    #   the verbose mode, by default false
    #
    # @return [Array(Integer, Integer), nil]
    #   the terminal screen size or nil
    #
    # @api private
    def size_from_io_console(verbose: false)
      return unless output.tty?

      require "io/console" unless IO.method_defined?(:winsize)
      return unless output.respond_to?(:winsize)

      size = output.winsize
      size if nonzero_column?(size[1])
    rescue Errno::EOPNOTSUPP
      # no support for winsize on output
    rescue LoadError
      warn "no native io/console support or io-console gem" if verbose
    end
    module_function :size_from_io_console

    if !jruby? && output.respond_to?(:ioctl)
      # The get window size control code for Linux
      #
      # @return [Integer]
      #
      # @api private
      TIOCGWINSZ = 0x5413

      # The get window size control code for FreeBSD and macOS
      #
      # @return [Integer]
      #
      # @api private
      TIOCGWINSZ_PPC = 0x40087468

      # The get window size control code for Solaris
      #
      # @return [Integer]
      #
      # @api private
      TIOCGWINSZ_SOL = 0x5468

      # The ioctl window size buffer format
      #
      # @return [String]
      #
      # @api private
      TIOCGWINSZ_BUF_FMT = "SSSS"
      private_constant :TIOCGWINSZ_BUF_FMT

      # The ioctl window size buffer length
      #
      # @return [Integer]
      #
      # @api private
      TIOCGWINSZ_BUF_LEN = TIOCGWINSZ_BUF_FMT.length
      private_constant :TIOCGWINSZ_BUF_LEN

      # Detect terminal screen size from ioctl
      #
      # @return [Array(Integer, Integer), nil]
      #   the terminal screen size or nil
      #
      # @api private
      def size_from_ioctl
        buffer = Array.new(TIOCGWINSZ_BUF_LEN, 0).pack(TIOCGWINSZ_BUF_FMT)

        if ioctl?(TIOCGWINSZ, buffer) ||
           ioctl?(TIOCGWINSZ_PPC, buffer) ||
           ioctl?(TIOCGWINSZ_SOL, buffer)

          rows, cols, = buffer.unpack(TIOCGWINSZ_BUF_FMT)
          [rows, cols] if nonzero_column?(cols)
        end
      end

      # Check if the ioctl call gets window size on any standard stream
      #
      # @param [Integer] control
      #   the control code
      # @param [String] buf
      #   the window size buffer
      #
      # @return [Boolean]
      #   true if the ioctl call gets window size, false otherwise
      #
      # @api private
      def ioctl?(control, buf)
        ($stdout.ioctl(control, buf) >= 0) ||
          ($stdin.ioctl(control, buf) >= 0) ||
          ($stderr.ioctl(control, buf) >= 0)
      rescue SystemCallError
        false
      end
      module_function :ioctl?
    else
      def size_from_ioctl; nil end
    end
    module_function :size_from_ioctl

    # Detect terminal screen size from readline
    #
    # @param [Boolean] verbose
    #   the verbose mode, by default false
    #
    # @return [Array(Integer, Integer), nil]
    #   the terminal screen size or nil
    #
    # @api private
    def size_from_readline(verbose: false)
      return unless output.tty?

      require "readline" unless defined?(::Readline)
      return unless ::Readline.respond_to?(:get_screen_size)

      size = ::Readline.get_screen_size
      size if nonzero_column?(size[1])
    rescue LoadError
      warn "no readline gem" if verbose
    rescue NotImplementedError
    end
    module_function :size_from_readline

    # Detect terminal screen size from tput
    #
    # @return [Array(Integer, Integer), nil]
    #   the terminal screen size or nil
    #
    # @api private
    def size_from_tput
      return unless output.tty? && command_exist?("tput")

      lines = run_command("tput", "lines")
      return unless lines

      cols = run_command("tput", "cols")
      [lines.to_i, cols.to_i] if nonzero_column?(cols)
    end
    module_function :size_from_tput

    # Detect terminal screen size from stty
    #
    # @return [Array(Integer, Integer), nil]
    #   the terminal screen size or nil
    #
    # @api private
    def size_from_stty
      return unless output.tty? && command_exist?("stty")

      out = run_command("stty", "size")
      return unless out

      size = out.split.map(&:to_i)
      size if nonzero_column?(size[1])
    end
    module_function :size_from_stty

    # Detect terminal screen size from environment variables
    #
    # After executing Ruby code, when the user changes terminal
    # screen size during code runtime, the code will not be
    # notified, and hence will not see the new size reflected
    # in its copy of LINES and COLUMNS environment variables.
    #
    # @return [Array(Integer, Integer), nil]
    #   the terminal screen size or nil
    #
    # @api private
    def size_from_env
      return unless env["COLUMNS"] =~ /^\d+$/

      size = [(env["LINES"] || env["ROWS"]).to_i, env["COLUMNS"].to_i]
      size if nonzero_column?(size[1])
    end
    module_function :size_from_env

    # Detect terminal screen size from the ANSICON environment variable
    #
    # @return [Array(Integer, Integer), nil]
    #   the terminal screen size or nil
    #
    # @api private
    def size_from_ansicon
      return unless env["ANSICON"] =~ /\((.*)x(.*)\)/

      size = [::Regexp.last_match(2).to_i, ::Regexp.last_match(1).to_i]
      size if nonzero_column?(size[1])
    end
    module_function :size_from_ansicon

    # Check if a command exists
    #
    # @param [String] command
    #   the command to check
    #
    # @return [Boolean]
    #
    # @api private
    def command_exist?(command)
      exts = env.fetch("PATHEXT", "").split(::File::PATH_SEPARATOR)
      env.fetch("PATH", "").split(::File::PATH_SEPARATOR).any? do |dir|
        file = ::File.join(dir, command)
        ::File.exist?(file) ||
          exts.any? { |ext| ::File.exist?("#{file}#{ext}") }
      end
    end
    private_module_function :command_exist?

    # Run command capturing the standard output
    #
    # @param [Array<String>] args
    #   the command arguments
    #
    # @return [String, nil]
    #   the command output or nil
    #
    # @api private
    def run_command(*args)
      %x(#{args.join(" ")})
    rescue IOError, SystemCallError
      nil
    end
    private_module_function :run_command

    # Check if a number is non-zero
    #
    # @param [Integer, String] column
    #   the column to check
    #
    # @return [Boolean]
    #
    # @api private
    def nonzero_column?(column)
      column.to_i > 0
    end
    private_module_function :nonzero_column?
  end # Screen
end # TTY
