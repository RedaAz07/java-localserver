package utils;

import java.util.List;

public class RouteConfig {
    private String path;
    private String root;
    private String default_file;
    private List<String> methods;
    private Boolean directory_listing;
    private Long client_body_limit; 
    private List<String> cgi_extensions; 
    private String redirect;

    public RouteConfig(String path, String root, String default_file, List<String> methods, Boolean directory_listing, Long client_body_limit, List<String> cgi_extensions, String redirect) {
        this.path = path;
        this.root = root;
        this.default_file = default_file;
        this.methods = methods;
        this.directory_listing = directory_listing;
        this.client_body_limit = client_body_limit;
        this.cgi_extensions = cgi_extensions;
        this.redirect = redirect;
    }

    public String getPath() { return path; }
    public String getRoot() { return root; }
    public String getDefaultFile() { return default_file; }
    public List<String> getMethods() { return methods; }
    public Boolean getDirectoryListing() { return directory_listing; }
    public Long getClientBodyLimit() { return client_body_limit; }
    public List<String> getCgiExtensions() { return cgi_extensions; }
    public String getRedirect() { return redirect; }
}
