package utils;

import java.util.List;
import java.util.Map;

public class ServerConfig {
    private String host;
    private List<Integer> ports;
    private String serverName;
    private Map<String, String> errorPages;
    private Long clientBodyLimit;
    private List<RouteConfig> routes;

    public ServerConfig(String host, List<Integer> ports, String serverName, Map<String, String> errorPages, Long clientBodyLimit, List<RouteConfig> routes) {
        this.host = host;
        this.ports = ports;
        this.serverName = serverName;
        this.errorPages = errorPages;
        this.clientBodyLimit = clientBodyLimit;
        this.routes = routes;
    }

    // Getters
    public String getHost() { return host; }
    public List<Integer> getPorts() { return ports; }
    public String getServerName() { return serverName; }
    public Map<String, String> getErrorPages() { return errorPages; }
    public Long getClientBodyLimit() { return clientBodyLimit; }
    public List<RouteConfig> getRoutes() { return routes; }
}
