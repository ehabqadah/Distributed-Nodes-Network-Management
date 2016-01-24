package pds.network;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import org.apache.xmlrpc.WebServer;

import pds.xmlrpc.client.XMLRpcClientUtil;
import pds.xmlrpc.server.XMLRpcServerUtils;

/**
 * 
 * This Class responsible for the node operations
 *
 */
public class Node {

	
	
	/**
	 * Constants
	 */
	private static final String NODE_HANDLER_NAME = "node";
	private static final String NODE_HANDLE_RELEASE_RESOURCE_METHOD_NAME = "node.handleReleaseResource";
	private static final String NODE_HANDLE_NEW_COORDINATION_MESSAGE_METHOD_NAME = "node.handleNewCoordinationMessage";
	private static final String NODE_HANDLE_NEW_ELECTION_MESSAGE_METHOD_NAME = "node.handleNewElectionMessage";
	private static final String NODE_HANDLE_NEW_SIGN_OFF_REQUEST_METHOD_NAME = "node.handleNewSignOffRequest";
	private static final String NODE_HANDLE_NEW_JOIN_REQUEST_METHOD_NAME = "node.handleNewJoinRequest";
	private static final String NODE_HANDLE_FORWARD_NEW_JOIN_REQUEST_METHOD_NAME = "node.handleForwardNewJoinRequest";
	private static final String NODE_HANDLE_NEW_ACCESS_REQUEST_METHOD_NAME = "node.handleNewAccessRequest";
	private static final String NODE_HANDLE_ALLOW_ACCESS_RESPONSE_METHOD_NAME = "node.handleAllowAccessResponse";
	private static final String NOT_OK_MESSAGE = "NOT OK";	
	private static final String ELECTION_OK_MESSAGE = "ok i will take care of election";
	private static final String RPC_URL= "/RPC2";
	private static final String ACCESS_OK_MESSAGE = "OK you can access it";
	static final String LOGICAL_CLOCK_AND_NODE_ID_SEPAROTR = ",";
	private static final int RANDOM_WAIT_THREASHOLD_MS = 5000;
	private static final int READ_WRITE_PERIOD_MS = 20 * 1000;
	private static final String NODE_UPDATE_RESOURCE_METHOD_NAME = "node.updateResource";
	private static final String NODE_START_METHOD_NAME = "node.start";
	/**
	 * Members and Properties 
	 */
	/** The network's nodes IPs and IDs*/
	public Map<String, String> networkNodesIps = new HashMap<String, String>();

	public Map<String, Boolean> networkNodesIpsAndAccessRequestResponse = new HashMap<String, Boolean>();

	private Object accessResponseslock = new Object();
	
	private Object accessRequestQueueeslock = new Object();

	public String ip;

	public String nodeId;

	public String superNodeIp = "";

	public boolean isSuperNode;

	protected boolean electionStarted;

	private int logicalClock;
	
	public boolean wantToAccessResource;
	
	public boolean curentllyAccessingTheResource;
	
	private String accessRequestLogicalClock;

	public List<String> accessRequestQueue = new ArrayList<String>();

	public String sharedString = "";

	public List<String> writesOnMasterNodeSharedString = new ArrayList<String>();
	
	private int portNumber;

	/**
	 * Constructors
	 */
	public Node(String id) {

		this.nodeId = id;
	}

	/***
	 * 
	 * Methods
	 * 
	 */

	/***
	 * This method start XML-RPC Server for node to be able to receive requests
	 * from the other nodes
	 * 
	 * @return
	 */
	public boolean setupServerPart(int portNumber) {
         this.portNumber=portNumber;
		 WebServer server=XMLRpcServerUtils.setupXmlRPCServer(portNumber, NODE_HANDLER_NAME, this);

		return server!=null;
	}

	/***
	 * This method send a join request to the node
	 * 
	 * @param conectedNodeIp
	 */
	public boolean join(String conectedNodeIp) {

		String url = getNodeUrl(conectedNodeIp);
		String methodName = NODE_HANDLE_NEW_JOIN_REQUEST_METHOD_NAME;
		Vector<String> params = new Vector<String>();
		params.addElement(this.getIp());
		params.addElement(this.nodeId);
		Object result = sendRequest(url, methodName, params);
		System.out.println("Join result:" + result);
		// store network nodes list
		this.networkNodesIps = NodeUtils.parseIpList((String) result);
		return true;
	}

