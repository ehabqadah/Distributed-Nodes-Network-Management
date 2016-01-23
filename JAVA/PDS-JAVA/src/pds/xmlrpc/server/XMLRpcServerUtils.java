package pds.xmlrpc.server;

import org.apache.xmlrpc.WebServer;

import pds.server.ServerRPC;

public class XMLRpcServerUtils {

	
	
	public static WebServer setupXmlRPCServer(int portNumber,
			String handlerName, Object serverHandler){
		 WebServer server=null;
		try {
         System.out.println("Attempting to start XML-RPC Server...");      
	          server = new WebServer(portNumber);
	         server.addHandler(handlerName, serverHandler);
	         server.start();    
	         System.out.println(handlerName+" started successfully.");
	         System.out.println("Accepting requests. (Halt program to stop.)");	         
	      } catch (Exception exception){
	         System.err.println("JavaServer: " + exception);
	      }
		return server;
	}
}
