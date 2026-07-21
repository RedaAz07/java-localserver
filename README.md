# java-localserver

A custom HTTP server written in Java with support for:

- multiple virtual servers and ports
- static file serving
- routing by path prefix
- redirects
- custom error pages
- uploads and deletes
- CGI execution
- cookies and server-side sessions
- configurable request body limits

## Project Layout

- `src/` - Java source code
- `www/` - default static content
- `uploads/` - upload target used by the sample config
- `cgi/` - CGI scripts referenced by the sample config
- `error_pages/` - custom HTTP error pages
- `config.json` - server configuration
- `run.sh` - build and start script
- `tests/` - automated and manual test suites

## Requirements

- Java installed
- Bash shell
- `curl` for testing
- Python 3 if you want to run CGI-related tests

## Run

Start the server with:

```bash
./run.sh
```

The script removes the previous `out/` directory, compiles all Java sources into `out/`, and launches the server with:

```bash
java -cp out Main
```

By default, the sample configuration binds to:

- `127.0.0.1:8080`
- `127.0.0.1:8081`
- `127.0.0.1:8082`

## Configuration

The server reads `config.json` on startup. If you want to change how the server behaves, this is the file to edit first.

### What `config.json` controls

- `servers` - list of virtual servers to start
- `host` - IP address or host to bind to
- `ports` - one or more ports for the same virtual server
- `server_name` - name used by the configuration
- `client_body_limit` - maximum request body size for that server
- `error_pages` - custom error page paths for HTTP status codes
- `routes` - route rules for serving files, uploads, CGI, or redirects

### Route settings

Each route can define:

- `path` - URL prefix to match
- `root` - directory to serve from
- `default_file` - file returned for a directory request
- `methods` - allowed HTTP methods
- `directory_listing` - whether directory browsing is enabled
- `client_body_limit` - per-route upload/body limit
- `cgi_extensions` - file extensions treated as CGI scripts
- `redirect` - target path for redirect routes

### Sample behavior from the bundled config

- `/` serves files from `www/`
- `/upload` stores and removes files in `uploads/`
- `/cgi` executes matching CGI scripts from `cgi/`
- `/redirect` sends the client to another route

### Example

```json
{
  "servers": [
    {
      "host": "127.0.0.1",
      "ports": [8080],
      "server_name": "localhost",
      "client_body_limit": 5875875,
      "routes": [
        {
          "path": "/",
          "root": "www",
          "default_file": "index.html",
          "methods": ["GET", "DELETE", "POST"],
          "directory_listing": true
        }
      ]
    }
  ]
}
```

If you edit `config.json`, restart the server to apply the changes.

## Features

- `GET`, `POST`, and `DELETE` handling where enabled by route config
- longest-prefix route matching
- static file downloads with MIME type detection
- directory listing when enabled
- custom `400`, `403`, `404`, `405`, `413`, and `500` pages
- multipart upload handling
- cookie support through the `SESSID` cookie
- session tracking with idle expiration
- CGI polling and response handling

