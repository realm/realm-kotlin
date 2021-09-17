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
node_label = 'kotlin_machine_test' //'osx_kotlin'

// When having multiple executors available, Jenkins might append @2/@3/etc. to workspace folders in order
// to allow multiple parallel builds on the same branch. Unfortunately this breaks Ninja and thus us
// building native code. To work around this, we force the workspace to mirror the git path.
// This has two side-effects: 1) It isn't possible to use this JenkinsFile on a worker with multiple
// executors. At least not if we want to support building multipe versions of the same P
workspacePath = "/Users/realm/workspace-realm-kotlin/${currentBranch}"

pipeline {
    agent {
        node {
            label node_label
            customWorkspace workspacePath
        }
     }
    // The Gradle cache is re-used between stages, in order to avoid builds interleave,
    // and potentially corrupt each others cache, we grab a global lock for the entire
    // build.
    options {
        lock resource: 'kotlin_build_lock'
        timeout(time: 15, activity: true, unit: 'MINUTES')
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
        stage('SCM') {
            steps {
                runScm()
            }
        }
        stage('Build') {
            steps {
                runBuild()
            }
        }
//         stage('Static Analysis') {
//             when { expression { runTests } }
//             steps {
//                 runStaticAnalysis()
//             }
//         }
//         stage('Tests Compiler Plugin') {
//             when { expression { runTests } }
//             steps {
//                 runCompilerPluginTest()
//             }
//         }
//         stage('Tests Macos - Unit Tests') {
//             when { expression { runTests } }
//             steps {
//                 testAndCollect("packages", "macosTest")
//             }
//         }
        stage('Tests Macos - Integration Tests') {
            when { expression { runTests } }
            steps {
                testWithServer("test", "macosTest")
            }
        }
//         stage('Tests Android') {
//             when { expression { runTests } }
//             steps {
//                 testAndCollect("packages", "connectedAndroidTest")
//                 testAndCollect("test",     "connectedAndroidTest")
//             }
//         }
//         stage('Tests JVM (compiler only)') {
//             when { expression { runTests } }
//             steps {
//                 testAndCollect("test", 'jvmTest --tests "io.realm.test.compiler*"')
//             }
//         }
//         stage('Tests Android Sample App') {
//             when { expression { runTests } }
//             steps {
//                 catchError(buildResult: 'FAILURE', stageResult: 'FAILURE') {
//                     runMonkey()
//                 }
//             }
//         }
//         stage('Build Android on Java 8') {
//             when { expression { runTests } }
//             environment {
//                 JAVA_HOME="${JAVA_8}"
//             }
//             steps {
//                 runBuildAndroidApp()
//             }
//         }
//         stage('Publish SNAPSHOT to Maven Central') {
//             when { expression { shouldPublishSnapshot(version) } }
//             steps {
//                 runPublishSnapshotToMavenCentral()
//             }
//         }
//         stage('Publish Release to Maven Central') {
//             when { expression { publishBuild } }
//             steps {
//                 runPublishReleaseOnMavenCentral()
//             }
//         }
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
    checkout([
            $class           : 'GitSCM',
            branches         : scm.branches,
            gitTool          : 'native git',
            extensions       : scm.extensions + [
                    [$class: 'WipeWorkspace'],
                    [$class: 'CleanCheckout'],
                    [$class: 'SubmoduleOption', recursiveSubmodules: true]
            ],
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
}

def runBuild() {
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
                  chmod +x gradlew && ./gradlew clean assemble ${signingFlags} --info --stacktrace --no-daemon
               """
        }
    }
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
        zip([
                'zipFile': 'ktlint.zip',
                'archive': true,
                'dir'    : '/tmp/ktlint'
        ])
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

def  runPublishReleaseOnMavenCentral() {
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
            ./gradlew --no-daemon clean :plugin-compiler:test --info --stacktrace
        """
        step([ $class: 'JUnitResultArchiver', allowEmptyResults: true, testResults: "packages/plugin-compiler/build/**/TEST-*.xml"])
    }
}

def testWithServer(dir, task) {
    // Work-around for https://github.com/docker/docker-credential-helpers/issues/82
    withCredentials([
            [$class: 'StringBinding', credentialsId: 'realm-kotlin-ci-password', variable: 'PASSWORD'],
    ]) {
        sh "security -v unlock-keychain -p $PASSWORD"
    }

    // Build Docker image with MongoDB Realm Test Server infrastructure
    def props = readProperties file: 'dependencies.list'
    echo "Version in dependencies.list: ${props.MONGODB_REALM_SERVER}"
    def mdbRealmImage = docker.image("docker.pkg.github.com/realm/ci/mongodb-realm-test-server:${props.MONGODB_REALM_SERVER}")
    docker.withRegistry('https://docker.pkg.github.com', 'github-packages-token') {
      mdbRealmImage.pull()
    }
    def commandServerEnv = docker.build 'mongodb-realm-command-server', "tools/sync_test_server"

    // Prepare Docker containers used by Instrumentation tests
    // TODO: How much of this logic can be moved to start_server.sh for shared logic with local testing.
    def tempDir = runCommand('mktemp -d -t app_config.XXXXXXXXXX')
    sh "tools/sync_test_server/app_config_generator.sh ${tempDir} tools/sync_test_server/app_template testapp1 testapp2"
    sh "docker network create ${dockerNetworkId}"
    mongoDbRealmContainer = mdbRealmImage.run("--network ${dockerNetworkId} -v$tempDir:/apps")
    mongoDbRealmCommandServerContainer = commandServerEnv.run("--network container:${mongoDbRealmContainer.id} -v$tempDir:/apps")
    sh "timeout 60 sh -c \"while [[ ! -f $tempDir/testapp1/app_id || ! -f $tempDir/testapp2/app_id ]]; do echo 'Waiting for server to start'; sleep 1; done\""

    try {
        testAndCollect(dir, task)
    } finally {
        // We assume that creating these containers and the docker network can be considered an atomic operation.
        if (mongoDbRealmContainer != null && mongoDbRealmCommandServerContainer != null) {
            archiveServerLogs(mongoDbRealmContainer.id, mongoDbRealmCommandServerContainer.id)
            mongoDbRealmContainer.stop()
            mongoDbRealmCommandServerContainer.stop()
            sh "docker network rm ${dockerNetworkId}"
        }
    }
}

def testAndCollect(dir, task) {
    withEnv(['PATH+USER_BIN=/usr/local/bin']) {
        sh """
            pushd $dir
            ./gradlew $task --info --stacktrace --no-daemon
            popd
        """
        step([$class: 'JUnitResultArchiver', allowEmptyResults: true, testResults: "$dir/**/build/**/TEST-*.xml"])
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

def runBuildAndroidApp() {
    try {
        sh """
            cd examples/kmm-sample
            java -version
            ./gradlew :androidApp:clean :androidApp:assembleDebug --stacktrace --no-daemon
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
    zip([
        'zipFile': 'command-server-log.zip',
        'archive': true,
        'glob' : 'command-server.log'
    ])
    sh 'rm command-server.log'

    sh "docker cp ${mongoDbRealmContainerId}:/var/log/stitch.log ./stitch.log"
    zip([
        'zipFile': 'stitchlog.zip',
        'archive': true,
        'glob' : 'stitch.log'
    ])
    sh 'rm stitch.log'

    sh "docker cp ${mongoDbRealmContainerId}:/var/log/mongodb.log ./mongodb.log"
    zip([
        'zipFile': 'mongodb.zip',
        'archive': true,
        'glob' : 'mongodb.log'
    ])
    sh 'rm mongodb.log'
}

def runCommand(String command){
  return sh(script: command, returnStdout: true).trim()
}
