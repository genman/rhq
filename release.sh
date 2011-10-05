#!/bin/bash
#========================================================================================
# Usage:
# See usage function.
#
# Description: 
# RHQ release script. Supports tagging and branching model described in the community
# wiki:
# http://www.rhq-project.org/display/RHQ/Source+Control
#
# Options: 
# See usage function.
#========================================================================================


#include the utility library
source `dirname $0`/rhq_bash.lib


#========================================================================================
# Description: Display an error message and abort the script.
#========================================================================================
abort()
{
   echo >&2
   for ARG in "$@"; do
      echo "$ARG" >&2
      echo "">&2
   done
   exit 1
}


#========================================================================================
# Description: Display usage information then abort the script.
#========================================================================================
usage() 
{
   USAGE=$(
cat << EOF
USAGE:   release.sh OPTIONS

   --release-version=version              [REQUIRED]
      The release version to be tagged by this script.

   --development-version=version          [REQUIRED]
      The version under which development will continue after tagging.

   --release-branch=git_branch            [REQUIRED]
      Git branch to be used as base for tagging and/or branching.

   --release-type=community|enterprise    [REQUIRED]
      Type of release.

   --test-mode                            [OPTIONAL, DEFAULT]
      Run this script in test mode. Create a test branch from release branch and perform tagging and version updates on this test branch.

   --production-mode                      [OPTIONAL]
      Run this script in production mode. Follow the official branching and tagging model.

   --mode=test|production                 [OPTIONAL]
      Used to directly set the script mode.

   --branch                               [OPTIONAL]
      Branch from release branch before tagging the release version. And updated development version on original branch.

   --tag                                  [OPTIONAL, DEFAULT]
      Use the release branch to tag the release version. And update development version on the same branch.

   --scm-strategy=tag|branch              [OPTIONAL]
      Used to directly set the scm strategy.

   --extra-profile                        [OPTIONAL]
      An extra maven profile to be used for all the maven commands.

EOF
)

   EXAMPLE="release.sh --release-type=\"enterprise\" --release-version=\"5.0.0.GA\" --development-version=\"5.0.1-SNAPSHOT\" --release-branch=\"stefan/release_test\" --branch"

   abort "$@" "$USAGE" "$EXAMPLE"
}


#========================================================================================
# Description: Validate and parse input arguments
#========================================================================================
parse_and_validate_options()
{
   RELEASE_VERSION=
   DEVELOPMENT_VERSION=
   RELEASE_BRANCH=
   RELEASE_TYPE="community"
   MODE="test"
   SCM_STRATEGY="tag"
   EXTRA_MAVEN_PROFILE=

   short_options="h"
   long_options="help,release-version:,development-version:,release-branch:,release-type:,test-mode,production-mode,mode:,branch,tag,scm-strategy:,extra-profile:"

   PROGNAME=${0##*/}
   ARGS=$(getopt -s bash --options $short_options --longoptions $long_options --name $PROGNAME -- "$@" )
   eval set -- "$ARGS"

   while true; do
	   case $1 in
         -h|--help)
            usage
            ;;
         --release-version)
            shift
            RELEASE_VERSION="$1"
            shift
            ;;
         --development-version)
            shift
            DEVELOPMENT_VERSION="$1"
            shift
            ;;
         --release-branch)
            shift
            RELEASE_BRANCH="$1"
            shift
            ;;
         --release-type)
            shift
            RELEASE_TYPE="$1"
            shift
            ;;
         --test-mode)
            MODE="test"
            shift
            ;;
         --production-mode)
            MODE="production"
            shift
            ;;
         --mode)
            shift
            MODE="$1"
            shift
            ;;
         --tag)
            SCM_STRATEGY="tag"
            shift
            ;;
         --branch)
            SCM_STRATEGY="branch"
            shift
            ;;
         --scm-strategy)
            shift
            SCM_STRATEGY="$1"
            shift
            ;;
         --extra-profile)
            shift
            EXTRA_MAVEN_PROFILE="$1"
            shift
            ;;
         --)
            shift
            break
            ;;
         *)
            usage
            ;;
	   esac
   done

   if [ -z $RELEASE_VERSION ];
   then
      usage "Release version not specified!"
   fi

   if [ -z $DEVELOPMENT_VERSION ];
   then
      usage "Development version not specified!"
   fi

   if [ -z $RELEASE_BRANCH ];
   then
      usage "Release branch not specified!"
   fi

   if [ "$RELEASE_TYPE" != "community" ] && [ "$RELEASE_TYPE" != "enterprise" ]; then
      usage "Invalid release type: $RELEASE_TYPE (valid release types are 'community' or 'enterprise')"
   fi

   if [ "$MODE" != "test" ] && [ "$MODE" != "production" ]; then
      usage "Invalid script mode: $MODE (valid modes are 'test' or 'production')"
   fi

   if [ "$SCM_STRATEGY" != "tag" ] && [ "$SCM_STRATEGY" != "branch" ]; then
      usage "Invalid scm strategy: $SCM_STRATEGY (valid scm strategies are 'tag' or 'branch')"
   fi

   #if [ "$MODE" = "production" ]; then
   #   if [ -z "$JBOSS_ORG_USERNAME" ] || [ -z "$JBOSS_ORG_PASSWORD" ]; then
   #      usage "In production mode, jboss.org credentials must be specified via the JBOSS_ORG_USERNAME and JBOSS_ORG_PASSWORD environment variables."
   #   fi
   #fi

   print_centered "Script Options"
   script_options=( "RELEASE_VERSION" "DEVELOPMENT_VERSION" "RELEASE_BRANCH" "RELEASE_TYPE" \
                     "MODE" "SCM_STRATEGY")
   print_variables "${script_options[@]}"
}


