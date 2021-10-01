/*
 * Copyright 2020 Realm Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import groovy.json.JsonOutput

@Library('realm-ci') _

// Branches from which we release SNAPSHOT's. Only release branches need to run on actual hardware.
releaseBranches = [ 'master', 'releases', 'next-major' ]
// Branches that are "important", so if they do not compile they will generate a Slack notification
slackNotificationBranches = [ 'master', 'releases', 'next-major' ]
// Shortcut to current branch name that is being tested
currentBranch = env.BRANCH_NAME
// Will be set to `true` if this build is a full release that should be available on Bintray.
// This is determined by comparing the current git tag to the version number of the build.
publishBuild = false
// Version of Realm Kotlin being tested. This values is defined in `buildSrc/src/main/kotlin/Config.kt`.
version = null
// Wether or not to run test steps
runTests = true
isReleaseBranch = releaseBranches.contains(currentBranch)

// References to Docker containers holding the MongoDB Test server and infrastructure for
// controlling it.
dockerNetworkId = UUID.randomUUID().toString()
mongoDbRealmContainer = null
mongoDbRealmCommandServerContainer = null

// Mac CI dedicated machine
node_label = 'osx_kotlin'

// When having multiple executors available, Jenkins might append @2/@3/etc. to workspace folders in order
// to allow multiple parallel builds on the same branch. Unfortunately this breaks Ninja and thus us
// building native code. To work around this, we force the workspace to mirror the git path.
// This has two side-effects: 1) It isn't possible to use this JenkinsFile on a worker with multiple
// executors. At least not if we want to support building multiple versions of the same PR.
workspacePath = "/Users/realm/workspace-realm-kotlin/${currentBranch}"

rlmNode('osx_kotlin') {
    stage('SCM') {
        runScm()
    }
}

rlmNode('docker') {
    stage('build-linux') {
        unstash 'packages'
        dir('packages') {
            docker.build('jvm_linux', '-f cinterop/src/jvmMain/linux/generic.Dockerfile .').inside {
                sh """
                   cd cinterop/src/jvmMain/linux/
                   rm -rf build-dir
                   mkdir build-dir
                   cd build-dir
                   cmake ..
                   make -j8
                """

                archiveArtifacts artifacts: 'cinterop/src/jvmMain/linux/build-dir/core/src/realm/object-store/c_api/librealm-ffi.so,cinterop/src/jvmMain/linux/build-dir/librealmc.so', allowEmptyArchive: true
                stash includes:"cinterop/src/jvmMain/linux/build-dir/core/src/realm/object-store/c_api/librealm-ffi.so,cinterop/src/jvmMain/linux/build-dir/librealmc.so", name: 'linux_so_files'
            }
        }
    }
}

rlmNode('aws-windows-01') { // has jdk 1.8
  unstash 'packages'

  def cmakeOptions = [
        CMAKE_GENERATOR_PLATFORM: 'x64',
        CMAKE_BUILD_TYPE: 'Release',
        REALM_ENABLE_SYNC: "ON",
        CMAKE_TOOLCHAIN_FILE: "c:\\src\\vcpkg\\scripts\\buildsystems\\vcpkg.cmake",
        CMAKE_SYSTEM_VERSION: '8.1',
        REALM_NO_TESTS: '1',
        VCPKG_TARGET_TRIPLET: 'x64-windows-static'

      ]

  def cmakeDefinitions = cmakeOptions.collect { k,v -> "-D$k=$v" }.join(' ')
  dir('packages') {
      bat "cd cinterop\\src\\jvmMain\\windows && mkdir build-dir && cd build-dir &&  \"${tool 'cmake'}\" ${cmakeDefinitions} .. && \"${tool 'cmake'}\" --build . --config Release"
  }
  archiveArtifacts artifacts: 'packages/cinterop/src/jvmMain/windows/build-dir/core/src/realm/object-store/c_api/Release/realm-ffi.dll,packages/cinterop/src/jvmMain/windows/build-dir/Release/realmc.dll', allowEmptyArchive: true
  stash includes: 'packages/cinterop/src/jvmMain/windows/build-dir/core/src/realm/object-store/c_api/Release/realm-ffi.dll,packages/cinterop/src/jvmMain/windows/build-dir/Release/realmc.dll', name: 'win_dlls'
}

def environment() {
    return [
        "ANDROID_SDK_ROOT=/Users/realm/Library/Android/sdk/",
        "NDK_HOME='/Users/realm/Library/Android/sdk/ndk/22.0.6917172",
        "ANDROID_NDK=$NDK_HOME",
        "ANDROID_NDK_HOME=$NDK_HOME",
        "REALM_DISABLE_ANALYTICS=true",
        "JAVA_8=/Library/Java/JavaVirtualMachines/jdk1.8.0_301.jdk/Contents/Home",
        "JAVA_11=/Library/Java/JavaVirtualMachines/jdk-11.0.12.jdk/Contents/Home",
        "JAVA_HOME=$JAVA_11",
        ]
}

def runScm() {
    def repoExtensions = [
        [$class: 'SubmoduleOption', recursiveSubmodules: true]
    ]
    if (isReleaseBranch) {
        repoExtensions += [
            [$class: 'WipeWorkspace'],
            [$class: 'CleanCheckout'],
        ]
    }
    checkout([
            $class           : 'GitSCM',
            branches         : scm.branches,
            gitTool          : 'native git',
            extensions       : scm.extensions + repoExtensions,
            userRemoteConfigs: scm.userRemoteConfigs
    ])

    // Check type of Build. We are treating this as a release build if we are building
    // the exact Git SHA that was tagged.
    gitTag = readGitTag()
    version = sh(returnStdout: true, script: 'grep "const val version" buildSrc/src/main/kotlin/Config.kt | cut -d \\" -f2').trim()
    echo "Git branch/tag: ${currentBranch}/${gitTag ?: 'none'}"
    if (!gitTag) {
        gitSha = sh(returnStdout: true, script: 'git rev-parse HEAD').trim().take(8)
        echo "Building commit: ${version} - ${gitSha}"
        setBuildName(gitSha)
        publishBuild = false
    } else {
        if (gitTag != "v${version}") {
            error "Git tag '${gitTag}' does not match v${version}"
        } else {
            echo "Building release: '${gitTag}'"
            setBuildName("Tag ${gitTag}")
            publishBuild = true
        }
    }

    stash includes: 'packages/**', name: 'packages'
}

def readGitTag() {
    def command = 'git describe --exact-match --tags HEAD'
    def returnStatus = sh(returnStatus: true, script: command)
    if (returnStatus != 0) {
        return null
    }
    return sh(returnStdout: true, script: command).trim()
}
