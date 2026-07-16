# HTTP Server Testing Guide

## Overview

This document contains all the manual tests that should be performed to verify that the HTTP server is working correctly. Every feature required by the project should be tested before considering the server complete.

---

# Prerequisites

* Java installed.
* Your server compiled and running.
* Python installed (for CGI tests).
* JavaScript runtime installed (Node.js) if JavaScript CGI is supported.
* `curl` installed.
* `siege` installed (for stress testing).

Example:

```bash
java -jar server.jar
```

or

```bash
java Main
```

Assume the server is listening on:

```
http://localhost:8080
```

---

# 1. Basic Connection

Open your browser:

```
http://localhost:8080
```

Expected:

* HTTP 200
* Default page displayed

---

# 2. GET Request

Create:

```
www/index.html
```

Test:

```bash
curl http://localhost:8080/index.html
```

Expected:

* Status 200
* HTML returned

---

# 3. GET Image

Create:

```
www/images/logo.png
```

Test:

```bash
curl http://localhost:8080/images/logo.png --output logo.png
```

Expected:

* Status 200
* Downloaded image identical to original

---

# 4. GET CSS

```bash
curl http://localhost:8080/style.css
```

Expected:

```
Content-Type: text/css
```

---

# 5. GET JavaScript

```bash
curl http://localhost:8080/app.js
```

Expected:

```
Content-Type: application/javascript
```

---

# 6. GET Non-existing File

```bash
curl http://localhost:8080/unknown.html
```

Expected:

```
404 Not Found
```

Custom error page should be returned.

---

# 7. Forbidden Access

Try accessing a forbidden directory.

```bash
curl http://localhost:8080/private/
```

Expected:

```
403 Forbidden
```

---

# 8. Unsupported HTTP Method

```bash
curl -X PUT http://localhost:8080/index.html
```

Expected:

```
405 Method Not Allowed
```

---

# 9. POST Request

```bash
curl -X POST \
-d "username=Ahmed&password=1234" \
http://localhost:8080/login
```

Expected:

* HTTP 200
* Body correctly received

---

# 10. POST JSON

```bash
curl \
-X POST \
-H "Content-Type: application/json" \
-d '{"name":"Ahmed"}' \
http://localhost:8080/api
```

Expected:

Server receives JSON correctly.

---

# 11. DELETE Request

Create:

```
uploads/test.txt
```

Then:

```bash
curl -X DELETE http://localhost:8080/upload/test.txt
```

Expected:

* File deleted
* HTTP 200

---

# 12. DELETE Missing File

```bash
curl -X DELETE http://localhost:8080/upload/notfound.txt
```

Expected:

```
404
```

---

# 13. File Upload

Create:

```
hello.txt
```

Test:

```bash
curl \
-F "file=@hello.txt" \
http://localhost:8080/upload
```

Expected:

* File stored inside upload folder
* HTTP 200

---

# 14. Upload Multiple Files

```bash
curl \
-F "file1=@image.png" \
-F "file2=@hello.txt" \
http://localhost:8080/upload
```

Expected:

Both files uploaded.

---

# 15. Upload Large File

Generate a 100 MB file on Linux:

```bash
dd if=/dev/zero of=large_file.bin bs=1M count=100
```

Or a 1 GB file:

```bash
dd if=/dev/zero of=huge_file.bin bs=1M count=1024
```

Upload:

```bash
curl \
-F "file=@large_file.bin" \
http://localhost:8080/upload
```

Expected:

If file exceeds configured limit:

```
413 Payload Too Large
```

Otherwise:

```
200 OK
```

---

# 16. Cookies

```bash
curl -i http://localhost:8080
```

Expected header:

```
Set-Cookie:
```

---

# 17. Sending Cookies

```bash
curl \
-H "Cookie: SESSIONID=12345" \
http://localhost:8080
```

Expected:

Server receives cookie.

---

# 18. Sessions

Open twice using:

```bash
curl -c cookies.txt http://localhost:8080
```

Then

```bash
curl -b cookies.txt http://localhost:8080
```

Expected:

Same session reused.

---

# 19. Redirection

```bash
curl -L http://localhost:8080/home
```

Expected:

Redirect to configured page.

---

# 20. Directory Listing Enabled

