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

pipeline {
     agent none
     options {
        // In Realm Java, we had to lock the entire build as sharing the global Gradle
        // cache was causing issues. We never discovered the root cause, but
        // https://github.com/gradle/gradle/issues/851 seems to indicate that the problem
        // is when running builds inside Docker containers that share a host .gradle
        // folder.
        //
        // This isn't the case for Kotlin, so it seems safe to remove the lock.
        // Locking is furthermore complicated by the fact that there doesn't seem an
        // easy way to grap a node-lock for pipeline syntax builds.
        // https://stackoverflow.com/a/44758361/1389357.
        //
        // So in summary, removing the lock should work fine. I'm mostly keeping this
        // description in case we run into problems down the line.

        // lock resource: 'kotlin_build_lock'
        // Overall job timeout. This doesn't include time for waiting on the agent.
        // Setting 'activity'-timeouts here doesn't clear the overall job timeout, so
        // not an option. More finegrained timeouts can be targeted at specific
        // stages, but these will include the time waiting for resources/nodes.
        timeout(time: 120, unit: 'MINUTES')
    }
    environment {
          ANDROID_SDK_ROOT='/Users/realm/Library/Android/sdk/'
          NDK_HOME='/Users/realm/Library/Android/sdk/ndk/22.0.6917172'
          ANDROID_NDK="${NDK_HOME}"
          ANDROID_NDK_HOME="${NDK_HOME}"
          REALM_DISABLE_ANALYTICS=true
          JAVA_8='/Library/Java/JavaVirtualMachines/jdk1.8.0_301.jdk/Contents/Home'
          JAVA_11='/Library/Java/JavaVirtualMachines/jdk-11.0.12.jdk/Contents/Home'
          JAVA_HOME="${JAVA_11}"
    }
    stages {
        stage('Prepare CI') {
            // Force all stages to use the same node, so we can take advantage
            // of the gradle cache between steps, otherwise Jenkins are free
            // to move a stage to a different node.
            agent {
                node {
                    label node_label
                    customWorkspace workspacePath
                }
             }
            stages {
                stage('SCM') {
                    steps {
                        runScm()
                        setBuildDetails()
                        genAndStashSwigJNI()
                    }
                }

                stage('build-jvm-native-libs') {
                    parallel{
                      stage('build_jvm_linux') {
                          when { expression { shouldBuildJvmABIs() } }
                          agent {
                              node {
                                  label 'docker'
                              }
                          }
                          steps {
                              // It is an order of magnitude faster to checkout the repo
                              // rather then stashing/unstashing all files to build Linux and Win
                              runScm()
                              build_jvm_linux()
                          }
                      }
                      stage('build_jvm_windows') {
                          when { expression { shouldBuildJvmABIs() } }
                          agent {
                              node {
                                   // FIXME aws-windows-02 has issue with checking out the repo with symlinks
                                  label 'aws-windows-01'
                              }
                          }
                          steps {
                            runScm()
                            build_jvm_windows()
                          }
                      }
                    }
                }

                stage('Build') {
                    steps {
                        runBuild()
                    }
                }
                stage('Static Analysis') {
                    when { expression { runTests } }
                    steps {
                        runStaticAnalysis()
                    }
                }
                stage('Tests Compiler Plugin') {
                    when { expression { runTests } }
                    steps {
                        runCompilerPluginTest()
                    }
                }
                stage('Tests macOS - Unit Tests') {
                    when { expression { runTests } }
                    steps {
                        testAndCollect("packages", "macosTest")
                    }
                }
                stage('Tests Android - Unit Tests') {
                    when { expression { runTests } }
                    steps {
                        withLogcatTrace(
                            "unittest",
                            {
                                testAndCollect("packages", "connectedAndroidTest")
                            }
                        )
                    }
                }
                stage('Integration Tests - macOS') {
                    when { expression { runTests } }
                    steps {
                        testWithServer([
                            {
                                testAndCollect("test", "macosTest")
                            },
                            {
                                withLogcatTrace(
                                    "integrationtest",
                                    {
                                        forwardAdbPorts()
                                        testAndCollect("test", "connectedAndroidTest")
                                    }
                                )
                            }
                        ])
                    }
                }
                stage('Tests JVM') {
                    when { expression { runTests } }
                    steps {
                          testAndCollect("test", ':base:jvmTest --tests "io.realm.test.compiler*"')
                          testAndCollect("test", ':base:jvmTest --tests "io.realm.test.shared*"')
                          testWithServer([
                              { testAndCollect("test", ':sync:jvmTest') }
                          ])
                    }
                }
                stage('Integration Tests - iOS') {
                    when { expression { runTests } }
                    steps {
                        testWithServer([
                            {
                                testAndCollect("test", "iosTest")
                            }
                        ])
                    }
                }
                stage('Tests Android Sample App') {
                    when { expression { runTests } }
                    steps {
                        catchError(buildResult: 'FAILURE', stageResult: 'FAILURE') {
                            runMonkey()
                        }
                    }
                }
                stage('Build Android on minimum versions') {
                    when { expression { runTests } }
                    steps {
                        runBuildMinAndroidApp()
                    }
                }
                stage('Publish SNAPSHOT to Maven Central') {
                    when { expression { shouldPublishSnapshot(version) } }
                    steps {
                        runPublishSnapshotToMavenCentral()
                    }
                }
                stage('Publish Release to Maven Central') {
                    when { expression { publishBuild } }
                    steps {
                        runPublishReleaseOnMavenCentral()
                    }
                }
            }
        }
    }
    post {
        failure {
            notifySlackIfRequired("*The realm-kotlin/${currentBranch} branch is broken!*")
        }
        unstable {
            notifySlackIfRequired("*The realm-kotlin/${currentBranch} branch is unstable!*")
        }
        fixed {
            notifySlackIfRequired("*The realm-kotlin/${currentBranch} branch has been fixed!*")
        }
    }
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
}

