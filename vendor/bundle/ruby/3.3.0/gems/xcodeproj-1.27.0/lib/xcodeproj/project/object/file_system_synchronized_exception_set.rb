require 'xcodeproj/project/object_attributes'
require 'xcodeproj/project/object/helpers/groupable_helper'

module Xcodeproj
  class Project
    module Object
      # This class represents a file system synchronized build file exception set.
      class PBXFileSystemSynchronizedBuildFileExceptionSet < AbstractObject
        # @return [AbstractTarget] The target to which this exception set applies.
        #
        has_one :target, AbstractTarget

        # @return [Array<String>] The list of files in the group that are excluded from the target.
        #
        attribute :membership_exceptions, Array

        # @return [Array<String>] The list of public headers.
        #
        attribute :public_headers, Array

        # @return [Array<String>] The list of private headers.
        #
        attribute :private_headers, Array

        # @return [Hash] The files with specific compiler flags.
        #
        attribute :additional_compiler_flags_by_relative_path, Hash

        # @return [Hash] The files with specific attributes.
        #
        attribute :attributes_by_relative_path, Hash

        # @return [Hash] The files with a platform filter.
        #
        attribute :platform_filters_by_relative_path, Hash

        def display_name
          "Exceptions for \"#{GroupableHelper.parent(self).display_name}\" folder in \"#{target.name}\" target"
        end
      end

      # This class represents a file system synchronized group build phase membership exception set.
      class PBXFileSystemSynchronizedGroupBuildPhaseMembershipExceptionSet < AbstractObject
        # @return [PBXSourcesBuildPhase, PBXCopyFilesBuildPhase] The build phase to which this exception set applies.
        #
        has_one :build_phase, [PBXSourcesBuildPhase, PBXCopyFilesBuildPhase]

        # @return [Array<String>] The list of files in the group that are excluded from the build phase.
        #
        attribute :membership_exceptions, Array

        # @return [Hash] The files with a platform filter.
        #
        attribute :platform_filters_by_relative_path, Hash

        def display_name
          "Exceptions for \"#{GroupableHelper.parent(self).display_name}\" folder in \"#{build_phase.name}\" build phase"
        end
      end
    end
  end
end
