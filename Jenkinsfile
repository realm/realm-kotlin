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
                        [$class: 'CleanCheckout'],
                        [$class: 'SubmoduleOption', recursiveSubmodules: true]
                ],
                userRemoteConfigs: scm.userRemoteConfigs
        ])

        gitSha = sh(returnStdout: true, script: 'git rev-parse HEAD').trim().take(8)
        echo "Building: ${gitSha}"
        setBuildName(gitSha)

        stash includes: '**', name: 'source', useDefaultExcludes: false
    }
}

stage('build') {
    parralelExecutors = [:]
    parralelExecutors['android'] = android {
        sh """
            cd test && ./gradlew jvmTest'
        """
    }
    parralelExecutors['macos'] = macos {
        sh """
            cd test && ./gradlew macosTest'
        """
    }
    parallel parralelExecutors
}

def macos(workerFunction) {
    return {
        node('osx') {
            unstash 'source'
            sh "bash ./scripts/utils.sh set-version ${dependencies.VERSION}"
            workerFunction('macos')
        }
    }
}

def android(workerFunction) {
    return {
        node('android') {
            unstash 'source'
            def image
            image = buildDockerEnv("ci/realm-kotlin:android-build",)

            // Locking on the "android" lock to prevent concurrent usage of the gradle-cache
            // @see https://github.com/realm/realm-java/blob/00698d1/Jenkinsfile#L65
            lock("${env.NODE_NAME}-android") {
                image.inside(
                        // Mounting ~/.android/adbkey(.pub) to reuse the adb keys
                        "-v ${HOME}/.android/adbkey:/home/jenkins/.android/adbkey:ro -v ${HOME}/.android/adbkey.pub:/home/jenkins/.android/adbkey.pub:ro " +
                                // Mounting ~/gradle-cache as ~/.gradle to prevent gradle from being redownloaded
                                "-v ${HOME}/gradle-cache:/home/jenkins/.gradle " +
                                // Mounting ~/ccache as ~/.ccache to reuse the cache across builds
                                "-v ${HOME}/ccache:/home/jenkins/.ccache " +
                                // Mounting /dev/bus/usb with --privileged to allow connecting to the device via USB
                                "-v /dev/bus/usb:/dev/bus/usb --privileged"
                ) {
                    workerFunction()
                }
            }
        }
    }
}
