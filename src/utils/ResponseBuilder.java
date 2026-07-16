package utils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;

public class ResponseBuilder {

    public static HttpResponse build(HttpRequest request, RouteConfig route) {
        HttpResponse response = new HttpResponse();

        if (route == null) {
            return buildErrorResponse(404, "Not Found", null);
        }

        if (route.getMethods() != null && !route.getMethods().contains(request.getMethod())) {
            return buildErrorResponse(405, "Method Not Allowed", route.getErrorPages());
        }

        // Handle redirect
        if (route.getRedirect() != null) {
            response.setStatusCode(301, "Moved Permanently");
            response.setHeader("Location", route.getRedirect());
            response.setBody(new byte[0]);
            return response;
        }

        String relativePath = request.getPath().substring(route.getPath().length());
        if (!relativePath.startsWith("/")) {
            relativePath = "/" + relativePath;
        }

        // Normalize to prevent directory traversal
        File targetFile = new File(route.getRoot() + relativePath);

        // Resolve canonical path to prevent traversal attacks
        try {
            File rootDir = new File(route.getRoot()).getCanonicalFile();
            File resolved = targetFile.getCanonicalFile();
            if (!resolved.getPath().startsWith(rootDir.getPath())) {
                return buildErrorResponse(403, "Forbidden", route.getErrorPages());
            }
            targetFile = resolved;
        } catch (IOException e) {
            return buildErrorResponse(500, "Internal Server Error", route.getErrorPages());
        }

        // Handle POST (file upload)
        if ("POST".equals(request.getMethod())) {
            return handlePost(request, route, targetFile);
        }

        // Handle DELETE
        if ("DELETE".equals(request.getMethod())) {
            return handleDelete(route, targetFile);
        }

        // Handle GET (serve files / directory listing)
        return handleGet(route, targetFile, relativePath);
    }

    private static HttpResponse handleGet(RouteConfig route, File targetFile, String relativePath) {
        if (targetFile.isDirectory()) {
            if (route.getDefaultFile() != null) {
                File defaultFile = new File(targetFile, route.getDefaultFile());
                if (defaultFile.exists() && defaultFile.isFile()) {
                    return serveFile(defaultFile);
                }
                return buildErrorResponse(404, "Not Found", route.getErrorPages());
            }

            if (route.getDirectoryListing() != null && route.getDirectoryListing()) {
                return generateDirectoryListing(targetFile, relativePath, route);
            }

            return buildErrorResponse(403, "Forbidden", route.getErrorPages());
        }

        if (targetFile.exists() && targetFile.isFile()) {
            return serveFile(targetFile);
        }

        return buildErrorResponse(404, "Not Found", route.getErrorPages());
    }

    private static HttpResponse serveFile(File file) {
        HttpResponse response = new HttpResponse();
        try {
            byte[] fileContent = Files.readAllBytes(file.toPath());
            response.setBody(fileContent);

            String mimeType = MimeTypeUtil.getMimeType(file.getName());
            response.setHeader("Content-Type", mimeType);

            return response;
        } catch (IOException e) {
            return buildErrorResponse(500, "Internal Server Error", null);
        }
    }

    private static HttpResponse generateDirectoryListing(File dir, String relativePath, RouteConfig route) {
        HttpResponse response = new HttpResponse();
        response.setHeader("Content-Type", "text/html; charset=UTF-8");

        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n<html>\n<head>\n");
        html.append("<meta charset=\"UTF-8\">\n");
        html.append("<title>Index of ").append(escapeHtml(relativePath)).append("</title>\n");
        html.append("<style>\n");
        html.append("body { font-family: monospace; background: #1e1e1e; color: #d4d4d4; padding: 20px; }\n");
        html.append("h1 { border-bottom: 1px solid #444; padding-bottom: 10px; }\n");
        html.append("a { color: #569cd6; text-decoration: none; display: block; padding: 4px 8px; }\n");
        html.append("a:hover { background: #2a2a2a; }\n");
        html.append(".dir::before { content: \"📁 \"; }\n");
        html.append(".file::before { content: \"📄 \"; }\n");
        html.append("pre { line-height: 1.6; }\n");
        html.append("</style>\n</head>\n<body>\n");

        html.append("<h1>Index of ").append(escapeHtml(relativePath)).append("</h1>\n");
        html.append("<hr>\n<pre>\n");

        // Parent directory link (if not at root)
        if (!relativePath.equals("/") && !relativePath.isEmpty()) {
            String parentPath = relativePath.substring(0, relativePath.lastIndexOf('/'));
            if (parentPath.isEmpty())
                parentPath = "/";
            html.append("<a href=\"").append(escapeHtml(parentPath)).append("\" class=\"dir\">../</a>\n");
        }

        File[] files = dir.listFiles();
        if (files != null) {
            java.util.Arrays.sort(files, (a, b) -> {
                if (a.isDirectory() && !b.isDirectory())
                    return -1;
                if (!a.isDirectory() && b.isDirectory())
                    return 1;
                return a.getName().compareToIgnoreCase(b.getName());
            });

            for (File f : files) {
                String name = f.getName();
                String cssClass = f.isDirectory() ? "dir" : "file";
                String suffix = f.isDirectory() ? "/" : "";
                html.append("<a href=\"upload").append(escapeHtml(relativePath));
                if (!relativePath.endsWith("/"))
                    html.append("/");
                html.append(escapeHtml(name)).append(suffix).append("\" class=\"").append(cssClass).append("\">");
                html.append(escapeHtml(name)).append(suffix).append("</a>\n");
            }
        }

        html.append("</pre>\n<hr>\n");
        html.append("<em>Custom Java Server</em>\n");
        html.append("</body>\n</html>");

        response.setBody(html.toString().getBytes());
        return response;
    }