```bash
curl http://localhost:8080/images/
```

Expected:

HTML listing files.

---

# 21. Directory Listing Disabled

Expected:

```
403
```

or

```
404
```

depending on configuration.

---

# 22. Default Index Page

Create:

```
folder/index.html
```

Test:

```bash
curl http://localhost:8080/folder/
```

Expected:

Returns:

```
index.html
```

---

# 23. Timeout

Open a connection without sending the full request.

Example using netcat:

```bash
nc localhost 8080
```

Type:

```
GET /
```

Do not finish the request.

Expected:

Connection closes automatically after timeout.

---

# 24. Chunked Transfer Encoding

```bash
curl \
-H "Transfer-Encoding: chunked" \
-T hello.txt \
http://localhost:8080/upload
```

Expected:

Server reconstructs request correctly.

---

# 25. Invalid HTTP Request

```bash
printf "HELLO\r\n\r\n" | nc localhost 8080
```

Expected:

```
400 Bad Request
```

---

# 26. Malformed Headers

```bash
printf "GET / HTTP/1.1\r\nHost\r\n\r\n" | nc localhost 8080
```

Expected:

```
400
```

---

# 27. Multiple Ports

Run server on:

```
8080

8081
```

Test:

```bash
curl http://localhost:8081
```

Expected:

HTTP 200.

---

# 28. Multiple Requests

```bash
for i in {1..100}
do
curl http://localhost:8080/index.html
done
```

Expected:

All successful.

---

# 29. CGI Python

Suppose:

```
cgi-bin/test.py
```

Example:

```python
print("Content-Type: text/html")
print()
print("<h1>Hello Python CGI</h1>")
```

Test:

```bash
curl http://localhost:8080/cgi-bin/test.py
```

Expected:

```
<h1>Hello Python CGI</h1>
```

Also test passing parameters:

```bash
curl "http://localhost:8080/cgi-bin/test.py?name=Ahmed"
```

Expected:

Python receives query parameters correctly.

---

# 30. CGI JavaScript (Node.js)

Example:

```
cgi-bin/test.js
```

```javascript
console.log("Content-Type: text/html");
console.log();
console.log("<h1>Hello JavaScript CGI</h1>");
```

Run:

```bash
curl http://localhost:8080/cgi-bin/test.js
```

Expected:

```
<h1>Hello JavaScript CGI</h1>
```

---

# 31. CGI Invalid Script

```bash
curl http://localhost:8080/cgi-bin/does_not_exist.py
```

Expected:

```
404
```

---

# 32. CGI Runtime Error

Python:

```python
raise Exception("Crash")
```

Test:

```bash
curl http://localhost:8080/cgi-bin/error.py
```

Expected:

```
500 Internal Server Error
```

Server must continue running.

---

# 33. CGI Relative Path

Verify that:

```
PATH_INFO
```

contains the requested path and relative paths are handled correctly.

---

# 34. Configuration Test

Modify configuration:

* Change port
* Change root
* Change upload folder
* Change error pages

Restart server.

Expected:

Changes applied without modifying Java code.

---

# 35. Custom Error Pages

Trigger:

* 400
* 403
* 404
* 405
* 413
* 500

Verify custom HTML pages are returned.

---

# 36. Memory Leak Test

Run:

```bash
siege -b -t60S http://localhost:8080
```

or

```bash
siege -c100 -t2M http://localhost:8080
```

Expected:

* No crash
* Stable memory usage
* Stable CPU usage
* Server still responds afterward

---

# 37. Stress Test

```bash
siege -b http://localhost:8080
```

Expected:

Availability close to:

```
99.5%
```

or higher.

---

# 38. Browser Tests

Verify using a browser:

* HTML pages
* CSS loading
* JavaScript loading
* Images
* Redirects
* File downloads
* Upload forms
* Cookies
* Sessions

---

# 39. Security Tests

Try requesting:

```
../../etc/passwd
```

Expected:

Access denied.

Try:

```
../../../secret.txt
```

Expected:

403 or 404.

Directory traversal must never work.

---

# 40. Server Stability

Run all previous tests multiple times.

Expected:

* No crashes
* No resource leaks
* No hanging connections
* All sockets closed properly
* Correct HTTP status codes
* Server remains responsive
