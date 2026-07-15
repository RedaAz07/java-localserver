package utils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public class ResponseBuilder {

    public static HttpResponse build(HttpRequest request, RouteConfig route) {
        HttpResponse response = new HttpResponse();

        if (route == null) {
            return buildErrorResponse(404, "Not Found");
        }

        if (route.getMethods() != null && !route.getMethods().contains(request.getMethod())) {
            return buildErrorResponse(405, "Method Not Allowed");
        }

        String relativePath = request.getPath().substring(route.getPath().length());
        if (!relativePath.startsWith("/")) {
            relativePath = "/" + relativePath;
        }

        File targetFile = new File(route.getRoot() + relativePath);

        if (targetFile.isDirectory()) {
            if (route.getDefaultFile() != null) {
                targetFile = new File(targetFile, route.getDefaultFile());
            } else if (route.getDirectoryListing() != null && route.getDirectoryListing()) {
                response.setBody("<html><body>Directory Listing Enabled (WIP)</body></html>".getBytes());
                response.setHeader("Content-Type", "text/html; charset=UTF-8");
                return response;
            } else {
                return buildErrorResponse(403, "Forbidden");
            }
        }

        if (targetFile.exists() && targetFile.isFile()) {
            try {
                byte[] fileContent = Files.readAllBytes(targetFile.toPath());
                response.setBody(fileContent);

                String mimeType = MimeTypeUtil.getMimeType(targetFile.getName());
                response.setHeader("Content-Type", mimeType);

                return response;
            } catch (IOException e) {
                return buildErrorResponse(500, "Internal Server Error");
            }
        } else {
            return buildErrorResponse(404, "Not Found");
        }
    }

    private static HttpResponse buildErrorResponse(int code, String message) {
        HttpResponse response = new HttpResponse();
        response.setStatusCode(code, message);
        response.setHeader("Content-Type", "text/html; charset=UTF-8");

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

        String fallbackBody = "<html><body><center><h1>" + code + " - " + message
                + "</h1></center><hr><center>Custom Java Server</center></body></html>";
        response.setBody(fallbackBody.getBytes());
        return response;
    }
}