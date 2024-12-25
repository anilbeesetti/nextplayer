# frozen_string_literal: true

module Aws
  module S3
    module Plugins

      # A handful of Amazon S3 operations will respond with a 200 status
      # code but will send an error in the response body. This plugin
      # injects a handler that will parse 200 response bodies for potential
      # errors, allowing them to be retried.
      # @api private
      class Http200Errors < Seahorse::Client::Plugin

        class Handler < Seahorse::Client::Handler

          def call(context)
            @handler.call(context).on(200) do |response|
              return response if streaming_output?(context.operation.output)

              error = check_for_error(context)
              return response unless error

              context.http_response.status_code = 500
              response.data = nil
              response.error = error
            end
          end

          private

          # Streaming outputs are not subject to 200 errors.
          def streaming_output?(output)
            if (payload = output[:payload_member])
              # checking ref and shape
              payload['streaming'] || payload.shape['streaming'] ||
                payload.eventstream
            else
              false
            end
          end

          # Checks if the output shape is a structure shape and has members that
          # are in the body for the case of a payload and a normal structure. A
          # non-structure shape will not have members in the body. In the case
          # of a string or blob, the body contents would have been checked first
          # before this method is called in incomplete_xml_body?.
          def members_in_body?(output)
            shape =
              if output[:payload_member]
                output[:payload_member].shape
              else
                output.shape
              end

            if structure_shape?(shape)
              shape.members.any? { |_, k| k.location.nil? }
            else
              false
            end
          end

          def structure_shape?(shape)
            shape.is_a?(Seahorse::Model::Shapes::StructureShape)
          end

          # Must have a member in the body and have the start of an XML Tag.
          # Other incomplete xml bodies will result in an XML ParsingError.
          def incomplete_xml_body?(xml, output)
            members_in_body?(output) && !xml.match(/<\w/)
          end

          def check_for_error(context)
            xml = context.http_response.body_contents
            if xml.match(/<\?xml\s[^>]*\?>\s*<Error>/)
              error_code = xml.match(%r{<Code>(.+?)</Code>})[1]
              error_message = xml.match(%r{<Message>(.+?)</Message>})[1]
              S3::Errors.error_class(error_code).new(context, error_message)
            elsif incomplete_xml_body?(xml, context.operation.output)
              Seahorse::Client::NetworkingError.new(
                S3::Errors
                  .error_class('InternalError')
                  .new(context, 'Empty or incomplete response body')
              )
            end
          end
        end

        handler(Handler, step: :sign)
      end
    end
  end
end
