FROM ghcr.io/asamk/signal-cli:0.13.1
USER root
RUN apt-get update
RUN apt-get install qrencode bash
RUN ln -s /opt/signal-cli/bin/signal-cli /usr/bin/signal-cli
ADD event-scraper /usr/bin/event-scraper
ENTRYPOINT ["/usr/bin/event-scraper"]