def setBuildDetails() {
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
}

def genAndStashSwigJNI() {
    withEnv(['PATH+USER_BIN=/usr/local/bin']) {
        sh """
        cd packages/jni-swig-stub
        ../gradlew assemble
        """
    }
    stash includes: 'packages/jni-swig-stub/build/generated/sources/jni/realmc.cpp,packages/jni-swig-stub/build/generated/sources/jni/realmc.h', name: 'swig_jni'
}
def runBuild() {
    def buildJvmAbiFlag = "-PcopyJvmABIs=false"
    if (shouldBuildJvmABIs()) {
        unstash name: 'linux_so_file'
        unstash name: 'win_dll'
        buildJvmAbiFlag = "-PcopyJvmABIs=true"
    }

    withCredentials([
        [$class: 'StringBinding', credentialsId: 'maven-central-kotlin-ring-file', variable: 'SIGN_KEY'],
        [$class: 'StringBinding', credentialsId: 'maven-central-kotlin-ring-file-password', variable: 'SIGN_KEY_PASSWORD'],
    ]) {
        withEnv(['PATH+USER_BIN=/usr/local/bin']) {
            startEmulatorInBgIfNeeded()
            def signingFlags = ""
            if (isReleaseBranch) {
                signingFlags = "-PsignBuild=true -PsignSecretRingFileKotlin=\"${env.SIGN_KEY}\" -PsignPasswordKotlin=${env.SIGN_KEY_PASSWORD}"
            }
            sh """
                  cd packages
                  chmod +x gradlew && ./gradlew assemble ${buildJvmAbiFlag} ${signingFlags} publishAllPublicationsToBuildFolderRepository --info --stacktrace --no-daemon
               """
        }
    }
    archiveArtifacts artifacts: 'packages/cinterop/src/jvmMain/resources/**/*.*', allowEmptyArchive: true

}