	/***
	 * Handle the join request from another node
	 * 
	 * @param newNodeIp
	 * @param newNodeId
	 * @param logicalClock
	 * 
	 */
	public String handleNewJoinRequest(String newNodeIp, String newNodeId,
			String senderLogicalClock) {
		updateLogicalClockOnReceive(senderLogicalClock);
		System.out.println("Receving a new join request from ip:" + newNodeIp
				+ "; id =" + newNodeId);

		Map<String, String> oldNodesMap = new HashMap<String, String>(
				this.networkNodesIps);
		// add current node  ip and id to the map
		oldNodesMap.put(this.getIp(), this.nodeId);
		String oldNodes = oldNodesMap.toString();
		// forward the new ip for the all other nodes
		for (String nodeIp : networkNodesIps.keySet()) {
			forwardJoinRequest(nodeIp, newNodeIp, newNodeId);
		}
		// add the new ip
		if (!this.networkNodesIps.keySet().contains(newNodeIp))
			this.networkNodesIps.put(newNodeIp, newNodeId);
		// TODO: add the logical clock to result
		return oldNodes;
	}
	
	/**
	 * Send a request for the url with method specification
	 * 
	 * @param url
	 * @param methodName
	 * @param params
	 * @return null in the case of any connection error
	 */
	private Object sendRequest(String url, String methodName,
			Vector<String> params) {
		params.addElement(getExtentLogicalClock());
		Object result = XMLRpcClientUtil.sendXMLRpcRequest(url, methodName,
				params);
		return result;
	}

	/**
	 * Get the extend logical clock string LC+nodeID
	 * @return
	 */
	private String getExtentLogicalClock() {
		return incrementLogicalClock() + LOGICAL_CLOCK_AND_NODE_ID_SEPAROTR
				+ this.nodeId;
	}

	/**
	 * This method increment the logical clock value one step
	 */
	private synchronized int incrementLogicalClock() {
		this.logicalClock++;
		return this.logicalClock;
	}

	/**
	 * Get the url to send xml-rpc request 
	 * @param conectedNodeIp
	 * @return
	 */
	private String getNodeUrl(String conectedNodeIp) {
		return "http://" + conectedNodeIp + ":"+portNumber+RPC_URL;
	}

	

	/**
	 * This method update the logical clock to the max(currentLC,senderLC)+1
	 * 
	 * @param senderLogicalClock
	 */
	private void updateLogicalClockOnReceive(String senderLogicalClock) {
		
		//split the sender extent Lamport logical clock 
		String[] logicalClockAndNodeId = senderLogicalClock
				.split(LOGICAL_CLOCK_AND_NODE_ID_SEPAROTR);
		int senderLC = Integer.parseInt(logicalClockAndNodeId[0]);
		//use the maximum 
		this.logicalClock = Math.max(senderLC, this.logicalClock);
		incrementLogicalClock();
		System.out.println("Sender LC/ current LC :" + senderLogicalClock +"/"+this.logicalClock);
	}

	/**
	 * Forward the new join request for all other nodes
	 * 
	 * @param receiverNodeIp
	 * @param newNodeIp
	 * @param newNodeId
	 */
	private void forwardJoinRequest(final String receiverNodeIp,
			final String newNodeIp, final String newNodeId) {
		Thread thread = new Thread(new Runnable() {
			public void run() {
				//send a the new node information for receiver node 
				String url = getNodeUrl(receiverNodeIp);		
				String methodName = NODE_HANDLE_FORWARD_NEW_JOIN_REQUEST_METHOD_NAME;
				Vector<String> params = new Vector<String>();
				params.addElement(newNodeIp);
				params.addElement(newNodeId);
				sendRequest(url, methodName, params);
			}
		});
		thread.start();

	}

