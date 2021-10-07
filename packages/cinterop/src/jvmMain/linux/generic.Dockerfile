FROM centos:7

# Install EPEL & devtoolset
RUN yum install -y \
        epel-release \
        centos-release-scl-rh \
 && yum-config-manager --enable rhel-server-rhscl-7-rpms

RUN yum install -y \
        chrpath \
        devtoolset-9 \
        jq \
        libconfig-devel \
        openssh-clients \
        rh-git218 \
        zlib-devel \
        java-11-openjdk-devel \
        pcre-devel \
        automake \
        bison \
        byacc \
 && yum clean all

ENV PATH /opt/cmake/bin:/opt/rh/rh-git218/root/usr/bin:/opt/rh/devtoolset-9/root/usr/bin:$PATH
ENV LD_LIBRARY_PATH /opt/rh/devtoolset-9/root/usr/lib64:/opt/rh/devtoolset-9/root/usr/lib:/opt/rh/devtoolset-9/root/usr/lib64/dyninst:/opt/rh/devtoolset-9/root/usr/lib/dyninst:/opt/rh/devtoolset-9/root/usr/lib64:/opt/rh/devtoolset-9/root/usr/lib
ENV JAVA_HOME /usr/lib/jvm/java-11-openjdk/
ENV ANDROID_HOME /opt/android-sdk-linux

RUN mkdir -p /opt/cmake \
 && curl https://cmake.org/files/v3.18/cmake-3.18.2-Linux-x86_64.sh -o /cmake.sh \
 && sh /cmake.sh --prefix=/opt/cmake --skip-license \
 && rm /cmake.sh

RUN mkdir -p /etc/ssh && \
    echo "Host github.com\n\tStrictHostKeyChecking no\n" >> /etc/ssh/ssh_config && \
    ssh-keyscan github.com >> /etc/ssh/ssh_known_hosts

# install SWIG 4.0.2 (yum has an old version 2.0 incompatible)
RUN curl -L https://github.com/swig/swig/archive/refs/tags/v4.0.2.tar.gz -o swig.tar.gz \
 && tar xf  swig.tar.gz && cd swig-4.0.2 && ./autogen.sh && ./configure && make -j8 && make install

# install Android SDK & SDK to be able to compile the repo locally for SWIG JNI generation
# Install the Android SDK
# See https://developer.android.com/studio/index.html#downloads for latest version
RUN cd /opt && \
    wget -q https://dl.google.com/android/repository/commandlinetools-linux-7302050_latest.zip -O android-tools-linux.zip && \
    mkdir --parents ${ANDROID_HOME}/cmdline-tools/latest && \
    unzip android-tools-linux.zip -d ${ANDROID_HOME}/cmdline-tools/latest && \
    mv ${ANDROID_HOME}/cmdline-tools/latest/cmdline-tools/* ${ANDROID_HOME}/cmdline-tools/latest/ && \
    rm -f android-tools-linux.zip

# Grab what's needed in the SDK
RUN sdkmanager --update

# Accept licenses before installing components, no need to echo y for each component
# License is valid for all the standard components in versions installed from this file
# Non-standard components: MIPS system images, preview versions, GDK (Google Glass) and Android Google TV require separate licenses, not accepted there
RUN yes | sdkmanager --licenses

# SDKs
# The `yes` is for accepting all non-standard tool licenses.
# Please keep all sections in descending order!
RUN yes | sdkmanager \
    'build-tools;30.0.3' \
    'extras;android;m2repository' \
    'platform-tools' \
    'ndk;22.1.7171670'
