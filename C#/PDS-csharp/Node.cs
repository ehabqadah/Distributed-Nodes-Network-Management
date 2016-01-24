using System;
using System.ServiceModel;
using Microsoft.Samples.XmlRpc;
using System.Net;
using System.Net.Sockets;
using System.Collections.Generic;
using System.Collections;
using System.Threading;
using System.Diagnostics;

namespace PDS

{
    /// <summary>
    /// This class responsible for the node operations 
    /// </summary>
    class Node : INodeOperation
    {
        public static string OK_MESSAGE = "ok i will take care of election";
        private static string ACCESS_OK_MESSAGE = "OK you can access it";
        private static string NOT_OK_MESSAGE = "NOT OK";
        public static char LOGICAL_CLOCK_AND_NODE_ID_SEPAROTR = ',';
        private static int RANDOM_WAIT_THREASHOLD_MS = 5000;
        private static int READ_WRITE_PERIOD_MS = 20 * 1000;
        private static int connectionPortNumber;
        public static Hashtable networkNodesIPs = new Hashtable();

        public static Hashtable networkNodesIpsAndAccessRequestResponse = new Hashtable();

        public static String ip;

        public static String nodeId;

        public static String superNodeIp;

        public static bool isSuperNode;

        public static bool electionStarted;

        public static int logicalClock;

        public static bool wantToAccessResource;

        public static bool curentllyAccessingTheResource;

        private static string accessRequestLogicalClock = "";

        public static List<string> accessRequestQueue = new List<string>();

        private static readonly object syncLCLock = new object();

        private static readonly object accessRequestQueueeslock = new object();

        private static readonly object accessResponseslock = new object();

        public static List<string> writesOnMasterNodeSharedString = new List<string>();

        public static string sharedString = "";
      

        public Node()
        {


        }

        public Node(String id)
        {

            nodeId = id;
        }
        /// <summary>
        /// Send a join request for node 
        /// </summary>
        /// <param name="connectedNodeIp"></param>
        /// <returns></returns>
        public bool join(String connectedNodeIp)
        {
            try
            {
                INodeOperation nodeAPI = createNodeAPI(connectedNodeIp);
                String result = nodeAPI.handleNewJoinRequest(GetLocalIPAddress(), nodeId, getExtentLogicalClock());
                networkNodesIPs = NodeUtils.parseIpList(result);
                Console.WriteLine("Join result :" + result);
            }
            catch (Exception ex)
            {
                Console.WriteLine("Exception :" + ex.Message);
            }
            return true;
        }


        /// <summary>
        ///  This method increment the logical clock value one step
        /// </summary>
        /// <returns></returns>
        private int incrementLogicalClock()
        {
            lock (syncLCLock)
            {
                logicalClock++;
                return logicalClock;
            }
        }

        /// <summary>
        /// Get the logical clock string for the sending operation =(logicalClock,nodeId)
        /// </summary>
        /// <returns></returns>
        public string getExtentLogicalClock()
        {
            return incrementLogicalClock() + "," + nodeId;
        }

        /// <summary>
        /// 
        /// Create the node API channel
        /// </summary>
        /// <param name="connectedNodeIp"></param>
        /// <returns></returns>

        public INodeOperation createNodeAPI(String connectedNodeIp)
        {

            Uri nodeAddress = new UriBuilder(Uri.UriSchemeHttp, connectedNodeIp, connectionPortNumber, "/RPC2").Uri;
            ChannelFactory<INodeOperation> nodeAPIFactory = new ChannelFactory<INodeOperation>(new WebHttpBinding(WebHttpSecurityMode.None), new EndpointAddress(nodeAddress));
            nodeAPIFactory.Endpoint.Behaviors.Add(new XmlRpcEndpointBehavior());
            return nodeAPIFactory.CreateChannel();

        }