def runStaticAnalysis() {
    try {
        sh '''
        ./gradlew --no-daemon ktlintCheck detekt
        '''
    } finally {
        // CheckStyle Publisher plugin is deprecated and does not support multiple Checkstyle files
        // New Generation Warnings plugin throw a NullPointerException when used with recordIssues()
        // As a work-around we just stash the output of Ktlint and Detekt for manual inspection.
        sh '''
                rm -rf /tmp/ktlint
                rm -rf /tmp/detekt
                mkdir /tmp/ktlint
                mkdir /tmp/detekt
                rsync -a --delete --ignore-errors examples/kmm-sample/androidApp/build/reports/ktlint/ /tmp/ktlint/example/ || true
                rsync -a --delete --ignore-errors test/build/reports/ktlint/ /tmp/ktlint/test/ || true
                rsync -a --delete --ignore-errors packages/library-base/build/reports/ktlint/ /tmp/ktlint/library-base/ || true
                rsync -a --delete --ignore-errors packages/library-sync/build/reports/ktlint/ /tmp/ktlint/library-sync/ || true
                rsync -a --delete --ignore-errors packages/plugin-compiler/build/reports/ktlint/ /tmp/ktlint/plugin-compiler/ || true
                rsync -a --delete --ignore-errors packages/gradle-plugin/build/reports/ktlint/ /tmp/ktlint/plugin-gradle/ || true
                rsync -a --delete --ignore-errors packages/runtime-api/build/reports/ktlint/ /tmp/ktlint/runtime-api/ || true
                rsync -a --delete --ignore-errors examples/kmm-sample/androidApp/build/reports/detekt/ /tmp/detekt/example/ || true
                rsync -a --delete --ignore-errors test/build/reports/detekt/ /tmp/detekt/test/ || true
                rsync -a --delete --ignore-errors packages/library-base/build/reports/detekt/ /tmp/detekt/library-base/ || true
                rsync -a --delete --ignore-errors packages/library-sync/build/reports/detekt/ /tmp/detekt/library-sync/ || true
                rsync -a --delete --ignore-errors packages/plugin-compiler/build/reports/detekt/ /tmp/detekt/plugin-compiler/ || true
                rsync -a --delete --ignore-errors packages/gradle-plugin/build/reports/detekt/ /tmp/detekt/plugin-gradle/ || true
                rsync -a --delete --ignore-errors packages/runtime-api/build/reports/detekt/ /tmp/detekt/runtime-api/ || true
            '''
        sh 'rm ktlint.zip || true'
        zip([
                'zipFile': 'ktlint.zip',
                'archive': true,
                'dir'    : '/tmp/ktlint'
        ])
        sh 'rm detekt.zip || true'
        zip([
                'zipFile': 'detekt.zip',
                'archive': true,
                'dir'    : '/tmp/detekt'
        ])
    }
}

def runPublishSnapshotToMavenCentral() {
    withEnv(['PATH+USER_BIN=/usr/local/bin']) {
        withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: 'maven-central-credentials', passwordVariable: 'MAVEN_CENTRAL_PASSWORD', usernameVariable: 'MAVEN_CENTRAL_USER']]) {
            sh """
            cd packages
            ./gradlew publishToSonatype -PossrhUsername=${env.MAVEN_CENTRAL_USER} -PossrhPassword=${env.MAVEN_CENTRAL_PASSWORD} --no-daemon
            """
        }
    }
}