#========================================================================================
# Description: Set all the local and environment variables required by the script.
#========================================================================================
set_local_and_environment_variables()
{
   # Set environment variables
   MAVEN_OPTS="-Xms512M -Xmx1024M -XX:PermSize=128M -XX:MaxPermSize=256M"
   export MAVEN_OPTS


   # Set various local variables
   if [ -n "$WORKSPACE" ]; then
      echo "We appear to be running in a Hudson job." 
      MAVEN_LOCAL_REPO_DIR="$WORKSPACE/.m2/repository"
      MAVEN_SETTINGS_FILE="$WORKSPACE/.m2/settings.xml"
   else
      MAVEN_LOCAL_REPO_DIR="$HOME/.m2/repository"
      MAVEN_SETTINGS_FILE="$HOME/.m2/settings.xml"
   fi

   MAVEN_ARGS="--settings $MAVEN_SETTINGS_FILE --batch-mode --errors -Penterprise,dist,release"

   if [ -n "$EXTRA_MAVEN_PROFILE" ];
   then
      MAVEN_ARGS="$MAVEN_ARGS,$EXTRA_MAVEN_PROFILE"
   fi

   if [ "$RELEASE_TYPE" = "enterprise" ];
   then
      MAVEN_ARGS="$MAVEN_ARGS -Dexclude-webdav "
   fi

   if [ -n "$RELEASE_DEBUG" ]; then
      MAVEN_ARGS="$MAVEN_ARGS --debug"
   fi

   TAG_PREFIX="RHQ"
   TAG_VERSION=`echo $RELEASE_VERSION | sed 's/\./_/g'`
   RELEASE_TAG="${TAG_PREFIX}_${TAG_VERSION}"

   # Set the system character encoding to ISO-8859-1 to ensure i18log reads its 
   # messages and writes its resource bundle properties files in that encoding, 
   # since that is how the German and French I18NMessage annotation values are
   # encoded and the encoding used by i18nlog to read in resource bundle
   # property files.
   LANG=en_US.iso8859
   export LANG

   # Print out a summary of the environment.
   print_centered "Environment Variables"
   environment_variables=("JAVA_HOME" "M2_HOME" "MAVEN_OPTS" "PATH" "LANG" "RELEASE_TYPE")
   print_variables "${environment_variables[@]}"

   print_centered "Local Variables"
   local_variables=( "RELEASE_TYPE" "DEVELOPMENT_VERSION" \
                     "RELEASE_BRANCH" "MODE" "MAVEN_LOCAL_REPO_DIR" \
                     "MAVEN_SETTINGS_FILE" "MAVEN_ARGS" "JBOSS_ORG_USERNAME" \
                     "RELEASE_VERSION" "RELEASE_TAG")
   print_variables "${local_variables[@]}"
}


