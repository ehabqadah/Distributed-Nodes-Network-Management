package pds.xmlrpc.client;

import java.util.Vector;

import org.apache.xmlrpc.XmlRpcClient;

/**
 * This class responsible for the XML-RPC calls 
 * 
 *
 */
public class XMLRpcClientUtil {

	/***
	 * Send XML-RPC call
	 * @param url
	 * @param methodName
	 * @param params
	 * @return  null in the case of any connection error 
	 */
	public static Object sendXMLRpcRequest(String url, String methodName,
			Vector<String> params) {

		Object result = null;
		try {
			XmlRpcClient server = new XmlRpcClient(url);
			result = server.execute(methodName, params);
			System.out.println("The method:" + methodName + " returned:"+ result +"  from:"+url);
		} catch (Exception exception) {
			System.err.println("****** The method:" + methodName + ";url:" + url
					+ ";JavaClient: " + exception.getMessage());
			return null;
		}

		return result;
	}

}
