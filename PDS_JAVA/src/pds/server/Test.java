package pds.server;

import java.io.Console;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import pds.network.Node;

public class Test {

	public static void main(String[] args) {
		
		
		 Map<String,String> networkNodesIps = new HashMap<String,String>();
		 
		 networkNodesIps.put("key1","value1");
		 networkNodesIps.put("key2","value2");
		 networkNodesIps.put("key3","value3");

		 System.out.println(networkNodesIps.toString());
		
		Node node = new Node("1");
		
		node.setupServerPart();
		
	}

}
