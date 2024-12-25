# frozen_string_literal: true

module Aws
  class ErrorHandler < Seahorse::Client::Handler

    private

    def error(context)
      body = context.http_response.body_contents
      if body.empty?
        code, message, data = http_status_error(context)
      else
        code, message, data = extract_error(body, context)
      end
      build_error(context, code, message, data)
    end

    def build_error(context, code, message, data)
      errors_module = context.client.class.errors_module
      errors_module.error_class(code).new(context, message, data)
    end

    def http_status_error(context)
      [http_status_error_code(context), '', EmptyStructure.new]
    end

    def http_status_error_code(context)
      status_code = context.http_response.status_code
      {
        302 => 'MovedTemporarily',
        304 => 'NotModified',
        400 => 'BadRequest',
        403 => 'Forbidden',
        404 => 'NotFound',
        412 => 'PreconditionFailed',
        413 => 'RequestEntityTooLarge',
      }[status_code] || "Http#{status_code}Error"
    end

  end
end