	/**
	 * Handle new forward join request
	 * 
	 * @param newNodeIp
	 * @param newNodeId
	 * @return
	 */
	public boolean handleForwardNewJoinRequest(String newNodeIp,
			String newNodeId, String senderLogicalClock) {
		updateLogicalClockOnReceive(senderLogicalClock);
		System.out.println("Receving a forward join request for the new ip:" + newNodeIp
				+ "; id =" + newNodeId);
		//add the new node information 
		if (!this.networkNodesIps.keySet().contains(newNodeIp))
			this.networkNodesIps.put(newNodeIp, newNodeId);
		return true;
	}

	/**
	 * Get the node IP
	 */
	private String getIp() {
		if (this.ip == null) {
			try {
				ip = InetAddress.getLocalHost().getHostAddress();
			} catch (UnknownHostException e) {
				System.out.println("Unable to get IP:" + e.getMessage());
			}
		}

		return ip;
	}

	/**
	 * Send a sign off for all network nodes
	 * 
	 * @return
	 */
	public boolean signOff() {
		// send sign off for the all other nodes
		for (String nodeIp : networkNodesIps.keySet()) {
			sendSignOffRequest(nodeIp);
		}
		return true;
	}

	/**
	 * Send a sign off request for certain node
	 * 
	 * @param recieverNodeIp
	 */
	private void sendSignOffRequest(final String recieverNodeIp) {

		Thread thread = new Thread(new Runnable() {
			public void run() {
				String url = getNodeUrl(recieverNodeIp);
				String methodName = NODE_HANDLE_NEW_SIGN_OFF_REQUEST_METHOD_NAME;
				Vector<String> params = new Vector<String>();
				params.addElement(Node.this.getIp());
				sendRequest(url, methodName, params);
			}
		});

		thread.start();

	}

	/**
	 * Handle sign off request from another node
	 * 
	 * @param signedOffNodeIp
	 * @param senderLogicalClock
	 * @return
	 */
	public boolean handleNewSignOffRequest(String signedOffNodeIp,
			String senderLogicalClock) {
		updateLogicalClockOnReceive(senderLogicalClock);
		System.out.println("Recieve sign off request from " + signedOffNodeIp);
		this.networkNodesIps.remove(signedOffNodeIp);

		// start election if the signed off node is the super node
		if (signedOffNodeIp.equals(this.superNodeIp)) {

			Thread thread = new Thread(new Runnable() {

				@Override
				public void run() {
					startElection();

				}
			});
			thread.start();

		}

		return true;
	}

