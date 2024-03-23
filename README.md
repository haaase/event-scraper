Register number:
podman run --rm --volume ./signal-cli-data:/var/lib/signal-cli:Z --entrypoint "/bin/signal-cli" -it signal-cli --config "/var/lib/signal-cli" link --name event-scraper
