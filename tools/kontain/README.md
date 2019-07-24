## Build

First build the kontain compatible Docker images for nodejs runtime by executing below command in `nodejs` folder

```bash
docker build -t kontain/whisk-node-12:latest  .
```

## Launch Standalone

```
$ ./gradlew :core:standalone:build
$ sudo java -Dwhisk.spi.ContainerFactoryProvider=org.apache.openwhisk.core.containerpool.kontain.KontainContainerFactoryProvider \
      -jar bin/openwhisk-standalone.jar \
      -m tools/kontain/kontain-runtimes.json
```