def runPublishReleaseOnMavenCentral() {
    withCredentials([
            [$class: 'StringBinding', credentialsId: 'maven-central-kotlin-ring-file', variable: 'SIGN_KEY'],
            [$class: 'StringBinding', credentialsId: 'maven-central-kotlin-ring-file-password', variable: 'SIGN_KEY_PASSWORD'],
            [$class: 'StringBinding', credentialsId: 'slack-webhook-java-ci-channel', variable: 'SLACK_URL_CI'],
            [$class: 'StringBinding', credentialsId: 'slack-webhook-releases-channel', variable: 'SLACK_URL_RELEASE'],
            [$class: 'StringBinding', credentialsId: 'gradle-plugin-portal-key', variable: 'GRADLE_PORTAL_KEY'],
            [$class: 'StringBinding', credentialsId: 'gradle-plugin-portal-secret', variable: 'GRADLE_PORTAL_SECRET'],
            [$class: 'UsernamePasswordMultiBinding', credentialsId: 'maven-central-credentials', passwordVariable: 'MAVEN_CENTRAL_PASSWORD', usernameVariable: 'MAVEN_CENTRAL_USER'],
            [$class: 'AmazonWebServicesCredentialsBinding', accessKeyVariable: 'DOCS_S3_ACCESS_KEY', credentialsId: 'mongodb-realm-docs-s3', secretKeyVariable: 'DOCS_S3_SECRET_KEY'],
            [$class: 'AmazonWebServicesCredentialsBinding', accessKeyVariable: 'REALM_S3_ACCESS_KEY', credentialsId: 'realm-s3', secretKeyVariable: 'REALM_S3_SECRET_KEY']
    ]) {
      sh """
        set +x
        sh tools/publish_release.sh '$MAVEN_CENTRAL_USER' '$MAVEN_CENTRAL_PASSWORD' \
        '$REALM_S3_ACCESS_KEY' '$REALM_S3_SECRET_KEY' \
        '$DOCS_S3_ACCESS_KEY' '$DOCS_S3_SECRET_KEY' \
        '$SLACK_URL_RELEASE' '$SLACK_URL_CI' \
        '$GRADLE_PORTAL_KEY' '$GRADLE_PORTAL_SECRET' \
        '-PsignBuild=true -PsignSecretRingFileKotlin="$SIGN_KEY" -PsignPasswordKotlin=$SIGN_KEY_PASSWORD'
      """
    }
}

def runCompilerPluginTest() {
    withEnv(['PATH+USER_BIN=/usr/local/bin']) {
        sh """
            cd packages
            ./gradlew --no-daemon :plugin-compiler:test --info --stacktrace
        """
        // See https://stackoverflow.com/a/51206394/1389357
        script {
            def testResults = findFiles(glob: "packages/plugin-compiler/build/**/TEST-*.xml")
            for(xml in testResults) {
                touch xml.getPath()
            }
        }
        step([ $class: 'JUnitResultArchiver', allowEmptyResults: true, testResults: "packages/plugin-compiler/build/**/TEST-*.xml"])
    }
}

def testWithServer(tasks) {
    // Work-around for https://github.com/docker/docker-credential-helpers/issues/82
    withCredentials([
            [$class: 'StringBinding', credentialsId: 'realm-kotlin-ci-password', variable: 'PASSWORD'],
    ]) {
        sh "security -v unlock-keychain -p $PASSWORD"
    }

    try {
        // Prepare Docker containers with MongoDB Realm Test Server infrastructure for
        // integration tests.
        // TODO: How much of this logic can be moved to start_server.sh for shared logic with local testing.
        def props = readProperties file: 'dependencies.list'
        echo "Version in dependencies.list: ${props.MONGODB_REALM_SERVER}"
        def mdbRealmImage = docker.image("docker.pkg.github.com/realm/ci/mongodb-realm-test-server:${props.MONGODB_REALM_SERVER}")
        docker.withRegistry('https://docker.pkg.github.com', 'github-packages-token') {
          mdbRealmImage.pull()
        }
        def commandServerEnv = docker.build 'mongodb-realm-command-server', "tools/sync_test_server"
        def tempDir = runCommand('mktemp -d -t app_config.XXXXXXXXXX')
        sh "tools/sync_test_server/app_config_generator.sh ${tempDir} tools/sync_test_server/app_template testapp1 testapp2"

        sh "docker network create ${dockerNetworkId}"
        mongoDbRealmContainer = mdbRealmImage.run("--rm -i -t -d --network ${dockerNetworkId} -v$tempDir:/apps -p9090:9090 -p8888:8888 -p26000:26000")
        mongoDbRealmCommandServerContainer = commandServerEnv.run("--rm -i -t -d --network container:${mongoDbRealmContainer.id} -v$tempDir:/apps")
        sh "timeout 60 sh -c \"while [[ ! -f $tempDir/testapp1/app_id || ! -f $tempDir/testapp2/app_id ]]; do echo 'Waiting for server to start'; sleep 1; done\""

        // Techinically this is only needed for Android, but since all tests are
        // executed on same host and tasks are grouped in same stage we just do it
        // here
        forwardAdbPorts()

        tasks.each { task ->
            task()
        }
    } finally {
        // We assume that creating these containers and the docker network can be considered an atomic operation.
        if (mongoDbRealmContainer != null && mongoDbRealmCommandServerContainer != null) {
            try {
                archiveServerLogs(mongoDbRealmContainer.id, mongoDbRealmCommandServerContainer.id)
            } finally {
                mongoDbRealmContainer.stop()
                mongoDbRealmCommandServerContainer.stop()
                sh "docker network rm ${dockerNetworkId}"
            }
        }
    }
}

