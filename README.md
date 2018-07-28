This is a web application that consumes results from the [TechEmpower Framework
Benchmarks](https://github.com/TechEmpower/FrameworkBenchmarks) (TFB).  TFB may
be configured to upload its results files to some other server between tests and
after a full run, and this application this is meant to be that other server.

To build:

```
mvn clean package
```

To run:

```
java -jar target/tfb-status.jar path/to/config.yml
```

To create your own YAML config file, start by copying [an existing config
file](config/mhixson.yml) and then modify whatever settings you want.  The
structure of the config file is defined by the
[ApplicationConfig](src/main/java/tfb/status/config/ApplicationConfig.java)
class.