        /// <summary>
        ///  Start the election process
        /// </summary>
        public void startElection()
        {
            // to make sure we don't initiate multiple election at the same node
            if (electionStarted)
            {
                return;
            }
            electionStarted = true;
            isSuperNode = true;

            List<Thread> threads = new List<Thread>();
            foreach (string nodeIp in networkNodesIPs.Keys)
            {

                // send the election messages for nodes with higher id 
                if (nodeId.CompareTo(networkNodesIPs[nodeIp]) < 0)
                {
                    Thread thread = new Thread(
                new ThreadStart(
                    () =>
                    {
                        sendElectionMessage(nodeIp);
                    })
                );
                    threads.Add(thread);
                    thread.Start();
                }

            }

            //wait all thread to finish
            foreach (Thread thread in threads)
            {

                thread.Join();

            }

            if (isSuperNode)
            {
                // Send coordination message for all other nodes
                foreach (String nodeIp in networkNodesIPs.Keys)
                {

                    sendCoordinationMessage(nodeIp);
                }

                superNodeIp = GetLocalIPAddress();//assign the super node ip in current node 

            }

            electionStarted = false;

        }

        /// <summary>
        /// Send an election message for node with higher id
        /// </summary>
        /// <param name="recieverNodeIp"></param>
        private void sendElectionMessage(String recieverNodeIp)
        {
            string result = null;
            try
            {
                INodeOperation nodeAPI = createNodeAPI(recieverNodeIp);
                result = nodeAPI.handleNewElectionMessage(GetLocalIPAddress(), getExtentLogicalClock());
            }
            catch (Exception ex)
            {
                Console.WriteLine("Exception :" + ex.Message);
            }

            if (OK_MESSAGE.Equals(result))
            {
                isSuperNode = false;
            }

        }
        /// <summary>
        /// Get the node IP
        /// </summary>
        /// <returns></returns>
        public string GetLocalIPAddress()
        {
            if (ip != null)
                return ip;
            var host = Dns.GetHostEntry(Dns.GetHostName());
            foreach (var ipAddress in host.AddressList)
            {
                if (ipAddress.AddressFamily == AddressFamily.InterNetwork)
                {
                    ip = ipAddress.ToString();
                    return ip;
                }
            }
            return null;
        }

        /// <summary>
        /// 
        /// Handle new join request from another node
        /// </summary>
        /// <param name="newNodeIp"></param>
        /// <param name="newNodeId"></param>
        /// <param name="senderLogicalClock"></param>
        /// <returns></returns>
        public string handleNewJoinRequest(string newNodeIp, String newNodeId, String senderLogicalClock)
        {
            updateLogicalClockOnReceive(senderLogicalClock);
            Console.WriteLine("Recieve a  join request from :" + newNodeIp + " node id=" + newNodeId);
            String result = "";
            result = "{";
            foreach (String nodeIp in networkNodesIPs.Keys)
            {

                result += nodeIp + "=" + networkNodesIPs[nodeIp] + ",";
            }

            result += GetLocalIPAddress() + "=" + nodeId + " }";


            //forward the new ip for other nodes 

            foreach (string nodeIp in networkNodesIPs.Keys)
            {

                forwardJoinRequest(nodeIp, newNodeIp, newNodeId);
            }

            if (!networkNodesIPs.Contains(newNodeIp))
                networkNodesIPs.Add(newNodeIp, newNodeId);

            return result;

        }
        /// <summary>
        /// This method update the logical clock to the max(currentLC,senderLC)+1
        /// </summary>
        /// <param name="senderLogicalClock"></param>
        private void updateLogicalClockOnReceive(String senderLogicalClock)
        {
            // split the sender logical clock to take just the LC 		
            String[] logicalClockAndNodeId = senderLogicalClock.Split(LOGICAL_CLOCK_AND_NODE_ID_SEPAROTR);
            int senderLC = int.Parse(logicalClockAndNodeId[0]);
            logicalClock = Math.Max(senderLC, logicalClock);
            incrementLogicalClock();
            Console.WriteLine("sender/current node logical clocks" + senderLogicalClock+"/"+logicalClock);
        }
        /// <summary>
        /// Forward a join request the all other nodes 
        /// </summary>
        /// <param name="recieverNodeIp"></param>
        /// <param name="newNodeIp"></param>
        /// <param name="newNodeId"></param>
        private void forwardJoinRequest(String recieverNodeIp, String newNodeIp, String newNodeId)
        {

            Thread thread = new Thread(
                new ThreadStart(
                    () =>
                    {

                        try
                        {
                            INodeOperation nodeAPI = createNodeAPI(recieverNodeIp);
                            nodeAPI.handleForwardNewJoinRequest(newNodeIp, newNodeId, getExtentLogicalClock());
                        }
                        catch (Exception ex)
                        {
                            Console.WriteLine("Exception :" + ex.Message);
                        }
                    })
                );

            thread.Start();

        }
        /// <summary>
        /// Set up the receiver part 
        /// </summary>
        /// <returns></returns>
        public ServiceHost setupServerPart(int portNumber)
        {
            connectionPortNumber = portNumber;
            Uri baseAddress = new UriBuilder(Uri.UriSchemeHttp, "localhost", portNumber, "/RPC2/").Uri;
            ServiceHost serviceHost = new ServiceHost(typeof(Node));
            var epXmlRpc = serviceHost.AddServiceEndpoint(typeof(INodeOperation), new WebHttpBinding(WebHttpSecurityMode.None), new Uri(baseAddress, "./"));
            epXmlRpc.Behaviors.Add(new XmlRpcEndpointBehavior());
            serviceHost.Open();
            Console.WriteLine("pds endpoint listening at {0}", epXmlRpc.ListenUri);
            return serviceHost;
        }