def withLogcatTrace(name, task) {
    try {
       backgroundPid = startLogCatCollector(name)
       task()
    } finally {
        stopLogCatCollector(backgroundPid, name)
    }
}
String startLogCatCollector(name) {
  // Cancel build quickly if no device is available. The lock acquired already should
  // ensure we have access to a device. If not, it is most likely a more severe problem.
  timeout(time: 1, unit: 'MINUTES') {
    // Need ADB as root to clear all buffers: https://stackoverflow.com/a/47686978/1389357
    sh '$ANDROID_SDK_ROOT/platform-tools/adb devices'
    sh """
      $ANDROID_SDK_ROOT/platform-tools/adb root
      $ANDROID_SDK_ROOT/platform-tools/adb logcat -b all -c
      $ANDROID_SDK_ROOT/platform-tools/adb logcat -v time > 'logcat-${name}.txt' &
      echo \$! > pid
    """
    return readFile("pid").trim()
  }
}

def stopLogCatCollector(String backgroundPid, name) {
  // The pid might not be available if the build was terminated early or stopped due to
  // a build error.
  if (backgroundPid != null) {
    sh "kill ${backgroundPid}"
    // Zip file generation will fail if the file is already there
    // Pipeline Utility Steps Plugin 2.6.1 introduces 'overwrite' property
    // https://issues.jenkins.io/browse/JENKINS-42591
    sh "rm -f logcat-${name}.zip"
    zip([
      'zipFile': "logcat-${name}.zip",
      'archive': true,
      'glob' : "logcat-${name}.txt"
    ])
    sh "rm logcat-${name}.txt"
  }
}

def forwardAdbPorts() {
    sh """
        $ANDROID_SDK_ROOT/platform-tools/adb reverse tcp:9080 tcp:9080
        $ANDROID_SDK_ROOT/platform-tools/adb reverse tcp:9443 tcp:9443
        $ANDROID_SDK_ROOT/platform-tools/adb reverse tcp:8888 tcp:8888
        $ANDROID_SDK_ROOT/platform-tools/adb reverse tcp:9090 tcp:9090
    """
}

def testAndCollect(dir, task) {
    withEnv(['PATH+USER_BIN=/usr/local/bin']) {
        try {
            sh """
                pushd $dir
                ./gradlew $task --info --stacktrace --no-daemon
                popd
            """
        } finally {
            // See https://stackoverflow.com/a/51206394/1389357
            script {
                def testResults = findFiles(glob: "$dir/**/build/**/TEST-*.xml")
                for(xml in testResults) {
                    touch xml.getPath()
                }
            }
            step([$class: 'JUnitResultArchiver', allowEmptyResults: true, testResults: "$dir/**/build/**/TEST-*.xml"])
        }
    }
}

def runMonkey() {
    try {
        withEnv(['PATH+USER_BIN=/usr/local/bin']) {
            sh """
                cd examples/kmm-sample
                ./gradlew uninstallAll installDebug --stacktrace --no-daemon
                $ANDROID_SDK_ROOT/platform-tools/adb shell monkey -p  io.realm.example.kmmsample.androidApp -v 500 --kill-process-after-error
            """
        }
    } catch (err) {
        currentBuild.result = 'FAILURE'
        currentBuild.stageResult = 'FAILURE'
    }
}

def runBuildMinAndroidApp() {
    try {
        sh """
            cd examples/min-android-sample
            java -version
            ./gradlew assembleDebug --stacktrace --no-daemon
        """
    } catch (err) {
        currentBuild.result = 'FAILURE'
        currentBuild.stageResult = 'FAILURE'
    }
}

