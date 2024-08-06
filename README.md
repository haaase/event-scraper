A simple script to inform me about new events in various places. Notifications are sent to a signal group.

Register number:
podman run --rm --volume ./signal-cli-data:/var/lib/signal-cli:Z --entrypoint "/bin/signal-cli" -it signal-cli --config "/var/lib/signal-cli" link --name event-scraper

scala-cli package event_scraper.scala -o event-scraper -f --assembly
podman build --tag signal-cli .

## Packaging as Native Image
```shell
scala-cli package --native-image -f -o event-scraper event_scraper.scala -- --no-fallback --enable-url-protocols=http,https
```

## Receive Signal Messages
```shell
podman run --rm --user root -it -v ./signal-cli-data:/var/lib/signal-cli:Z registry.gitlab.com/packaging/signal-cli/signal-cli-native:latest -v --config /var/lib/signal-cli -a PHONENUM receive
```

## Start Daemon
```shell
podman run --rm --network=host --user root -it -v ./signal-cli-data:/var/lib/signal-cli:Z registry.gitlab.com/packaging/signal-cli/signal-cli-native:latest -v --config /var/lib/signal-cli -a PHONENUM daemon --http localhost:8094
```