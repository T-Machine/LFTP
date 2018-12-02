package command;

import picocli.CommandLine;

@CommandLine.Command(name = "lget", mixinStandardHelpOptions = true, description = "Get files from the specified IP address")
public class Lget implements Runnable  {

    @CommandLine.Option(names = {"-s", "--myserver"}, description = "Server IP address", defaultValue = "localhost")
    private String serverAddress;

    @CommandLine.Parameters(description = "file name", defaultValue = "")
    private String filename;

    @CommandLine.Option(names = {"-cp", "--controlport"}, description = "The control PORT", defaultValue = "4000")
    private int controlPort;

    @CommandLine.Option(names = {"-dp", "--dataport"}, description = "The data PORT", defaultValue = "3777")
    private int dataPort;

    @Override
    public void run() {



    }

}