    private static String escapeHtml(String input) {
        if (input == null)
            return "";
        return input
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private static HttpResponse handlePost(HttpRequest request, RouteConfig route, File targetFile) {
        if (targetFile.isDirectory()) {
            return buildErrorResponse(400, "Bad Request - Cannot upload to a directory path", route.getErrorPages());
        }

        try {
            // Ensure parent directories exist
            File parentDir = targetFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                Files.createDirectories(parentDir.toPath());
            }

            byte[] body = request.getBody();
            if (body == null) {
                body = new byte[0];
            }

            Files.write(targetFile.toPath(), body);

            HttpResponse response = new HttpResponse();
            response.setStatusCode(201, "Created");
            response.setHeader("Content-Type", "text/plain; charset=UTF-8");
            response.setBody(("File uploaded successfully: " + targetFile.getName()).getBytes());
            return response;

        } catch (IOException e) {
            System.err.println("Upload failed: " + e.getMessage());
            return buildErrorResponse(500, "Internal Server Error - Upload failed", route.getErrorPages());
        }
    }

    private static HttpResponse handleDelete(RouteConfig route, File targetFile) {
        if (!targetFile.exists()) {
            return buildErrorResponse(404, "Not Found", route.getErrorPages());
        }

        if (targetFile.isDirectory()) {
            return buildErrorResponse(400, "Bad Request - Cannot delete a directory", route.getErrorPages());
        }

        try {
            Files.delete(targetFile.toPath());

            HttpResponse response = new HttpResponse();
            response.setStatusCode(200, "OK");
            response.setHeader("Content-Type", "text/plain; charset=UTF-8");
            response.setBody(("File deleted successfully: " + targetFile.getName()).getBytes());
            return response;

        } catch (IOException e) {
            System.err.println("Delete failed: " + e.getMessage());
            return buildErrorResponse(500, "Internal Server Error - Delete failed", route.getErrorPages());
        }
    }

    public static HttpResponse buildErrorResponse(int code, String message, Map<String, String> errorPages) {
        HttpResponse response = new HttpResponse();
        response.setStatusCode(code, message);
        response.setHeader("Content-Type", "text/html; charset=UTF-8");

        // 1. Try error pages from server config
        if (errorPages != null && errorPages.containsKey(String.valueOf(code))) {
            String errorPagePath = errorPages.get(String.valueOf(code));
            File errorFile = new File(errorPagePath);
            if (errorFile.exists() && errorFile.isFile()) {
                try {
                    byte[] fileContent = Files.readAllBytes(errorFile.toPath());
                    response.setBody(fileContent);
                    return response;
                } catch (IOException e) {
                    System.err.println("Warning: Failed to read custom error page: " + errorPagePath);
                }
            }
        }

        // 2. Fall back to default error_pages directory
        File errorFile = new File("./error_pages/" + code + ".html");
        if (errorFile.exists() && errorFile.isFile()) {
            try {
                byte[] fileContent = Files.readAllBytes(errorFile.toPath());
                response.setBody(fileContent);
                return response;
            } catch (IOException e) {
                System.err.println("Warning: Failed to read custom error page for " + code);
            }
        }

        // 3. Hardcoded HTML fallback
        String fallbackBody = "<html><body><center><h1>" + code + " - " + message
                + "</h1></center><hr><center>Custom Java Server</center></body></html>";
        response.setBody(fallbackBody.getBytes());
        return response;
    }
}