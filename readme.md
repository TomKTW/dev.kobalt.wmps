Wayback Machine Proxy Server
===========

This program provides a proxy server to browse the old web through Internet Archive's Wayback Machine.

## Requirements

- Java 8 Runtime

- Network access to Wayback Machine server

## Usage

Launch executable with following command:

```java -jar waybackmachineproxy.jar --httpServerPort "%PORT%" --httpServerHost "%HOST%" --timestamp "%TIMESTAMP%"```

Replace following values:

%PORT% - Port to host the server at.

%HOST% - Host value (127.0.0.1 for private, 0.0.0.0 for public access)

%TIMESTAMP% - Timestamp of the timeline where you want to browse pages at (format: YYYYMMDDHHMMSS)

## Mechanism

Define the timestamp to define the nearest timeline of browsing the web. HTTP proxy server will start and when you
configure a browser or system to run through it, all requests will be passed to Internet Archive's Wayback Machine
service to get archived versions of pages.

All requests that receive a response will be cached. Cache is bound to requested URL and timestamp. To remove the cache,
stop the server, remove "cache" folder and start the server again.

## Limitations

- Few simultaneous requests can be active at a time.

- Some requests may fail and require another attempt.

- This isn't well optimized and it may cause high memory usage.

- Cache has no storage limit and it may fill your storage if browsing a lot.

## License

This program is licensed under AGPL-3.0.