        /// <summary>
        /// Handle the forward new join request 
        /// </summary>
        /// <param name="newNodeIp"></param>
        /// <param name="newNodeId"></param>
        /// <returns></returns>
        public bool handleForwardNewJoinRequest(string newNodeIp, string newNodeId, String senderLogicalClock)
        {
            updateLogicalClockOnReceive(senderLogicalClock);
            Console.WriteLine("Recieve a forward join request for " + newNodeIp + " newNodeId" + newNodeId);
            if (!networkNodesIPs.Contains(newNodeIp))
                networkNodesIPs.Add(newNodeIp, newNodeId);
            return true;
        }


        /// <summary>
        /// Send a sign off request for all other nodes
        /// </summary>
        /// <returns></returns>
        public bool signOff()
        {
            // send sign off for the all other nodes
            foreach (String nodeIp in networkNodesIPs.Keys)
            {
                sendSignOffRequest(nodeIp);
            }
            return true;
        }

        /// <summary>
        /// Send a sign off request for another node
        /// </summary>
        /// <param name="recieverNodeIp"></param>
        private void sendSignOffRequest(String recieverNodeIp)
        {

            Thread thread = new Thread(
             new ThreadStart(
                 () =>
                 {
                     try
                     {

                         INodeOperation nodeAPI = createNodeAPI(recieverNodeIp);
                         String ip = GetLocalIPAddress();
                         nodeAPI.handleNewSignOffRequest(ip, getExtentLogicalClock());
                     }
                     catch (Exception ex)
                     {
                         Console.WriteLine("Exception :" + ex.Message);
                     }
                 })
             );

            thread.Start();

        }


        /// <summary>
        /// Handle new sign off message from another node 
        /// </summary>
        /// <param name="ip"></param>
        /// <returns></returns>
        public bool handleNewSignOffRequest(string ip, string senderLogicalClock)
        {
            updateLogicalClockOnReceive(senderLogicalClock);
            Console.WriteLine("Receive a sign off request from " + ip);
            networkNodesIPs.Remove(ip);
            return true;
        }

        /// <summary>
        /// Handle new election message from another node 
        /// </summary>
        /// <param name="senderNodeIp"></param>
        /// <returns></returns>
        public string handleNewElectionMessage(string senderNodeIp, string senderLogicalClock)
        {
            updateLogicalClockOnReceive(senderLogicalClock);
            Console.WriteLine("Receive a  new election message from " + senderNodeIp);
            Thread thread = new Thread(
             new ThreadStart(
                 () =>
                 {
                     startElection();
                 })
             );

            thread.Start();
            return OK_MESSAGE;
        }

        /// <summary>
        /// Send coordination message for node
        /// </summary>
        /// <param name="String"></param>
        /// <param name=""></param>
        public void sendCoordinationMessage(String recieverNodeIp)
        {
            INodeOperation nodeAPI = createNodeAPI(recieverNodeIp);

            Thread thread = new Thread(
                new ThreadStart(
                    () =>
                    {
                        try
                        {
                            nodeAPI.handleNewCoordinationMessage(GetLocalIPAddress(), getExtentLogicalClock());
                        }
                        catch (Exception ex)
                        {
                            Console.WriteLine("Exception :" + ex.Message);
                        }

                    })
                );

            thread.Start();
        }

