# Trick Maven into downloading our dependencies before we copy over our "src"
# directory.  This way, if we later change a file in "src", we won't always have
# to re-download our dependencies.
FROM maven:3.6.0-jdk-11 AS download_dependencies
WORKDIR /tfbstatus
RUN mkdir -p src/main/java/fake
RUN mkdir -p src/test/java/fake
RUN echo "package fake; public class Main { public static void main(String[] args) {} }"         >> src/main/java/fake/Main.java
RUN echo "package fake; public class Test { @org.junit.jupiter.api.Test public void test() {} }" >> src/test/java/fake/Test.java
COPY pom.xml pom.xml
RUN mvn package --batch-mode

FROM maven:3.6.0-jdk-11 AS build
WORKDIR /tfbstatus
COPY --from=download_dependencies /root/.m2 /root/.m2
COPY pom.xml pom.xml
COPY src src

# If we did everything right, this won't download any new dependencies.
RUN mvn package --batch-mode
# TODO: Consider -DskipTests for faster startup.
# TODO: Consider "mvn compile; java path/to/main.class" for even faster startup.

FROM openjdk:11-jdk
WORKDIR /tfbstatus
COPY --from=build /tfbstatus/target/tfb-status.jar tfb-status.jar
CMD [ "java", "-jar", "tfb-status.jar" ]
