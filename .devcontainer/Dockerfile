# FROM ibm-semeru-runtimes:open-11.0.22_7-jdk-jammy
FROM ibm-semeru-runtimes:open-17.0.13_11-jdk-jammy


ENV DEBIAN_FRONTEND=noninteractive
ENV PATH=$PATH:$GRADLE_HOME/bin
# ENV JAVA_TOOL_OPTIONS=-Dcom.ibm.jsse2.sp800-131=strict

# WORKDIR /source

# COPY . /source

RUN mkdir -p /workspace
WORKDIR /workspace


RUN apt update && apt install -y build-essential openssl x3270 c3270 vim wget unzip git pip

RUN pip install --upgrade "git+https://github.com/ibm/detect-secrets.git@master#egg=detect-secrets"



# Maven 3.8.8
RUN wget https://downloads.apache.org/maven/maven-3/3.8.8/binaries/apache-maven-3.8.8-bin.tar.gz \
    && tar -xzf apache-maven-3.8.8-bin.tar.gz -C /opt \
    && ln -s /opt/apache-maven-3.8.8 /opt/maven \
    && ln -s /opt/maven/bin/mvn /usr/bin/mvn \
    && rm apache-maven-3.8.8-bin.tar.gz

# Maven environment vars
ENV MAVEN_HOME=/opt/maven
ENV PATH=$MAVEN_HOME/bin:$PATH

#  Gradle 8.9
RUN wget https://services.gradle.org/distributions/gradle-8.9-bin.zip \
    && unzip gradle-8.9-bin.zip -d /opt \
    && ln -s /opt/gradle-8.9 /opt/gradle \
    && ln -s /opt/gradle/bin/gradle /usr/bin/gradle \
    && rm gradle-8.9-bin.zip

# Gradle environment vars
ENV GRADLE_HOME=/opt/gradle
ENV PATH=$GRADLE_HOME/bin:$PATH

# Install Go 
RUN wget https://go.dev/dl/go1.23.3.linux-arm64.tar.gz \
    && tar -xzf go1.23.3.linux-arm64.tar.gz -C /usr/local \
    && rm go1.23.3.linux-arm64.tar.gz

ENV PATH="/usr/local/go/bin:$PATH"

RUN mvn --version && gradle --version

# gpg --quick-generate-key "John Doe <john.doe@example.com>" rsa4096 sign 1y
# GPG_PASSPHRASE=shit ./build-locally.sh 
