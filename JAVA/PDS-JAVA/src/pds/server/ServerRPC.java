package pds.server;
import java.util.*;
import org.apache.xmlrpc.*;
public class ServerRPC {

	

		   public String sum(){
		      return "sergio";}
		   
	public static void main(String[] args) {
		try {

	         System.out.println("Attempting to start XML-RPC Server...");
	         
	         WebServer server = new WebServer(36);
	         server.addHandler("sample", new ServerRPC());
	         server.start();
	         
	         System.out.println("Started successfully.");
	         System.out.println("Accepting requests. (Halt program to stop.)");
	         
	      } catch (Exception exception){
	         System.err.println("JavaServer: " + exception);
	      }
	   }

	

}
