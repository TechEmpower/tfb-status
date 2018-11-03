This is a web application that consumes results from the [TechEmpower Framework
Benchmarks](https://github.com/TechEmpower/FrameworkBenchmarks) (TFB).  TFB may
be configured to upload its results files to some other server between tests and
after a full run, and this application this is meant to be that other server.

## Running with Docker

To build and run with [Docker Compose](https://docs.docker.com/compose/):

```
docker-compose up --build
```

## Running without Docker

To build:

```
mvn clean package
```

To run with a default configuration:

```
java -jar target/tfb-status.jar
```

To run with a custom configuration:

```
java -jar target/tfb-status.jar path/to/config.yml
```

To create your own YAML config file, start by copying [the example config
file](example-config.yml) and then modify whatever settings you want.  The
structure of this config file is defined by the
[ApplicationConfig](src/main/java/tfb/status/config/ApplicationConfig.java)
class.  Do not add your config file to source control.