        /// <summary>
        /// Handle new coordination message from the super node 
        /// </summary>
        /// <param name="coordinatorIp"></param>
        /// <returns></returns>
        public bool handleNewCoordinationMessage(string coordinatorIp, String senderLogicalClock)
        {
            updateLogicalClockOnReceive(senderLogicalClock);
            Console.WriteLine("Receiving a new coordination from  ip:"
                    + coordinatorIp);

            // set the super node ip
            superNodeIp = coordinatorIp;
            isSuperNode = false;
            return true;
        }



        //The mutual exclusion part 

        /// <summary>
        /// Start a distributed read / write process by sending a start message to all the other nodes in the network.
        /// </summary>
        /// <param name="mutualExclusionAlgorithm"></param>
        /// <param name="resourceId"></param>


        public void startDistributedReadWrite(MutualExclusionAlgorithm mutualExclusionAlgorithm, String resourceId)
        {

            // send start read/write for the all other nodes
            foreach (String nodeIp in networkNodesIPs.Keys)
            {
                sendStartMessage(nodeIp, mutualExclusionAlgorithm, resourceId);
            }

            // also start on the current node
            start(mutualExclusionAlgorithm.ToString(), resourceId, getExtentLogicalClock());
        }



        /// <summary>
        /// Send start message for certain node
        /// </summary>
        /// <param name="recieverNodeIp"></param>
        /// <param name="mutualExclusionAlgorithm"></param>
        /// <param name="resourceId"></param>
        private void sendStartMessage(string recieverNodeIp,
                 MutualExclusionAlgorithm mutualExclusionAlgorithm,
               String resourceId)
        {

            Thread thread = new Thread(
               new ThreadStart(
                   () =>
                   {

                       try
                       {
                           INodeOperation nodeAPI = createNodeAPI(recieverNodeIp);
                           nodeAPI.start(mutualExclusionAlgorithm.ToString(), resourceId, getExtentLogicalClock());
                       }
                       catch (Exception ex)
                       {
                           Console.WriteLine("Exception :" + ex.Message);
                       }
                   })
               );

            thread.Start();

        }


        /// <summary>
        ///  Start read write on master node shared string
        /// </summary>
        /// <param name="mutualExclusionAlgorithm"></param>
        /// <param name="resourceId"></param>
        /// <param name="senderLogicalClock"></param>
        /// <returns></returns>
        public bool start(String mutualExclusionAlgorithm,
                 String resourceId, String senderLogicalClock)
        {
            updateLogicalClockOnReceive(senderLogicalClock);
            // we create a thread to start the read/write and to don't block the
            // replay to the sender node
            Thread thread = new Thread(
               new ThreadStart(
                   () =>
                   {
                       Stopwatch stopwatch = new Stopwatch();
                       stopwatch.Start();
                       Random r = new Random();
                       while (stopwatch.ElapsedMilliseconds <= READ_WRITE_PERIOD_MS)
                       {
                           int randomWaitTime = r.Next(0, RANDOM_WAIT_THREASHOLD_MS);

                           Thread.Sleep(randomWaitTime);



                           if (stopwatch.ElapsedMilliseconds <= READ_WRITE_PERIOD_MS)
                           {
                               accessRequest((MutualExclusionAlgorithm)Enum.Parse(typeof(MutualExclusionAlgorithm), mutualExclusionAlgorithm), resourceId);
                           }
                           else
                           {
                               break;
                           }
                       }
                       checkSharedStringValue(resourceId);

                   })
               );

            thread.Start();
            return true;
        }


        /// <summary>
        /// Send an access request to use certain resource
        /// </summary>
        /// <param name="algorithm"></param>
        /// <param name="resourceId"></param>
        public void accessRequest(MutualExclusionAlgorithm algorithm,
                String resourceId)
        {

            switch (algorithm)
            {
                case MutualExclusionAlgorithm.Ricart:
                    richardAcessRequest(resourceId);
                    break;

                case MutualExclusionAlgorithm.Central:

                    centralAcessRequest(resourceId);
                    break;

                default:
                    break;
            }

        }