#========================================================================================
# Description: Perform version update process and test the outcome by building 
#              from source.
#========================================================================================
run_release_version_and_tag_process()
{
   # 1) Perform a test build before changing version
   mvn clean install $MAVEN_ARGS -Ddbreset
   [ "$?" -ne 0 ] && abort "Test build failed. Please see output for details, fix any issues, then try again."

   # 2) Run another cleanup
   echo "Cleaning up module target dirs..."
   mvn clean $MAVEN_ARGS
   [ "$?" -ne 0 ] && abort "Failed to cleanup snbapshot jars produced by test build from module target dirs. Please see above Maven output for details, fix any issues, then try again."

   # 3) Increment version on all poms
   mvn versions:set versions:use-releases -DnewVersion=$RELEASE_VERSION  -DallowSnapshots=false -DgenerateBackupPoms=false
   [ "$?" -ne 0 ] && abort "Version set failed. Please see output for details, fix any issues, then try again."

   # 4) Perform a test build with the new version
   mvn install $MAVEN_ARGS -DskipTests=true -Ddbsetup-do-not-check-schema=true
   [ "$?" -ne 0 ] && abort "Maven build for new version failed. Please see output for details, fix any issues, then try again."

   # 5) Publish release artifacts - **FUTURE IMPROVEMENT**
   #echo "Building release from tag and publishing Maven artifacts (this will take about 10-15 minutes)..."
   #mvn deploy $MAVEN_ARGS -Dmaven.test.skip=true -Ddbsetup-do-not-check-schema=true
   #[ "$?" -ne 0 ] && abort "Release build failed. Please see above Maven output for details, fix any issues, then try again."

   # 6) Cleanup after this test build
   echo "Cleaning up module target dirs..."
   mvn clean $MAVEN_ARGS
   [ "$?" -ne 0 ] && abort "Failed to cleanup snbapshot jars produced by test build from module target dirs. Please see above Maven output for details, fix any issues, then try again."

   # 7) Commit the change in version (if everything went well so far then this is a good tag)
   git add -u
   git commit -m "tag $RELEASE_TAG"
   
   # 8) Tag the current source
   git tag "$RELEASE_TAG"

   # 9) If everything went well so far than means all the changes can be pushed!!!
   git push origin "$BUILD_BRANCH"
   git push origin "$RELEASE_TAG"
}


#========================================================================================
# Description: Update the version for the development branch.
#========================================================================================
update_development_version()
{
   # 1) Set version to the current development version
   mvn versions:set versions:use-releases -DnewVersion=$DEVELOPMENT_VERSION  -DallowSnapshots=false -DgenerateBackupPoms=false
   [ "$?" -ne 0 ] && abort "Version set failed. Please see output for details, fix any issues, then try again."

   # 2) Commit the change in version
   git add -u
   git commit -m "development RHQ_$DEVELOPMENT_VERSION"

   # 3) If everything went well so far than means all the changes can be pushed!!!
   git push origin "$BUILD_BRANCH"
}


#========================================================================================
# Description: Perform tag verification process
#              1) if the tag already exists on the remote repo than abort the script
#              2) if the tag exists only locally then delete it, that means there was
#                 error during the previous run of this script. Tags are committed to
#                 repo only this script runs successfully.
#========================================================================================
verify_tags()
{
   # If the specified tag already exists remotely and we're in production mode, then abort. If it exists and
   # we're in test mode, delete it
   EXISTING_REMOTE_TAG=`git ls-remote --tags origin "$RELEASE_TAG"`
   if [ -n "$EXISTING_REMOTE_TAG" ];
   then
      abort "A remote tag named $RELEASE_TAG already exists - aborting"
   fi

   # See if the specified tag already exists locally - if so, delete it (even if in production mode).
   # If the tag is just local then there were errors during the last run; no harm in removing it.
   EXISTING_LOCAL_TAG=`git tag -l "$RELEASE_TAG"`
   if [ -n "$EXISTING_LOCAL_TAG" ];
   then
      echo "A local tag named $RELEASE_TAG already exists - deleting it..."
      git tag -d "$RELEASE_TAG"
      [ "$?" -ne 0 ] && abort "Failed to delete local tag ($RELEASE_TAG)."
   fi
}


#========================================================================================
# Description: Run the validation process for all the system utilities needed by
#              the script. At the end print the version of each utility.
#========================================================================================
validate_system_utilities()
{
   # TODO: Check that JDK version is < 1.7.

   validate_java_6

   validate_java_5

   validate_maven

   validate_git

   print_centered "Program Versions"
   program_versions=("git --version" "java -version" "mvn --version")
   print_program_versions "${program_versions[@]}"
}


#========================================================================================
# Description: Prints the release information.
#========================================================================================
print_release_information()
{
   echo
   print_centered "Release Info"
   echo "Version: $RELEASE_VERSION"
   echo "Branch URL: http://git.fedorahosted.org/git/?p=rhq/rhq.git;a=shortlog;h=refs/heads/$RELEASE_BRANCH"
   echo "Tag URL: http://git.fedorahosted.org/git/?p=rhq/rhq.git;a=shortlog;h=refs/tags/$RELEASE_TAG"
   print_centered "="
}