	/***
	 * Start the election process
	 */
	public void startElection() {
		// to make sure we don't initiate multiple election at the same node
		if (this.electionStarted) {
			return;
		}
		this.electionStarted = true;
		this.isSuperNode = true;

		List<Thread> threads = new ArrayList<Thread>();
		//send election message for all others node with higher id 
		for (final String nodeIp : networkNodesIps.keySet()) {
			if (this.nodeId.compareTo(networkNodesIps.get(nodeIp)) < 0) {
				Thread thread = new Thread(new Runnable() {

					@Override
					public void run() {
						sendElectionMessage(nodeIp);

					}
				});

				threads.add(thread);
				thread.start();
			}

		}

		// wait all thread to finish
		for (Thread thread : threads) {
			try {
				thread.join();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}

		if (this.isSuperNode) {
			// Send coordination message for all other nodes
			for (String nodeIp : networkNodesIps.keySet()) {

				sendCoordinationMessage(nodeIp);
			}
          this.superNodeIp=this.getIp();//assign the super node ip 
		}

		this.electionStarted = false;

	}

	/**
	 * Send an election message for node with higher id
	 * 
	 * @param recieverNodeIp
	 */
	private void sendElectionMessage(String recieverNodeIp) {
		String url = getNodeUrl(recieverNodeIp);
		String methodName = NODE_HANDLE_NEW_ELECTION_MESSAGE_METHOD_NAME;
		Vector<String> params = new Vector<String>();
		params.addElement(this.getIp());
		Object result = sendRequest(url, methodName, params);
      //when it receive ok that's mean there is another node taking care of the election 
		if (ELECTION_OK_MESSAGE.equals(result)) {
			this.isSuperNode = false;
		}

	}

	/**
	 * Handle new election message from another node
	 * 
	 * @param senderNodeIp
	 * @return
	 */
	public String handleNewElectionMessage(String senderNodeIp,
			String senderLogicalClock) {

		updateLogicalClockOnReceive(senderLogicalClock);
		System.out.println("Receving a new election message from  ip:"
				+ senderNodeIp);
        //start the election 
		Thread thread = new Thread(new Runnable() {
			public void run() {
				startElection();
			}
		});

		thread.start();
		return ELECTION_OK_MESSAGE;

	}

	/**
	 * Send coordination message for some node
	 * 
	 * @param recieverNodeIp
	 */
	private void sendCoordinationMessage(final String recieverNodeIp) {

		Thread thread = new Thread(new Runnable() {
			public void run() {

				String url = getNodeUrl(recieverNodeIp);
				String methodName = NODE_HANDLE_NEW_COORDINATION_MESSAGE_METHOD_NAME;
				Vector<String> params = new Vector<String>();
				params.addElement(Node.this.getIp());
				sendRequest(url, methodName, params);
			}
		});
		thread.start();

	}

	/**
	 * Handle new coordination message
	 * 
	 * @param coordinatorIp
	 * @param senderLogicalClock
	 * @return
	 */
	public boolean handleNewCoordinationMessage(String coordinatorIp,
			String senderLogicalClock) {
		updateLogicalClockOnReceive(senderLogicalClock);
		System.out.println("**** Receving a new coordination from  ip:"
				+ coordinatorIp);
		// set the super node ip
		this.superNodeIp = coordinatorIp;
		this.isSuperNode = false;
		return true;

	}

	// /////////////////////////////////////////////
	// /
	// / Mutual Exclusion section
	// /
	// //////////////////////////////////////////////

	/**
	 * Send an access request to use certain resource
	 * 
	 * @param algorithm
	 * @param resourceId
	 */
	public void accessRequest(MutualExclusionAlgorithm algorithm,
			String resourceId) {

		switch (algorithm) {
		case Ricart:
			richardAcessRequest(resourceId);
			break;

		case Central:		
			centralAccessRequest(resourceId);
			break;

		default:
			break;
		}

	}
	
	/***
	 * Central Mutual Exclusion 
	 * @param resourceId
	 */
	private void centralAccessRequest(String resourceId) {

		if (this.wantToAccessResource || curentllyAccessingTheResource)
			return;
		wantToAccessResource=true;
		curentllyAccessingTheResource=false;
		//send access request to the master node 
		final String methodName = NODE_HANDLE_NEW_ACCESS_REQUEST_METHOD_NAME;
		final String centeral = MutualExclusionAlgorithm.Central.name();
		String url = getNodeUrl(this.superNodeIp);//super node IP
		Vector<String> params = new Vector<String>();
		params.add(centeral);
		params.add(Node.this.getIp());
		params.add(getExtentLogicalClock());
		params.add(resourceId);
		Object result = XMLRpcClientUtil.sendXMLRpcRequest(url,
				methodName, params);		
		// save that allowance from receiver node
		if (ACCESS_OK_MESSAGE.equals(result)) {
			handleAllowAccessResponse(centeral, this.superNodeIp, resourceId);			
		}
		// if the super node crashed or doesn't response for the request we
		// start the election process
		else if (result == null) {
			System.out.print("***** It seems the super node hase crashed we will start election!");
			startElection();
			wantToAccessResource=false;

		}	
	}

	/**
	 * Ricart Algorithm
	 * 
	 * @param resourceId
	 */
	private void richardAcessRequest(final String resourceId) {

		if (this.wantToAccessResource || curentllyAccessingTheResource)
			return;
		networkNodesIpsAndAccessRequestResponse = new HashMap<String, Boolean>();
		this.wantToAccessResource = true;
		this.curentllyAccessingTheResource = false;
		accessRequestLogicalClock = getExtentLogicalClock();
		final String methodName = NODE_HANDLE_NEW_ACCESS_REQUEST_METHOD_NAME;
		final String richard = MutualExclusionAlgorithm.Ricart.name();
		// send access request for all other nodes
		for (final String recieverNodeIp : networkNodesIps.keySet()) {
			Thread thread = new Thread(new Runnable() {
				public void run() {
					String url = getNodeUrl(recieverNodeIp);

					Vector<String> params = new Vector<String>();
					params.add(richard);
					params.add(Node.this.getIp());
					params.add(accessRequestLogicalClock);
					params.add(resourceId);
					Object result = XMLRpcClientUtil.sendXMLRpcRequest(url,
							methodName, params);
					// save that allowance from receiver node   when the result = null that's mean the node was crashed.
					if (ACCESS_OK_MESSAGE.equals(result) || result == null) {
						handleAllowAccessResponse(richard, recieverNodeIp,
								resourceId);
					}

					// we remove the node that crashed to avoid sending a future 
					// access request for it
					if (result == null) {
						Node.this.networkNodesIps.remove(recieverNodeIp);

					}

				}
			});
			thread.start();
		}
	}

	/**
	 * Handle new access request
	 * 
	 * @param mutualExclusionAlgorithm
	 * @param senderNodeIp
	 * @param senderLC
	 * @param resourceId
	 * @return
	 */
	public String handleNewAccessRequest(String mutualExclusionAlgorithm,
			String senderNodeIp, String senderLC, String resourceId) {

		updateLogicalClockOnReceive(senderLC);
		System.out.println("Receving a new acess request from  ip:"+ senderNodeIp);
		switch (MutualExclusionAlgorithm.valueOf(mutualExclusionAlgorithm)) {
		case Ricart:
			return handleRicartAccessRequest(senderNodeIp, senderLC, resourceId);		
		case Central:		
			return handleCentralAccessRequest(senderNodeIp, senderLC, resourceId);
		default:
			break;
		}

		return null;
	}

	/***
	 * Handle new Ricart access request to certain resource
	 * 
	 * @param senderNodeIp
	 * @param senderLC
	 * @param resourceId
	 * @return
	 */
	private String handleRicartAccessRequest(String senderNodeIp,
			String senderLC, String resourceId) {
		// if (not accessing resource and do not want to access it)
		if (!wantToAccessResource && !curentllyAccessingTheResource) {
			return ACCESS_OK_MESSAGE;
		}
		// (currently using resource)
		else if (curentllyAccessingTheResource) {
			this.accessRequestQueue.add(senderNodeIp);

		} else if (wantToAccessResource) {
			// time stamp of request is smaller
			if (NodeUtils.compareExtentLogicalClock(senderLC,
					this.accessRequestLogicalClock) < 0) {
				return ACCESS_OK_MESSAGE;
			} else {
				// own timestamp is smaller
				this.accessRequestQueue.add(senderNodeIp);

			}
		}
		return NOT_OK_MESSAGE;
	}

	
	/***
	 * Handle new Central access request to certain resource
	 * 
	 * @param senderNodeIp
	 * @param senderLC
	 * @param resourceId
	 * @return
	 */
	private String handleCentralAccessRequest(String senderNodeIp,
			String senderLC, String resourceId) {
	updateLogicalClockOnReceive(senderLC);
		String result= NOT_OK_MESSAGE;
		//to avoid getting the access by multiple node, if the two request received at the same time 
		synchronized (accessRequestQueueeslock) {		
			if(this.accessRequestQueue.size()==0){
			result= ACCESS_OK_MESSAGE;
			}
		//add the access request to the queue  		
		this.accessRequestQueue.add(senderNodeIp);			
		}		
		return result;
	}
	/**
	 * Handle the response of the access request
	 * 
	 * @param mutualExclusionAlgorithm
	 * @param senderIp
	 * @param resourceId
	 */
	public boolean handleAllowAccessResponse(String mutualExclusionAlgorithm,
			String senderIp, String resourceId) {
		System.out.println("**** Receving a new allow acess request from  ip:"
				+ senderIp);
		switch (MutualExclusionAlgorithm.valueOf(mutualExclusionAlgorithm)) {
		case Ricart:
			handleRicartAccessRequestResponse(senderIp, resourceId);
			break;	
		case Central:
			handleCentralAccessRequestResponse(senderIp, resourceId);
			break;
		default:
			break;
		}

		return true;
	}

	

	/**
	 * Handle the response of the access request in Ricart
	 * 
	 * @param senderIp
	 * @param resourceId
	 */
	private void handleRicartAccessRequestResponse(String senderIp,
			String resourceId) {
		boolean receiveOkFromAllNodes = false;
		synchronized (accessResponseslock) {
			this.networkNodesIpsAndAccessRequestResponse.put(senderIp, true);
			for (String nodeIP : networkNodesIps.keySet()) {
				receiveOkFromAllNodes = true;
				Boolean nodeResponse = this.networkNodesIpsAndAccessRequestResponse
						.get(nodeIP);
				if (nodeResponse == null || !nodeResponse) {
					receiveOkFromAllNodes = false;
					break;
				}
			}
		}

		if (receiveOkFromAllNodes) {
			// after getting ok from all nodes
			accessResource(MutualExclusionAlgorithm.Ricart.name(), resourceId);
		}

	}

	/***
	 *  Handle the response of the access request in Central
	 * @param senderIp
	 * @param resourceId
	 */
	private void handleCentralAccessRequestResponse(String senderIp,
			String resourceId) {	
		accessResource(MutualExclusionAlgorithm.Central.name(), resourceId);	
	}
	/**
	 * Access certain resource after getting the permission
	 * 
	 * @param resourceId
	 */
	private void accessResource(String mutualExclusionAlgorithm,
			String resourceId) {
		this.curentllyAccessingTheResource = true;
		System.out.println("Started Access on resource " + resourceId);
		readWriteOnResource(resourceId);
		System.out.println("Finished  Access on resource " + resourceId);
		sendReleaseMessage(mutualExclusionAlgorithm, resourceId);

	}

	/**
	 * a)Read the string variable from the master node c) Append some random
	 * English word to this string d) Write the updated string to the master
	 * node
	 * 
	 * @param resourceId
	 */
	private void readWriteOnResource(String resourceId) {

		// read resource from super node
		String superNodeSharedString = readResourceFromSuperNode(resourceId);
		// in the cases of master node has been crashed
		if (superNodeSharedString == null)
			return;
		String randomEnglishStringToAppend = getRandomString();
		this.writesOnMasterNodeSharedString.add(randomEnglishStringToAppend);
		//append the random string to the shared string 
		superNodeSharedString += randomEnglishStringToAppend;
		// write the updated string on the master node
		updateResourceOnSuperNode(resourceId, superNodeSharedString);
	}

	/***
	 * Get Random String
	 * 
	 * @return
	 */
	private String getRandomString() {
		return  this.nodeId +NodeUtils.generateRandomString();
	}

	/**
	 * Read resource from super node
	 * 
	 * @param resourceId
	 */
	private String readResourceFromSuperNode(String resourceId) {
		String url = getNodeUrl(this.superNodeIp);
		String methodName = "node.readResource";
		Vector<String> params = new Vector<String>();
		params.addElement(this.getIp());
		params.addElement(resourceId);
		Object result = sendRequest(url, methodName, params);
		// if the super node crashed or doesn't response for the request we
		// start the election process
		if (result == null) {
			System.out
					.println("***** It seems the super node hase crashed I will start election!");
			startElection();
			return null;
		}

		System.out.println("Read resource from super node  result:" + result);

		return (String) result;
	}

	/**
	 * Read operation on certain resource
	 * 
	 * @param senderIp
	 * @param resourceId
	 * @param senderLogicalClock
	 * @return
	 */
	public String readResource(String senderIp, String resourceId,
			String senderLogicalClock) {

		updateLogicalClockOnReceive(senderLogicalClock);

		System.out.println("Receive a read operation on " + resourceId
				+ "from node:" + senderIp);

		return this.sharedString;
	}

	/**
	 * Update resource on the super node
	 * 
	 * @param resourceId
	 */
	private boolean updateResourceOnSuperNode(String resourceId, String newValue) {
		String url = getNodeUrl(this.superNodeIp);
		String methodName = NODE_UPDATE_RESOURCE_METHOD_NAME;
		Vector<String> params = new Vector<String>();
		params.addElement(this.getIp());
		params.addElement(resourceId);
		params.addElement(newValue);
		Object result = sendRequest(url, methodName, params);
		// if the super node crashed or doesn't response for the request we
		// start the election process
		if (result == null) {
			System.out
					.println("***** It seems the super node hase crashed we will start election!");
			startElection();
			return false;
		}

		System.out.println("Read resource from super node  result:" + result);

		return (boolean) result;
	}

	/**
	 * Write/update operation on certain resource
	 * 
	 * @param senderIp
	 * @param resourceId
	 * @param senderLogicalClock
	 * @return
	 */
	public boolean updateResource(String senderIp, String resourceId,
			String newResourceValue, String senderLogicalClock) {

		updateLogicalClockOnReceive(senderLogicalClock);

		System.out.println("Receive a update operation on " + resourceId
				+ "from node:" + senderIp);

		this.sharedString = newResourceValue;

		return true;
	}

	/**
	 * Send a release message after finishing the access on the resource
	 * 
	 * @param mutualExclusionAlgorithm
	 * @param resourceId
	 */
	private void sendReleaseMessage(String mutualExclusionAlgorithm,
			String resourceId) {

		switch (MutualExclusionAlgorithm.valueOf(mutualExclusionAlgorithm)) {
		case Ricart:
			sendRicartReleaseMessage(resourceId);
			break;
		case Central:
	  sendCentralReleaseMessage(resourceId);
			break;
		default:
			break;
		}

		this.wantToAccessResource = false;
		this.curentllyAccessingTheResource = false;
	}

	

	/**
	 * Send a release message for the all queued access requests
	 * 
	 * @param resourceId
	 */
	private void sendRicartReleaseMessage(final String resourceId) {
		// send OK to all processes in queue
		final String methodName = NODE_HANDLE_ALLOW_ACCESS_RESPONSE_METHOD_NAME;
		final String richard = MutualExclusionAlgorithm.Ricart.name();
		// send release for all other nodes in the access request queue
		for (final String recieverNodeIp : accessRequestQueue) {
			Thread thread = new Thread(new Runnable() {
				public void run() {
					String url = getNodeUrl(recieverNodeIp);

					Vector<String> params = new Vector<String>();
					params.add(richard);
					params.add(Node.this.getIp());
					params.add(resourceId);
					XMLRpcClientUtil.sendXMLRpcRequest(url, methodName, params);
				}
			});
			thread.start();
		}

		// empty queue
		accessRequestQueue = new ArrayList<String>();

	}

	/**
	 * Send a release message for the the master node 
	 * @param resourceId
	 */
	private void sendCentralReleaseMessage(String resourceId) {
		
		// send a release to the super node 
		final String methodName = NODE_HANDLE_RELEASE_RESOURCE_METHOD_NAME;
		final String central = MutualExclusionAlgorithm.Central.name();
		String url = getNodeUrl(this.superNodeIp);//super node IP
		Vector<String> params = new Vector<String>();
		params.add(central);
		params.add(Node.this.getIp());
		params.add(resourceId);
		sendRequest(url, methodName, params);
	}
	
	/**
	 * Handle the release message 
	 * 
	 * @param mutualExclusionAlgorithm
	 * @param senderIp
	 * @param resourceId
	 */
	public boolean handleReleaseResource(String mutualExclusionAlgorithm,
			String senderIp, String resourceId,String senderLogicalClock) {
		System.out.println("Receving realease message from  ip:"
				+ senderIp);
		switch (MutualExclusionAlgorithm.valueOf(mutualExclusionAlgorithm)) {
			
		case Central:
			handleCentralRelease(senderIp, resourceId,senderLogicalClock);
			break;
		default:
			break;
		}

		return true;
	}
	
	/**
	 * Handle release message in central
	 * @param senderIp
	 * @param resourceId
	 */
	private void handleCentralRelease(String senderIp, final String resourceId,
			String senderLogicalClock) {
		updateLogicalClockOnReceive(senderLogicalClock);

		// to avoid getting the access by multiple node, if the two request
		// received at the same time
		synchronized (accessRequestQueueeslock) {
			//remove the the released access request from the queue 
			this.accessRequestQueue.remove(senderIp);
			if (this.accessRequestQueue.size() > 0) {
				final String newGrantedNode = this.accessRequestQueue.get(0);
				final String methodName = NODE_HANDLE_ALLOW_ACCESS_RESPONSE_METHOD_NAME;
				final String central = MutualExclusionAlgorithm.Central.name();
				// send ok message for the granted node to access the resource 
				Thread thread = new Thread(new Runnable() {
					public void run() {
						String url = getNodeUrl(newGrantedNode);
						Vector<String> params = new Vector<String>();
						params.add(central);
						params.add(Node.this.getIp());
						params.add(resourceId);
						XMLRpcClientUtil.sendXMLRpcRequest(url, methodName,
								params);
					}
				});
				thread.start();
			}
		}

	}

	/**
	 * Start a distributed read / write process by sending a start message to
	 * all the other nodes in the network.
	 */
	public void startDistributedReadWrite(
			MutualExclusionAlgorithm mutualExclusionAlgorithm, String resourceId) {

		// send start read/write for the all other nodes
		for (String nodeIp : networkNodesIps.keySet()) {
			sendStartMessage(nodeIp, mutualExclusionAlgorithm, resourceId);
		}

		// also start on the current node
		start(mutualExclusionAlgorithm.name(), resourceId,
				getExtentLogicalClock());
	}

	/**
	 * Send start message for certain node
	 * 
	 * @param recieverNodeIp
	 */
	private void sendStartMessage(final String recieverNodeIp,
			final MutualExclusionAlgorithm mutualExclusionAlgorithm,
			final String resourceId) {

		Thread thread = new Thread(new Runnable() {
			public void run() {
				String url = getNodeUrl(recieverNodeIp);
				String methodName = NODE_START_METHOD_NAME;
				Vector<String> params = new Vector<String>();
				params.add(mutualExclusionAlgorithm.name());
				params.add(resourceId);
				sendRequest(url, methodName, params);
			}
		});

		thread.start();

	}

	/***
	 * Start read write on master node shared string
	 * 
	 * @param mutualExclusionAlgorithm
	 * @param resourceId
	 * @param senderLogicalClock
	 * @return
	 */
	public boolean start(final String mutualExclusionAlgorithm,
			final String resourceId, String senderLogicalClock) {
		updateLogicalClockOnReceive(senderLogicalClock);

		// we create a thread to start the read/write and to don't block the
		// replay to the sender node
		Thread thread = new Thread(new Runnable() {
			public void run() {
				long startTime = System.currentTimeMillis();

				while (System.currentTimeMillis() - startTime <= READ_WRITE_PERIOD_MS) {
					long randomWaitTime = (long) (Math.random() * RANDOM_WAIT_THREASHOLD_MS);
					try {
						Thread.sleep(randomWaitTime);
					} catch (InterruptedException e) {
					}

					if (System.currentTimeMillis() - startTime <= READ_WRITE_PERIOD_MS) {
						accessRequest(MutualExclusionAlgorithm
								.valueOf(mutualExclusionAlgorithm), resourceId);
					} else {
						break;
					}
				}

				checkSharedStringValue(resourceId);
			}

		});

		thread.start();

		return true;
	}

	/**
	 * Read the final string from the master node and write it to the screen.
	 * Moreover they check if all the words they added to the string are present
	 * in the final string. The result of this check is also written to the
	 * screen
	 * 
	 * @param resourceId
	 */
	private void checkSharedStringValue(String resourceId) {
		// in the case of pending access request we will wait them to be
		// finished
		while (wantToAccessResource || curentllyAccessingTheResource) {
			try {
				Thread.sleep(500);
			} catch (InterruptedException e) {
			}
		}

		String superNodeSharedString = readResourceFromSuperNode(resourceId);

		System.out.println("The shared string on the master node= "
				+ superNodeSharedString);

		int failedWritesCount = 0;
		// check if all write operations are in the shared string
		for (String randomString : this.writesOnMasterNodeSharedString) {

			if (!superNodeSharedString.contains(randomString)) {
				failedWritesCount++;
				System.out.println("Failed Write:" + randomString);
			}
		}

		System.out.println("**** " + failedWritesCount + " of "
				+ this.writesOnMasterNodeSharedString.size() + " were failed!");

	};
}
