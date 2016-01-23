package pds.network;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import pds.xmlrpc.client.XMLRpcClientUtil;
import pds.xmlrpc.server.XMLRpcServerUtils;

public class Node {

	private static final String OK_MESSAGE = "ok i will take care of election";

	public Map<String, String> networkNodesIps = new HashMap<String, String>();

	public String ip;

	public String nodeId;

	public String superNodeIp;

	public boolean isSuperNode;

	protected boolean electionStarted;

	public Node(String id) {

		this.nodeId = id;
	}

	public boolean setupServerPart() {

		XMLRpcServerUtils.setupXmlRPCServer(2000, "node", this);

		return true;
	}

	public boolean join(String conectedNodeIp) {

		String url = getNodeUrl(conectedNodeIp);
		String methodName = "node.handleNewJoinRequest";
		Vector params = new Vector();
		params.addElement(this.GetIp());
		params.addElement(this.nodeId);
		Object result = XMLRpcClientUtil.sendXMLRpcRequest(url, methodName,
				params);
		System.out.print("join result" + result);
		// store network ip list
		this.networkNodesIps = parseIpList((String) result);
		return true;
	}

	private String getNodeUrl(String conectedNodeIp) {
		return "http://" + conectedNodeIp + ":2000/RPC2";
	}

	public String handleNewJoinRequest(String newNodeIp, String newNodeId) {

		System.out.println("receving a new join request ip:" + newNodeIp
				+ "; id =" + newNodeId);

		Map<String, String> oldNodesMap = new HashMap<String, String>(
				this.networkNodesIps);
		// add its own ip and id to the map
		oldNodesMap.put(this.GetIp(), this.nodeId);
		String oldNodes = oldNodesMap.toString();
		// forward the new ip for the all other nodes
		for (String nodeIp : networkNodesIps.keySet()) {
			forwardJoinRequest(nodeIp, newNodeIp, newNodeId);
		}
		// add the new ip
		if (!this.networkNodesIps.keySet().contains(newNodeIp))
			this.networkNodesIps.put(newNodeIp, newNodeId);
		return oldNodes;

	}

	private void forwardJoinRequest(final String recieverNodeIp,
			final String newNodeIp, final String newNodeId) {

		Thread thread = new Thread(new Runnable() {
			public void run() {
				String url = getNodeUrl(recieverNodeIp);
				String methodName = "node.handleForwardNewJoinRequest";
				Vector params = new Vector();
				params.addElement(newNodeIp);
				params.addElement(newNodeId);
				Object result = XMLRpcClientUtil.sendXMLRpcRequest(url,
						methodName, params);
			}
		});
		thread.start();

	}

	public boolean handleForwardNewJoinRequest(String newNodeIp,
			String newNodeId) {
		System.out.println("receving a forward join request ip:" + newNodeIp
				+ "; id =" + newNodeId);
		if (!this.networkNodesIps.keySet().contains(newNodeIp))
			this.networkNodesIps.put(newNodeIp, newNodeId);
		return true;
	}

	private Map<String, String> parseIpList(String ipListStr) {

		ipListStr = ipListStr.replace("{", "");
		ipListStr = ipListStr.replace("}", "");
		ipListStr = ipListStr.trim();

		String[] ipsArray = ipListStr.split(",");

		Map<String, String> ipList = new HashMap();
		String[] ipAndId;
		for (int i = 0; i < ipsArray.length; i++) {

			ipAndId = ipsArray[i].split("=");
			ipList.put(ipAndId[0].trim(), ipAndId[1].trim());

		}

		return ipList;

	}

	private String GetIp() {
		if (this.ip == null) {
			try {
				ip = InetAddress.getLocalHost().getHostAddress();
			} catch (UnknownHostException e) {
				// TODO: to check if this method fails
			}
		}

		return ip;
	}

	public boolean signOff() {
		// send sign off for the all other nodes
		for (String nodeIp : networkNodesIps.keySet()) {
			sendSignOffRequest(nodeIp);
		}
		return true;
	}

	private void sendSignOffRequest(final String recieverNodeIp) {

		Thread thread = new Thread(new Runnable() {
			public void run() {
				String url = getNodeUrl(recieverNodeIp);
				String methodName = "node.handleNewSignOffRequest";
				Vector params = new Vector();
				params.addElement(Node.this.GetIp());
				Object result = XMLRpcClientUtil.sendXMLRpcRequest(url,
						methodName, params);
			}
		});

		thread.start();

	}

	public boolean handleNewSignOffRequest(String signedOffNodeIp) {

		System.out.println("recieve sign off request from " + signedOffNodeIp);
		this.networkNodesIps.remove(signedOffNodeIp);

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
		String methodName = "node.handleNewElectionMessage";
		Vector params = new Vector();
		params.addElement(this.GetIp());
		Object result = XMLRpcClientUtil.sendXMLRpcRequest(url, methodName,
				params);

		if (OK_MESSAGE.equals(result)) {
			this.isSuperNode = false;
		}

	}

	public String handleNewElectionMessage(String senderNodeIp) {

		System.out.println("receving a new election message from  ip:"
				+ senderNodeIp);

		Thread thread = new Thread(new Runnable() {
			public void run() {
				startElection();
			}
		});

		thread.start();
		return OK_MESSAGE;

	}

	/**
	 * Send coordination message for node
	 * 
	 * @param recieverNodeIp
	 */
	private void sendCoordinationMessage(final String recieverNodeIp) {

		Thread thread = new Thread(new Runnable() {
			public void run() {

				String url = getNodeUrl(recieverNodeIp);
				String methodName = "node.handleNewCoordinationMessage";
				Vector params = new Vector();
				params.addElement(Node.this.GetIp());
				Object result = XMLRpcClientUtil.sendXMLRpcRequest(url,
						methodName, params);
			}
		});
		thread.start();

	}

	public boolean handleNewCoordinationMessage(String coordinatorIp) {

		System.out.println("receving a new coordination from  ip:"
				+ coordinatorIp);

		// set the super node ip
		this.superNodeIp = coordinatorIp;
		this.isSuperNode = false;
		return true;

	}
}
