#!groovy
import groovy.json.JsonOutput

@Library('realm-ci') _

// Branches from which we release SNAPSHOT's. Only release branches need to run on actual hardware.
releaseBranches = ['master', 'next-major']
// Branches that are "important", so if they do not compile they will generate a Slack notification
slackNotificationBranches = [ 'master', 'releases', 'next-major' ]
// Shortcut to current branch name that is being tested
currentBranch = env.CHANGE_BRANCH
// Will be set to `true` if this build is a full release that should be available on Bintray.
// This is determined by comparing the current git tag to the version number of the build.
publishBuild = false
// Version of Realm Kotlin being tested. This values is defined in `<root>/version.txt`.
version = null

pipeline {
    agent none
    stages {
        stage('SCM') { 
            steps {
                runScm()
            }
        }
        stage('Static Analysis') { 
            steps {
                runStaticAnalysis() 
            }
        }
        stage('Build') { 
            steps {
                runBuild() 
            }
        }
        stage('Publish to OJO') {
            when { expression { shouldReleaseSnapshot(version) } }
            steps {
                runPublishToOjo()
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
    node('docker') {
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
        echo "Git tag: ${gitTag ?: 'none'}"
        if (!gitTag) {
            gitSha = sh(returnStdout: true, script: 'git rev-parse HEAD').trim().take(8)
            echo "Building commit: ${gitSha}"
            setBuildName(gitSha)
            publishBuild = false
        } else {
            version = readFile('version.txt').trim()
            if (gitTag != "v${version}") {
                error "Git tag '${gitTag}' does not match v${version}"
            } else {
                echo "Building release: '${gitTag}'"
                setBuildName("Tag ${gitTag}")
                publishBuild = true
            }
        }

        stash includes: '**/*', name: 'source', excludes: './realm/realm-library/cpp_engine/external/realm-object-store/.dockerignore'
    }
}

def runStaticAnalysis() {
    node('android') {
        getArchive()
        try {
            // Locking on the "android" lock to prevent concurrent usage of the gradle-cache
            // @see https://github.com/realm/realm-java/blob/00698d1/Jenkinsfile#L65
            lock("${env.NODE_NAME}-android") {
                sh 'chmod +x gradlew && ./gradlew ktlintCheck'
                sh 'chmod +x gradlew && ./gradlew detekt'
            }
        } finally {
            // CheckStyle Publisher plugin is deprecated and does not support multiple Checkstyle files
            // New Generation Warnings plugin throw a NullPointerException when used with recordIssues()
            // As a work-around we just stash the output of Ktlint and Detekt for manual inspection.
            sh '''
                rm -rf /tmp/ktlint 
                rm -rf /tmp/detekt 
                mkdir /tmp/ktlint
                mkdir /tmp/detekt
                rsync -a --delete --ignore-errors example/app/build/reports/ktlint/ /tmp/ktlint/example/ || true 
                rsync -a --delete --ignore-errors test/build/reports/ktlint/ /tmp/ktlint/test/ || true 
                rsync -a --delete --ignore-errors packages/library/build/reports/ktlint/ /tmp/ktlint/library/ || true
                rsync -a --delete --ignore-errors packages/plugin-compiler/build/reports/ktlint/ /tmp/ktlint/plugin-compiler/ || true
                rsync -a --delete --ignore-errors packages/plugin-gradle/build/reports/ktlint/ /tmp/ktlint/plugin-gradle/ || true
                rsync -a --delete --ignore-errors packages/runtime-api/build/reports/ktlint/ /tmp/ktlint/runtime-api/ || true
                rsync -a --delete --ignore-errors example/app/build/reports/detekt/ /tmp/detekt/example/ || true 
                rsync -a --delete --ignore-errors test/build/reports/detekt/ /tmp/detekt/test/ || true 
                rsync -a --delete --ignore-errors packages/library/build/reports/detekt/ /tmp/detekt/library/ || true
                rsync -a --delete --ignore-errors packages/plugin-compiler/build/reports/detekt/ /tmp/detekt/plugin-compiler/ || true
                rsync -a --delete --ignore-errors packages/plugin-gradle/build/reports/detekt/ /tmp/detekt/plugin-gradle/ || true
                rsync -a --delete --ignore-errors packages/runtime-api/build/reports/detekt/ /tmp/detekt/runtime-api/ || true
            '''
            zip([
                    'zipFile': 'ktlint.zip',
                    'archive': true,
                    'dir' : '/tmp/ktlint'
            ])
            zip([
                    'zipFile': 'detekt.zip',
                    'archive': true,
                    'dir' : '/tmp/detekt'
            ])
        }
    }
}
 
def runBuild() {
    parralelExecutors = [:]
    parralelExecutors['compiler']  = jvm             {
        sh """
            cd packages
            ./gradlew clean :plugin-compiler:test --info --stacktrace
        """
        step([ $class: 'JUnitResultArchiver', allowEmptyResults: true, testResults: "packages/plugin-compiler/build/**/TEST-*.xml"])
    }
    parralelExecutors['jvm']       = jvm             { test("jvmTest") }
    parralelExecutors['android']   = androidEmulator { test("connectedAndroidTest") }
    parralelExecutors['macos']   = macos           { test("macosTest") }
    parallel parralelExecutors
}

def runPublishToOjo() {
    node('docker-cph-03') {
        androidDockerBuild({
            withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: 'bintray', passwordVariable: 'BINTRAY_KEY', usernameVariable: 'BINTRAY_USER']]) {
                sh "chmod +x gradlew && ./gradlew -PbintrayUser=${env.BINTRAY_USER} -PbintrayKey=${env.BINTRAY_KEY} ojoUpload --stacktrace"
            }
        })
    }
}