        /// <summary>
        /// Ricart Algorithm
        /// </summary>
        /// <param name="String"></param>
        /// <param name=""></param>
        private void richardAcessRequest(String resourceId)
        {

            if (wantToAccessResource || curentllyAccessingTheResource)
                return;

            networkNodesIpsAndAccessRequestResponse = new Hashtable();
            wantToAccessResource = true;
            curentllyAccessingTheResource = false;
            accessRequestLogicalClock = getExtentLogicalClock();
            string ricart = MutualExclusionAlgorithm.Ricart.ToString();
            foreach (String recieverNodeIp in networkNodesIPs.Keys)
            {
                Thread thread = new Thread(
               new ThreadStart(
                   () =>
                   {

                       try
                       {
                           INodeOperation nodeAPI = createNodeAPI(recieverNodeIp);
                           String result = nodeAPI.handleNewAccessRequest(ricart, GetLocalIPAddress(), accessRequestLogicalClock, resourceId);

                           // save that allowance from receiver node
                           if (ACCESS_OK_MESSAGE.Equals(result))
                           {

                               handleAllowAccessResponse(ricart, recieverNodeIp, resourceId);
                           }

                       }
                       catch (Exception ex)
                       {
                           //in the case of connection failure
                           handleAllowAccessResponse(ricart, recieverNodeIp, resourceId);
                           // we remove the node that crashed to avoid sending a future
                           // access request for it

                           networkNodesIPs.Remove(recieverNodeIp);
                           Console.WriteLine("Exception :" + ex.Message);
                       }

                   })
               );

                thread.Start();
            }

        }



        /// <summary>
        /// Central Algorithm
        /// </summary>
        /// <param name="String"></param>
        /// <param name=""></param>
        private void centralAcessRequest(String resourceId)
        {

            if (wantToAccessResource || curentllyAccessingTheResource)
                return;

            wantToAccessResource = true;
            curentllyAccessingTheResource = false;
            string central = MutualExclusionAlgorithm.Central.ToString();

            try
            {
                INodeOperation nodeAPI = createNodeAPI(superNodeIp);
                String result = nodeAPI.handleNewAccessRequest(central, GetLocalIPAddress(), getExtentLogicalClock(), resourceId);

                // the super allow the access 
                if (ACCESS_OK_MESSAGE.Equals(result))
                {
                    handleAllowAccessResponse(central, superNodeIp, resourceId);
                }

            }
            catch (Exception ex)
            {
                // if the super node crashed or doesn't response for the request we
                // start the election process
                Console.WriteLine("***** It seems the super node hase crashed we will start election!");
                startElection();
                wantToAccessResource = false;
                Console.WriteLine("Exception :" + ex.Message);
            }

        }




        /// <summary>
        /// Handle the response of the access request
        /// </summary>
        /// <param name="mutualExclusionAlgorithm"></param>
        /// <param name="senderIp"></param>
        /// <param name="resourceId"></param>
        /// <returns></returns>
        public bool handleAllowAccessResponse(string mutualExclusionAlgorithm, string senderIp, string resourceId)
        {
            Console.WriteLine("Receiving a new allow acess request from  ip:" + senderIp);
            switch ((MutualExclusionAlgorithm)Enum.Parse(typeof(MutualExclusionAlgorithm), mutualExclusionAlgorithm))
            {
                case MutualExclusionAlgorithm.Ricart:
                    handleRicartAccessRequestResponse(senderIp, resourceId);
                    break;

                case MutualExclusionAlgorithm.Central:
                    handleCentralAccessRequestResponse(senderIp, resourceId);
                    break;
                default:
                    break;
            }

            return true;
        }



        /// <summary>
        /// Handle the response of the access request in Ricart
        /// </summary>
        /// <param name="senderIp"></param>
        /// <param name="resourceId"></param>
        private void handleRicartAccessRequestResponse(String senderIp, String resourceId)
        {
            bool receiveOkFromAllNodes = false;
            lock (accessResponseslock)
            {

                networkNodesIpsAndAccessRequestResponse.Add(senderIp, true);
                foreach (String nodeIP in networkNodesIPs.Keys)
                {
                    receiveOkFromAllNodes = true;
                    Object nodeResponse = networkNodesIpsAndAccessRequestResponse[nodeIP];
                    if (nodeResponse == null || (!(Boolean)nodeResponse))
                    {
                        receiveOkFromAllNodes = false;
                        break;
                    }
                }
            }

            if (receiveOkFromAllNodes)
            {
                // after getting ok from all nodes
                accessResource(MutualExclusionAlgorithm.Ricart.ToString(), resourceId);
            }

        }

