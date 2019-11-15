# Trick Maven into downloading our dependencies before we copy over our "src"
# directory.  This way, if we later change a file in "src", we won't always have
# to re-download our dependencies.
FROM maven:3.6.2-jdk-13 AS download_dependencies
WORKDIR /tfbstatus
RUN mkdir -p src/main/java/fake
RUN mkdir -p src/test/java/fake
RUN echo "package fake; public class Main { public static void main(String[] args) {} }"         >> src/main/java/fake/Main.java
RUN echo "package fake; public class Test { @org.junit.jupiter.api.Test public void test() {} }" >> src/test/java/fake/Test.java
COPY pom.xml pom.xml
RUN mvn package --batch-mode

FROM maven:3.6.2-jdk-13 AS build_app
WORKDIR /tfbstatus
COPY --from=download_dependencies /root/.m2 /root/.m2

# The .git directory is used by git-commit-id-plugin for Maven.  It collects
# useful information about the local Git repository that our application
# displays on its /about page.
COPY .git .git

COPY pom.xml pom.xml
COPY src src

ARG SKIP_TESTS=false

# If we did everything right, this won't download any new dependencies.
RUN mvn package --batch-mode --offline -DskipTests="${SKIP_TESTS}"

# Produce a slimmed-down version of the Java runtime that contains only what we
# need.
FROM openjdk:13-alpine AS build_runtime
WORKDIR /tfbstatus
# ------------------------------------------------------------------------------
# Module           Class from module                    Used by
# ------------------------------------------------------------------------------
# java.base        *                                    *
# java.logging     java.util.logging.Logger             org.glassfish.hk2.utilities.reflection.Logger
# java.naming      javax.naming.NamingException         ch.qos.logback.classic.joran.JoranConfigurator
# java.xml         org.xml.sax.InputSource              ch.qos.logback.core.joran.GenericConfigurator
# jdk.unsupported  sun.misc.Unsafe                      com.github.benmanes.caffeine.base.UnsafeAccess
# jdk.zipfs        jdk.nio.zipfs.ZipFileSystemProvider  tfb.status.util.ZipFiles (implicit)
# ------------------------------------------------------------------------------
RUN jlink --add-modules java.base,java.logging,java.naming,java.xml,jdk.unsupported,jdk.zipfs --output runtime

FROM alpine
WORKDIR /tfbstatus
COPY --from=build_runtime /tfbstatus/runtime runtime
ENV PATH "/tfbstatus/runtime/bin:${PATH}"
COPY --from=build_app /tfbstatus/target/lib lib
COPY --from=build_app /tfbstatus/target/tfb-status.jar tfb-status.jar

ENTRYPOINT [ "java", "-jar", "tfb-status.jar" ]
