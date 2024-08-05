A simple script to inform me about new events in various places. Notifications are sent to a signal group.

Register number:
podman run --rm --volume ./signal-cli-data:/var/lib/signal-cli:Z --entrypoint "/bin/signal-cli" -it signal-cli --config "/var/lib/signal-cli" link --name event-scraper

scala-cli package event_scraper.scala -o event-scraper -f --assembly
podman build --tag signal-cli .