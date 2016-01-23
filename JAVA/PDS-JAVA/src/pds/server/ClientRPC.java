package pds.server;

import java.util.*;
import org.apache.xmlrpc.*;

public class ClientRPC {
   public static void main (String [] args) {
   
      try {
         XmlRpcClient server = new XmlRpcClient("http://localhost:36/blogdemo/blogger"); 
         Vector params = new Vector();
         
       //  params.addElement(new Integer(27));
     //    params.addElement(new Integer(13));

         Object result = server.execute("sample.sum", params);

       //  int sum = ((Integer) result).intValue();
         System.out.println("The sum is: "+ result);

      } catch (Exception exception) {
         System.err.println("JavaClient: " + exception);
      }
   }
}