
using System;
using System.ServiceModel;

namespace PDS
{
    class Program
    {
        /// <summary>
        /// 
        /// </summary>
        /// <param name="args"></param>
        static void Main(string[] args)
        {
            Console.WriteLine("Enter node id");
            Node node = new Node(Console.ReadLine());
            Console.WriteLine("  Enter port number:");
            ServiceHost webServer = node.setupServerPart(int.Parse(Console.ReadLine()));


            Console.WriteLine("  Enter command join or signoff or  startElection or start (Central or Ricart) or exit");

            while (true)
            {
                String command = null;
                try
                {
                    // Reads a single line from the console
                    // and stores into name variable
                    command = Console.ReadLine();
                    // read line from the user input
                    String[] commandAndParms = command.Split(' ');
                    switch (commandAndParms[0].ToLower())
                    {

                        case "exit":
                            webServer.Close();
                            System.Environment.Exit(0);
                            break;
                        case "signoff":
                            node.signOff();
                            break;

                        case "join":
                            if (commandAndParms.Length > 1)
                                node.join(commandAndParms[1]);
                            else
                                Console.WriteLine
                                ("enter join and ip like join 192.168.1.1");
                            break;
                        case "startelection":
                            node.startElection();
                            break;
                        case "start":


                            if (commandAndParms.Length > 1)
                                node.startDistributedReadWrite((Node.MutualExclusionAlgorithm)Enum.Parse(typeof(Node.MutualExclusionAlgorithm), commandAndParms[1]), "r11");
                            else
                                Console.WriteLine
                                ("enter start (Central or Ricart) ");
                            break;
                        default:
                            break;
                    }

                }
                catch (Exception ex)
                {
                    // if any error occurs
                    Console.WriteLine(ex);
                }
            }



        }
    }
}