#========================================================================================
# Description: Checkout release branch.
#========================================================================================
checkout_release_branch()
{
   # Checkout the source from git, assume that the git repo is already cloned
   git status >/dev/null 2>&1
   GIT_STATUS_EXIT_CODE=$?
   # Note, git 1.6 and earlier returns an exit code of 1, rather than 0, if there are any uncommitted changes,
   # and git 1.7 returns 0, so we check if the exit code is less than or equal to 1 to determine if current folder
   # is truly a git working copy.
   if [ "$GIT_STATUS_EXIT_CODE" -le 1 ];
   then
       echo "Checking out a clean copy of the release branch ($RELEASE_BRANCH)..."
       git fetch origin "$RELEASE_BRANCH"
       [ "$?" -ne 0 ] && abort "Failed to fetch release branch ($RELEASE_BRANCH)."

       git checkout "$RELEASE_BRANCH" 2>/dev/null
       if [ "$?" -ne 0 ];
       then
           git checkout --track -b "$RELEASE_BRANCH" "origin/$RELEASE_BRANCH"
       fi

       [ "$?" -ne 0 ] && abort "Failed to checkout release branch ($RELEASE_BRANCH)."
       git reset --hard "origin/$RELEASE_BRANCH"
       [ "$?" -ne 0 ] && abort "Failed to reset release branch ($RELEASE_BRANCH)."
       git clean -dxf
       [ "$?" -ne 0 ] && abort "Failed to clean release branch ($RELEASE_BRANCH)."
       git pull origin $RELEASE_BRANCH
       [ "$?" -ne 0 ] && abort "Failed to update release branch ($RELEASE_BRANCH)."
   else
       echo "Current folder does not appear to be a git working directory ('git status' returned $GIT_STATUS_EXIT_CODE) - removing it so we can freshly clone the repo..."
   fi
}


#========================================================================================
# Description: Checkout or create the build branch for the release process.
#========================================================================================
checkout_build_branch_for_release()
{
   # if this is a test build then create a temporary build branch off of RELEASE_BRANCH.  This allows checkins to
   # continue in RELEASE_BRANCH without affecting the release plugin work, which will fail if the branch contents
   # change before it completes.
   if [ "$MODE" = "test" ];
   then
      BUILD_BRANCH="${RELEASE_BRANCH}-test-build"
      # delete the branch if it exists, so we can recreate it fresh
      EXISTING_BUILD_BRANCH=`git ls-remote --heads origin "$BUILD_BRANCH"`
      if [ -n "$EXISTING_BUILD_BRANCH" ];
      then
          echo "Deleting remote branch origin/$BUILD_BRANCH"
          git branch -D -r "origin/$BUILD_BRANCH"
          echo "Deleting local branch $BUILD_BRANCH"
          git branch -D "$BUILD_BRANCH"
      fi

      echo "Creating and checking out local branch $BUILD_BRANCH from $RELEASE_BRANCH"
      git checkout -b "$BUILD_BRANCH"
   else
      if [ "$SCM_STRATEGY" = "tag" ];
      then
         BUILD_BRANCH="${RELEASE_BRANCH}"
      else
         BUILD_BRANCH="release-$TAG_VERSION"
         # delete the branch if it exists, so we can recreate it fresh
         EXISTING_BUILD_BRANCH=`git ls-remote --heads origin "$BUILD_BRANCH"`
         if [ -n "$EXISTING_BUILD_BRANCH" ];
         then
            abort "Remote repository already contains $BUILD_BRANCH."
         fi

         echo "Creating and checking out local branch $BUILD_BRANCH from $RELEASE_BRANCH"
         git checkout -b "$BUILD_BRANCH"
      fi
   fi
}


#========================================================================================
# Description: Checkout or create the build branch for updating the development version.
#========================================================================================
checkout_build_branch_for_development()
{
   if [ "$MODE" = "production" ];
   then
      if [ "$SCM_STRATEGY" = "branch" ];
      then
         checkout_release_branch
         BUILD_BRANCH="${RELEASE_BRANCH}"
      fi
   fi
}


if [ -n "$RELEASE_DEBUG" ];
then
   echo "Debug output is enabled."
   set -x
fi




############ MAIN SCRIPT ############

parse_and_validate_options $@

validate_system_utilities

set_local_and_environment_variables

verify_tags

checkout_release_branch

checkout_build_branch_for_release

run_release_version_and_tag_process

checkout_build_branch_for_development

update_development_version

print_release_information
