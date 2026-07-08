package utils;

import java.io.File;

public interface CgiExecutor {
    HttpResponse handle(HttpRequest request, File scriptFile, String scriptPath, String queryString) throws Exception;
}