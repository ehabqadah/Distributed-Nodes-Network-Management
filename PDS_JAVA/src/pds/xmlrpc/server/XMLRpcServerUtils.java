package pds.xmlrpc.server;

import org.apache.xmlrpc.WebServer;
/**
 * This class responsible for receiving XML-RPC calls
 * 
 *
 */
public class XMLRpcServerUtils {

	
	/**
	 * Setup the XML-RPC receiving part 
	 * @param portNumber
	 * @param handlerName
	 * @param serverHandler
	 * @return
	 */
	public static WebServer setupXmlRPCServer(int portNumber,
			String handlerName, Object serverHandler){
		 WebServer server=null;
		try {
         System.out.println("Attempting to start XML-RPC Server...");      
	          server = new WebServer(portNumber);
	         server.addHandler(handlerName, serverHandler);
	         server.start();    
	         System.out.println(handlerName+" started successfully.");
	      } catch (Exception exception){
	         System.err.println("******* JavaServer error: " + exception);
	      }
		return server;
	}
}
