package utils;

public class Validate {

    public static boolean isValidServerConfig(ServerConfig config) {
        if (config.getServerName() == null || config.getServerName().isEmpty()) {
            return false;
        }
        if (config.getHost() == null || config.getHost().isEmpty()) {


            return false;
        }
       String  host  =   config.getHost();
   

        return true;
    }

}