        /// <summary>
        /// Handle the response of the access request in Central
        /// </summary>
        /// <param name="senderIp"></param>
        /// <param name="resourceId"></param>
        private void handleCentralAccessRequestResponse(String senderIp, String resourceId)
        {

            accessResource(MutualExclusionAlgorithm.Central.ToString(), resourceId);

        }
        /// <summary>
        ///  Access certain resource after getting the permission
        /// </summary>
        /// <param name="mutualExclusionAlgorithm"></param>
        /// <param name="resourceId"></param>
        private void accessResource(String mutualExclusionAlgorithm, String resourceId)
        {
            curentllyAccessingTheResource = true;
            Console.WriteLine("Start Access resource " + resourceId);
            readWriteOnResource(resourceId);
            Console.WriteLine("Finish Access resource " + resourceId);
            sendReleaseMessage(mutualExclusionAlgorithm, resourceId);

        }


        /// <summary>
        /// a)Read the string variable from the master node c) Append some random
        /// English word to this string d) Write the updated string to the master
        /// node
        /// </summary>
        /// <param name="resourceId"></param>
        private void readWriteOnResource(string resourceId)
        {
            // read resource from super node
            string superNodeSharedString = readResourceFromSuperNode(resourceId);
            // in the cases of master node has been crashed
            if (superNodeSharedString == null)
                return;
            string randomEnglishStringToAppend = getRandomString();
            writesOnMasterNodeSharedString.Add(randomEnglishStringToAppend);
            //append random string 
            superNodeSharedString += randomEnglishStringToAppend;
            updateResourceOnSuperNode(resourceId, superNodeSharedString);
        }

        /// <summary>
        /// Update resource on the super node
        /// </summary>
        /// <param name="resourceId"></param>
        /// <param name="newValue"></param>
        /// <returns></returns>
        private bool updateResourceOnSuperNode(String resourceId, String newValue)
        {
            try
            {
                INodeOperation nodeAPI = createNodeAPI(superNodeIp);
                bool result = nodeAPI.updateResource(GetLocalIPAddress(), resourceId, newValue, getExtentLogicalClock());
                Console.WriteLine("Write resource on super node  result:" + result);
                return result;

            }
            catch (Exception ex)
            {
                Console.WriteLine("Exception :" + ex.Message);
                // if the super node crashed or doesn't response for the request we
                // start the election process
                Console.WriteLine("***** It seems the super node hase crashed we will start election!");
                startElection();
                return false;
            }
        }

        /// <summary>
        /// Write/update operation on certain resource
        /// </summary>
        /// <param name="senderIp"></param>
        /// <param name="resourceId"></param>
        /// <param name="newResourceValue"></param>
        /// <param name="senderLogicalClock"></param>
        /// <returns></returns>
        public bool updateResource(String senderIp, String resourceId,
                String newResourceValue, String senderLogicalClock)
        {

            updateLogicalClockOnReceive(senderLogicalClock);

            Console.WriteLine("Receive an update operation on " + resourceId
                    + "from node:" + senderIp);

            sharedString = newResourceValue;

            return true;
        }
        /// <summary>
        ///  Read resource from super node
        /// </summary>
        /// <param name="resourceId"></param>
        /// <returns></returns>
        private String readResourceFromSuperNode(String resourceId)
        {

            try
            {
                INodeOperation nodeAPI = createNodeAPI(superNodeIp);
                string result = nodeAPI.readResource(GetLocalIPAddress(), resourceId, getExtentLogicalClock());
                Console.WriteLine("Read resource from super node  result:" + result);
                return result;

            }
            catch (Exception ex)
            {
                Console.WriteLine("Exception :" + ex.Message);
                // if the super node crashed or doesn't response for the request we
                // start the election process

                Console.WriteLine("***** It seems the super node hase crashed we will start election!");
                startElection();
                return null;
            }
        }

        /// <summary>
        /// Read operation on certain resource
        /// </summary>
        /// <param name="senderIp"></param>
        /// <param name="resourceId"></param>
        /// <param name="senderLogicalClock"></param>
        /// <returns></returns>
        public string readResource(string senderIp, string resourceId,
                string senderLogicalClock)
        {

            updateLogicalClockOnReceive(senderLogicalClock);

            Console.WriteLine("Receive a read operation on " + resourceId
                    + "from node:" + senderIp);

            return sharedString;
        }

