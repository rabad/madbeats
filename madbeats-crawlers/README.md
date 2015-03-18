# madbeats backend

## Build

Before compiling copy `src/main/resources/applications-sample.json` to `src/main/resources/applications.json` and fill all
the credentials. Modify the number of `shards` and `replicas` and the `index` to be created.

After `application.json` has been created, run `sbt assembly pack`

## Run

To run the crawling process:

```
cd target/pack/bin
nohup ./pack >madbeats.log 2>&1 &
```