def notifySlackIfRequired(String slackMessage) {
    // We should only generate Slack notifications for important branches that all team members use.
    if (slackNotificationBranches.contains(currentBranch)) {
        node {
            withCredentials([[$class: 'StringBinding', credentialsId: 'slack-webhook-java-ci-channel', variable: 'SLACK_URL']]) {
                def payload = JsonOutput.toJson([
                    username: "Realm CI",
                    icon_emoji: ":realm_new:",
                    text: "${slackMessage}\n<${env.BUILD_URL}|Click here> to check the build."
                ])

                sh "curl -X POST --data-urlencode \'payload=${payload}\' ${env.SLACK_URL}"
            }
        }
    }
}

def readGitTag() {
    def command = 'git describe --exact-match --tags HEAD'
    def returnStatus = sh(returnStatus: true, script: command)
    if (returnStatus != 0) {
        return null
    }
    return sh(returnStdout: true, script: command).trim()
}

def startEmulatorInBgIfNeeded() {
    def command = '$ANDROID_SDK_ROOT/platform-tools/adb shell pidof com.android.phone'
    def returnStatus = sh(returnStatus: true, script: command)
    if (returnStatus != 0) {
        // Changing the name of the emulator image requires that this emulator image is
        // present on both atlanta_host13 and atlanta_host14.
        sh '/usr/local/Cellar/daemonize/1.7.8/sbin/daemonize  -E JENKINS_NODE_COOKIE=dontKillMe  $ANDROID_SDK_ROOT/emulator/emulator -avd Pixel_2_API_30_x86_64 -no-boot-anim -no-window -wipe-data -noaudio -partition-size 4098'
    }
}

boolean shouldPublishSnapshot(version) {
    if (!releaseBranches.contains(currentBranch)) {
        return false
    }
    if (version == null || !version.endsWith("-SNAPSHOT")) {
        return false
    }
    return true
}

def archiveServerLogs(String mongoDbRealmContainerId, String commandServerContainerId) {
    sh "docker logs ${commandServerContainerId} > ./command-server.log"
    sh 'rm command-server-log.zip || true'
    zip([
        'zipFile': 'command-server-log.zip',
        'archive': true,
        'glob': 'command-server.log'
    ])
    sh 'rm command-server.log'

    sh "docker cp ${mongoDbRealmContainerId}:/var/log/stitch.log ./stitch.log"
    sh 'rm stitchlog.zip || true'
    zip([
        'zipFile': 'stitchlog.zip',
        'archive': true,
        'glob': 'stitch.log'
    ])
    sh 'rm stitch.log'

    sh "docker cp ${mongoDbRealmContainerId}:/var/log/mongodb.log ./mongodb.log"
    sh 'rm mongodb.zip || true'
    zip([
        'zipFile': 'mongodb.zip',
        'archive': true,
        'glob': 'mongodb.log'
    ])
    sh 'rm mongodb.log'
}

def runCommand(String command){
  return sh(script: command, returnStdout: true).trim()
}

def shouldBuildJvmABIs() {
    if (publishBuild || shouldPublishSnapshot(version)) return true else return false
}

// TODO combine various cmake files into one https://github.com/realm/realm-kotlin/issues/482
def build_jvm_linux() {
    unstash name: 'swig_jni'
    docker.build('jvm_linux', '-f packages/cinterop/src/jvmMain/generic.Dockerfile .').inside {
        sh """
           cd packages/cinterop/src/jvmMain/
           rm -rf linux-build-dir
           mkdir linux-build-dir
           cd linux-build-dir
           cmake ../../jvm
           make -j8
        """

        stash includes:'packages/cinterop/src/jvmMain/linux-build-dir/librealmc.so', name: 'linux_so_file'
    }
}

def build_jvm_windows() {
  unstash name: 'swig_jni'
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
      bat "cd cinterop\\src\\jvmMain && rmdir /s /q windows-build-dir & mkdir windows-build-dir && cd windows-build-dir &&  \"${tool 'cmake'}\" ${cmakeDefinitions} ..\\..\\jvm && \"${tool 'cmake'}\" --build . --config Release"
  }
  stash includes: 'packages/cinterop/src/jvmMain/windows-build-dir/Release/realmc.dll', name: 'win_dll'
}
