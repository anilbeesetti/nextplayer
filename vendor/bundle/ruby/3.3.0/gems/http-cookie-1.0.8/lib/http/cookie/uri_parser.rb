module HTTP::Cookie::URIParser
  module_function

  # Regular Expression taken from RFC 3986 Appendix B
  URIREGEX = %r{
    \A
    (?: (?<scheme> [^:/?\#]+ ) : )?
    (?: // (?<authority> [^/?\#]* ) )?
    (?<path> [^?\#]* )
    (?: \? (?<query> [^\#]* ) )?
    (?: \# (?<fragment> .* ) )?
    \z
  }x

  # Escape RFC 3986 "reserved" characters minus valid characters for path
  # More specifically, gen-delims minus "/" / "?" / "#"
  def escape_path(path)
    path.sub(/\A[^?#]+/) { |p| p.gsub(/[:\[\]@]+/) { |r| CGI.escape(r) } }
  end

  # Parse a URI string or object, relaxing the constraints on the path component
  def parse(uri)
    URI(uri)
  rescue URI::InvalidURIError
    str = String.try_convert(uri) or
      raise ArgumentError, "bad argument (expected URI object or URI string)"

    m = URIREGEX.match(str) or raise

    path = m[:path]
    str[m.begin(:path)...m.end(:path)] = escape_path(path)
    uri = URI.parse(str)
    uri.__send__(:set_path, path)
    uri
  end
end