def test(task) {
    sh """
        cd test
        ./gradlew clean $task --info --stacktrace
    """
    step([ $class: 'JUnitResultArchiver', allowEmptyResults: true, testResults: "test/build/**/TEST-*.xml"])
}

def macos(workerFunction) {
    return {
        node('osx') {
            getArchive()
            workerFunction()
        }
    }
}

def jvm(workerFunction) {
    return {
        // FIXME Could just use a docker node, but do not have overview on all the caching
        //  considerations now, so just reusing an Android machine with gradle caching etc.
        node('android') {
            getArchive()
            // TODO Consider adding a specific jvm docker image instead. For now just reuse Android
            //  one as it fulfills the toolchain requirement
            androidDockerBuild {
                workerFunction()
            }
        }
    }
}

def androidDockerBuild(workerFunction) {
    return {
        def image = buildDockerEnv('ci/realm-kotlin:android-build', extra_args: '-f Dockerfile.android')
        // Locking on the "android" lock to prevent concurrent usage of the gradle-cache
        // @see https://github.com/realm/realm-java/blob/00698d1/Jenkinsfile#L65
        sh "echo Waiting for log ${env.NODE_NAME}-android"
        lock("${env.NODE_NAME}-android") {
            image.inside("-e HOME=/tmp " +
                            "-e _JAVA_OPTIONS=-Duser.home=/tmp " +
                            "-e REALM_CORE_DOWNLOAD_DIR=/tmp/.gradle " +
                            // Mounting ~/.android/adbkey(.pub) to reuse the adb keys
                            "-v ${HOME}/.android:/tmp/.android " +
                            // Mounting ~/gradle-cache as ~/.gradle to prevent gradle from being redownloaded
                            "-v ${HOME}/gradle-cache:/tmp/.gradle " +
                            // Mounting ~/ccache as ~/.ccache to reuse the cache across builds
                            "-v ${HOME}/ccache:/tmp/.ccache " +
                            // Mounting /dev/bus/usb with --privileged to allow connecting to the device via USB
                            "-v /dev/bus/usb:/dev/bus/usb " +
                            "--privileged"
            ) {
                workerFunction()
            }
        }
    }
}

def androidDevice(workerFunction) {
    return {
        node('android') {
            getArchive()
            androidDockerBuild {
                workerFunction()
            }
        }
    }
}

def androidEmulator(workerFunction) {
    return {
        node('docker-cph-03') {
            getArchive()
            androidDockerBuild {
                sh """
                        yes '\n' | avdmanager create avd -n CIEmulator -k 'system-images;android-29;default;x86_64' --force
                        # https://stackoverflow.com/questions/56198290/problems-with-adb-exe
                        adb start-server
                        # Need to go to ANDROID_HOME due to https://askubuntu.com/questions/1005944/emulator-avd-does-not-launch-the-virtual-device
                        cd \$ANDROID_HOME/tools && emulator -avd CIEmulator -no-boot-anim -no-window -wipe-data -noaudio -partition-size 4098 &
                    """
                try {
                    workerFunction()
                } finally {
                    sh "adb emu kill"
                }
            }
        }
    }
}

def getArchive() {
    deleteDir()
    unstash 'source'
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

boolean shouldReleaseSnapshot(version) {
    if (!releaseBranches.contains(currentBranch)) {
        return false
    }
    if (version == null || !version.endsWith("-SNAPSHOT")) {
        return false
    }
    return true
}
