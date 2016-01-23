package pds.xmlrpc.client;

import java.util.Vector;

import org.apache.xmlrpc.XmlRpcClient;

public class XMLRpcClientUtil {

	public static Object sendXMLRpcRequest(String url, String methodName,
			Vector params) {

		Object result = null;
		try {
			XmlRpcClient server = new XmlRpcClient(url);
			result = server.execute(methodName, params);
			System.out.println("The method:" + methodName + " returned :"
					+ result);
		} catch (Exception exception) {
			System.err.println("The method:" + methodName + ";url:" + url
					+ ";JavaClient: " + exception.getMessage());
		}

		return result;
	}

}
