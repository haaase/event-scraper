FROM debian
COPY event-scraper /usr/bin/event-scraper
WORKDIR /eventscraper
ENTRYPOINT ["/usr/bin/event-scraper"]
