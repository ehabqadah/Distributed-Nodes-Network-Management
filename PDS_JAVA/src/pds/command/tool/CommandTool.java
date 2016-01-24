package pds.command.tool;

import java.util.Scanner;

import pds.network.MutualExclusionAlgorithm;
import pds.network.Node;

/**
 * This class responsible for the node commands
 * 
 *
 */
public class CommandTool {

	public static void main(String[] args) {

		Scanner in = new Scanner(System.in);
		
			System.out.println(" Enter the nodeId:");
			Node node = new Node(in.nextLine());
			System.out.println(" Enter the port number:");
			in = new Scanner(System.in);
		   node.setupServerPart(Integer.parseInt(in.nextLine()));
		   
		   
		System.out.println(" Enter command join or signoff or  startElection or start (Central or Ricart) or exit");

		while (true) {
			String command = null;
			try {
				
				// Reads a single line from the console
				// and stores into name variable
				command = in.nextLine();
				// read line from the user input
				String[] commandAndParms = command.split(" ");
				switch (commandAndParms[0].toLowerCase()) {

				case "exit":
					System.exit(0);
					break;
				case "signoff":
					node.signOff();
					break;

				case "join":
					if (commandAndParms.length > 1)
						node.join(commandAndParms[1]);
					else
						System.out.println("enter join and ip like join 192.168.1.1");
					break;

				case "startelection":
					node.startElection();
					break;
					
				case "start":
					if (commandAndParms.length > 1)
						node.startDistributedReadWrite(MutualExclusionAlgorithm.valueOf(commandAndParms[1]), "r11");
					else
						System.out.println("enter start and Central or Ricart");
					break;
					
					
				default:
					break;
				}

			} catch (Exception ex) {

				System.out.println("Error :" + ex.getMessage());
			}
		}

	}

}
