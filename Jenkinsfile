#!groovy
import groovy.json.JsonOutput

@Library('realm-ci') _

stage('SCM') {
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

        gitSha = sh(returnStdout: true, script: 'git rev-parse HEAD').trim().take(8)
        echo "Building: ${gitSha}"
        setBuildName(gitSha)

        stash includes: '**/*', name: 'source', excludes: './realm/realm-library/cpp_engine/external/realm-object-store/.dockerignore'
    }
}

stage('Static Analysis') {
    node('android') {
        getArchive()
        try {
            // Locking on the "android" lock to prevent concurrent usage of the gradle-cache
            // @see https://github.com/realm/realm-java/blob/00698d1/Jenkinsfile#L65
            lock("${env.NODE_NAME}-android") {
                sh 'chmod +x gradlew && ./gradlew ktlintCheck'
            }
        } finally {
            // CheckStyle Publisher plugin is deprecated and does not support multiple Checkstyle files
            // New Generation Warnings plugin throw a NullPointerException when used with recordIssues()
            // As a work-around we just stash the output of Ktlint for manual inspection.
            sh """
                rm -rf /tmp/ktlint
                mkdir /tmp/ktlint
                [ ! -d 'test/build/reports/ktlint' ] && rsync -a --delete --ignore-errors test/build/reports/ktlint/ /tmp/ktlint/test/ 
                [ ! -d 'packages/library/build/reports/ktlint' ] && rsync -a --delete --ignore-errors packages/library/build/reports/ktlint/ /tmp/ktlint/library/
                [ ! -d 'packages/plugin-compiler/build/reports/ktlint' ] && rsync -a --delete --ignore-errors packages/plugin-compiler/build/reports/ktlint/ /tmp/ktlint/plugin-compiler/
                [ ! -d 'packages/plugin-compiler-shaded/build/reports/ktlint' ] && rsync -a --delete --ignore-errors packages/plugin-compiler-shaded/build/reports/ktlint/ /tmp/ktlint/plugin-compiler-shaded/
                [ ! -d 'packages/plugin-gradle/build/reports/ktlint' ] && rsync -a --delete --ignore-errors packages/plugin-gradle/build/reports/ktlint/ /tmp/ktlint/plugin-gradle/
                [ ! -d 'packages/runtime-api/build/reports/ktlint' ] && rsync -a --delete --ignore-errors packages/runtime-api/build/reports/ktlint/ /tmp/ktlint/runtime-api/
            """
            zip([
                    'zipFile': 'ktlint.zip',
                    'archive': true,
                    'dir' : '/tmp/ktlint'
            ])
        }
    }
}
 
stage('build') {
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
            def image = buildDockerEnv('ci/realm-kotlin:android-build', extra_args: '-f Dockerfile.android')

            image.inside(
                // TODO Abstract common setup across executor methods (jvm, androidDevice, androidEmulator)
                "-e HOME=/tmp " +
                "-e _JAVA_OPTIONS=-Duser.home=/tmp " +
                "-e REALM_CORE_DOWNLOAD_DIR=/tmp/.gradle " +
                // Mounting ~/gradle-cache as ~/.gradle to prevent gradle from being redownloaded
                "-v ${HOME}/gradle-cache:/tmp/.gradle " +
                // Mounting ~/ccache as ~/.ccache to reuse the cache across builds
                "-v ${HOME}/ccache:/tmp/.ccache "
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
            def image = buildDockerEnv('ci/realm-kotlin:android-build', extra_args: '-f Dockerfile.android')

            // Locking on the "android" lock to prevent concurrent usage of the gradle-cache
            // @see https://github.com/realm/realm-java/blob/00698d1/Jenkinsfile#L65
            lock("${env.NODE_NAME}-android") {
                image.inside(
                    // TODO Abstract common setup across executor methods (jvm, androidDevice, androidEmulator)
                    "-e HOME=/tmp " +
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
}

def androidEmulator(workerFunction) {
    return {
        node('docker-cph-03') {
            getArchive()
            def image = buildDockerEnv('ci/realm-kotlin:android-build', extra_args: '-f Dockerfile.android')

            // Locking on the "android" lock to prevent concurrent usage of the gradle-cache
            // @see https://github.com/realm/realm-java/blob/00698d1/Jenkinsfile#L65
            sh "echo Waiting for log ${env.NODE_NAME}-android"
            lock("${env.NODE_NAME}-android") {
                sh "echo Executing with lock ${env.NODE_NAME}-android"
                image.inside(
                        // TODO Abstract common setup across executor methods (jvm, androidDevice, androidEmulator)
                        "-e HOME=/tmp " +
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
                        "--privileged " +
                        "-v /dev/kvm:/dev/kvm " +
                        "-e ANDROID_SERIAL=emulator-5554"
                ) {
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
}

def getArchive() {
    deleteDir()
    unstash 'source'
}