        /// <summary>
        /// Get Random String
        /// </summary>
        /// <returns></returns>
        private String getRandomString()
        {
            
            return "  " + nodeId + NodeUtils.generateRandomString();
        }
        /// <summary>
        ///  Send a release message after finishing the access on the resource
        /// </summary>
        /// <param name="mutualExclusionAlgorithm"></param>
        /// <param name="resourceId"></param>
        private void sendReleaseMessage(String mutualExclusionAlgorithm, String resourceId)
        {

            switch ((MutualExclusionAlgorithm)Enum.Parse(typeof(MutualExclusionAlgorithm), mutualExclusionAlgorithm))
            {
                case MutualExclusionAlgorithm.Ricart:
                    sendRicartReleaseMessage(resourceId);
                    break;

                case MutualExclusionAlgorithm.Central:
                    sendCentralReleaseMessage(resourceId);
                    break;
            }

            wantToAccessResource = false;
            curentllyAccessingTheResource = false;
        }

        /// <summary>
        /// Send a release message for the all queued access requests
        /// </summary>
        /// <param name="String"></param>
        /// <param name=""></param>
        private void sendRicartReleaseMessage(String resourceId)
        {
            // send OK to all processes in queue
            String ricart = MutualExclusionAlgorithm.Ricart.ToString();
            // send release for all other nodes in the access request queue
            foreach (String recieverNodeIp in accessRequestQueue)
            {
                Thread thread = new Thread(
               new ThreadStart(
                 () =>
                 {

                     try
                     {
                         INodeOperation nodeAPI = createNodeAPI(recieverNodeIp);
                         nodeAPI.handleAllowAccessResponse(ricart, GetLocalIPAddress(), resourceId);
                     }
                     catch (Exception ex)
                     {
                         Console.WriteLine("Exception :" + ex.Message);
                     }

                 })
               );

                thread.Start();

            }
            // empty queue
            accessRequestQueue = new List<String>();

        }



        /// <summary>
        /// Send a release message for super node
        /// </summary>
        /// <param name="String"></param>
        /// <param name=""></param>
        private void sendCentralReleaseMessage(String resourceId)
        {
            // send OK to all processes in queue
            String central = MutualExclusionAlgorithm.Central.ToString();
            // send release for all other nodes in the access request queue


            try
            {
                INodeOperation nodeAPI = createNodeAPI(superNodeIp);
                nodeAPI.handleReleaseResource(central, GetLocalIPAddress(), resourceId, getExtentLogicalClock());
            }
            catch (Exception ex)
            {
                Console.WriteLine("Exception :" + ex.Message);
            }

        }


        /// <summary>
        /// Handle the release message 
        /// </summary>
        /// <param name="mutualExclusionAlgorithm"></param>
        /// <param name="senderIp"></param>
        /// <param name="resourceId"></param>
        /// <param name="senderLogicalClock"></param>
        /// <returns></returns>
        public bool handleReleaseResource(string mutualExclusionAlgorithm,
                string senderIp, string resourceId, string senderLogicalClock)
        {
            Console.WriteLine("Receiving realease message from  ip:"
                     + senderIp);

            switch ((MutualExclusionAlgorithm)Enum.Parse(typeof(MutualExclusionAlgorithm), mutualExclusionAlgorithm))
            {


                case MutualExclusionAlgorithm.Central:
                    handleCentralRelease(senderIp, resourceId, senderLogicalClock);
                    break;
            }

            return true;
        }
        /// <summary>
        /// Handle release message in central
        /// </summary>
        /// <param name="senderIp"></param>
        /// <param name="resourceId"></param>
        /// <param name="senderLogicalClock"></param>
        private void handleCentralRelease(string senderIp, string resourceId,
                string senderLogicalClock)
        {
            updateLogicalClockOnReceive(senderLogicalClock);

            // to avoid getting the access by multiple node, if the two request
            // received at the same time
            lock (accessRequestQueueeslock)
            {
                //remove the the released access request from the queue 
                accessRequestQueue.Remove(senderIp);
                if (accessRequestQueue.Count > 0)
                {
                    String newGrantedNode = accessRequestQueue[0];
                    Thread thread = new Thread(new ThreadStart(
                    () =>
                    {
                        //send a new ok message fro the granted node 
                        try
                        {
                            INodeOperation nodeAPI = createNodeAPI(newGrantedNode);
                            nodeAPI.handleAllowAccessResponse(MutualExclusionAlgorithm.Central.ToString(), GetLocalIPAddress(), resourceId);
                        }
                        catch (Exception ex)
                        {
                            Console.WriteLine("Exception :" + ex.Message);
                        }

                    })
                    );

                    thread.Start();
                }
            }

        }
        /// <summary>
        /// Handle new access request
        /// </summary>
        /// <param name="mutualExclusionAlgorithm"></param>
        /// <param name="senderNodeIp"></param>
        /// <param name="senderLC"></param>
        /// <param name="resourceId"></param>
        /// <returns></returns>
        public String handleNewAccessRequest(String mutualExclusionAlgorithm,
                String senderNodeIp, String senderLC, String resourceId)
        {
            updateLogicalClockOnReceive(senderLC);
            Console.WriteLine("Receiving a new acess request from  ip:"
                     + senderNodeIp);
            switch ((MutualExclusionAlgorithm)Enum.Parse(typeof(MutualExclusionAlgorithm), mutualExclusionAlgorithm))
            {
                case MutualExclusionAlgorithm.Ricart:
                    return handleRicartAccessRequest(senderNodeIp, senderLC, resourceId);

                case MutualExclusionAlgorithm.Central:
                    return handleCentralAccessRequest(senderNodeIp, senderLC, resourceId);


                default:
                    break;
            }

            return null;
        }


