FROM debian:bullseye-slim AS base_build_image
RUN apt-get update && \
    apt-get install -y curl && \
    rm -rf /var/lib/apt/lists/*

RUN curl -o /tmp/jdk.tgz https://download.java.net/java/GA/jdk20/bdc68b4b9cbc4ebcb30745c85038d91d/36/GPL/openjdk-20_linux-x64_bin.tar.gz
RUN echo -n 'bb863b2d542976d1ae4b7b81af3e78b1e4247a64644350b552d298d8dc5980dc /tmp/jdk.tgz' | sha256sum -c
RUN tar -xvf /tmp/jdk.tgz -C /opt
RUN rm /tmp/jdk.tgz
ENV JAVA_HOME /opt/jdk-20
ENV PATH "${JAVA_HOME}/bin:${PATH}"

RUN curl -o /tmp/maven.tgz https://archive.apache.org/dist/maven/maven-3/3.9.1/binaries/apache-maven-3.9.1-bin.tar.gz
RUN echo -n 'd3be5956712d1c2cf7a6e4c3a2db1841aa971c6097c7a67f59493a5873ccf8c8b889cf988e4e9801390a2b1ae5a0669de07673acb090a083232dbd3faf82f3e3 /tmp/maven.tgz' | sha512sum -c
RUN tar -xvf /tmp/maven.tgz -C /opt
RUN rm /tmp/maven.tgz
ENV MAVEN_HOME /opt/apache-maven-3.9.1
ENV PATH "${MAVEN_HOME}/bin:${PATH}"

# Produce a small Java runtime that contains only what we need.
# ------------------------------------------------------------------------------
# Module             Class from module                       Used by
# ------------------------------------------------------------------------------
# java.datatransfer  java.awt.datatransfer.Transferrable     jakarta.activation
# java.logging       java.util.logging.Logger                hk2
# java.management    java.lang.management.ManagementFactory  maven-surefire-plugin, tfb.status.service.HealthChecker
# java.naming        javax.naming.NamingException            logback
# java.net.http      java.net.http.HttpClient                tfb.status.testlib.HttpTester
# java.xml           org.xml.sax.InputSource                 logback
# jdk.crypto.ec      sun.security.ec.SunEC                   jakarta.mail (for STARTTLS)
# jdk.jdwp.agent     (native code)                           java -agentlib:jdwp
# jdk.unsupported    sun.misc.Unsafe                         jboss-threads
# jdk.zipfs          jdk.nio.zipfs.ZipFileSystemProvider     tfb.status.util.ZipFiles
# ------------------------------------------------------------------------------
FROM base_build_image AS build_jre
RUN jlink --add-modules java.datatransfer,java.logging,java.management,java.naming,java.net.http,java.xml,jdk.crypto.ec,jdk.jdwp.agent,jdk.unsupported,jdk.zipfs \
          --output /opt/jre

FROM base_build_image AS build_app
WORKDIR /tfbstatus
COPY --from=build_jre /opt/jre /opt/jre
COPY .git .git
COPY pom.xml pom.xml
COPY src src
ARG SKIP_TESTS=false
RUN --mount=type=cache,target=/root/.m2/repository \
    mvn package --batch-mode \
                -Djvm=/opt/jre/bin/java \
                -DskipTests="${SKIP_TESTS}"
# To debug surefire VM crashes, append this to the previous line:
# || (cat target/surefire-reports/* && exit 1)

FROM debian:bullseye-slim AS run_app
WORKDIR /tfbstatus
COPY --from=build_jre /opt/jre /opt/jre
ENV JAVA_HOME "/opt/jre"
ENV PATH "${JAVA_HOME}/bin:${PATH}"
COPY --from=build_app /tfbstatus/target/lib lib
COPY --from=build_app /tfbstatus/target/tfb-status.jar tfb-status.jar

ENTRYPOINT [ "java", "-jar", "tfb-status.jar" ]
