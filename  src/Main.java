
import utils.RouteConfig;
import utils.ServerConfig;



public class Main {
    public static void main(String[] args) {
        // Server code implementation goes here
        ConfigLoader configLoader = new ConfigLoader("config.json");
        configLoader.GlobalFormat();

        for (ServerConfig elem : configLoader.getServers()) {
            System.out.println("Server Name: " + elem.getServerName());
            System.out.println("Host: " + elem.getHost());
            System.out.println("Ports: " + elem.getPorts());
            System.out.println("Error Pages: " + elem.getErrorPages());
            System.out.println("Client Body Limit: " + elem.getClientBodyLimit());
            System.out.println("Routes: ");
            for (RouteConfig route : elem.getRoutes()) {
                System.out.println("  Path: " + route.getPath());
                System.out.println("  Method: " + route.getRedirect());
                System.out.println("  Handler: " + route.getCgiExtensions());
            }
        
            
        }
    }
}