        /// <summary>
        /// Handle new Ricart access request to certain resource
        /// </summary>
        /// <param name="senderNodeIp"></param>
        /// <param name="senderLC"></param>
        /// <param name="resourceId"></param>
        /// <returns></returns>
        private String handleRicartAccessRequest(String senderNodeIp,
                String senderLC, String resourceId)
        {
            // if (not accessing resource and do not want to access it)
            if (!wantToAccessResource && !curentllyAccessingTheResource)
            {
                return ACCESS_OK_MESSAGE;
            }
            // (currently using resource)
            else if (curentllyAccessingTheResource)
            {
                accessRequestQueue.Add(senderNodeIp);

            }
            else if (wantToAccessResource)
            {
                // time stamp of request is smaller
                if (NodeUtils.compareExtentLogicalClock(senderLC, accessRequestLogicalClock) < 0)
                {
                    return ACCESS_OK_MESSAGE;
                }
                else
                {
                    // own timestamp is smaller
                    accessRequestQueue.Add(senderNodeIp);

                }
            }
            return NOT_OK_MESSAGE;
        }

        /// <summary>
        /// Handle new central access request to certain resource
        /// </summary>
        /// <param name="senderNodeIp"></param>
        /// <param name="senderLC"></param>
        /// <param name="resourceId"></param>
        /// <returns></returns>
        private String handleCentralAccessRequest(String senderNodeIp,
                String senderLC, String resourceId)
        {

            updateLogicalClockOnReceive(senderLC);
            String result = NOT_OK_MESSAGE;
            //to avoid getting the access by multiple node, if the two request received at the same time 
            lock (accessRequestQueueeslock)
            {
                if (accessRequestQueue.Count == 0)
                {
                    result = ACCESS_OK_MESSAGE;
                }
                //add the access request to the queue  		
                accessRequestQueue.Add(senderNodeIp);
            }
            return result;
        }


        /// <summary>
        /// Read the final string from the master node and write it to the screen.
        /// Moreover they check if all the words they added to the string are present
        /// in the final string. The result of this check is also written to the
        /// screen
        /// </summary>
        /// <param name="resourceId"></param>
        private void checkSharedStringValue(String resourceId)
        {
            // in the case of pending access request we will wait them to be
            // finished
            while (wantToAccessResource || curentllyAccessingTheResource)
            {

                Thread.Sleep(500);

            }

            String superNodeSharedString = readResourceFromSuperNode(resourceId);

            Console.WriteLine("***** The shared string on the master node= "
                      + superNodeSharedString);

            int failedWritesCount = 0;
            // check if all write operations are in the shared string
            foreach (String randomString in writesOnMasterNodeSharedString)
            {

                if (!superNodeSharedString.Contains(randomString))
                {
                    failedWritesCount++;
                    Console.WriteLine("Failed Write:" + randomString);
                }
            }

            Console.WriteLine("**** " + failedWritesCount + " of "
                     + writesOnMasterNodeSharedString.Count + " were failed!");

        }
      

        public enum MutualExclusionAlgorithm
        {
            Central, Ricart
        };
    }
}
