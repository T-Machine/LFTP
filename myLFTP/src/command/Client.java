package command;

import picocli.CommandLine;
import picocli.CommandLine.RunLast;

@CommandLine.Command(name = "LFTP", mixinStandardHelpOptions = true, description = "to support large file transfer between two computers in the Internet")
public class Client implements Runnable  {

    public static void main(String[] args) {
        CommandLine cmd = new CommandLine(new Client());
        cmd.addSubcommand("lget", new Lget());
        cmd.addSubcommand("lsend", new Lsend());
        cmd.parseWithHandler(new RunLast(), args);
    }

    @Override
    public void run() {
        new CommandLine(this).usage(System.out);
    }
}
