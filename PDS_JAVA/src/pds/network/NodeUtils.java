package pds.network;

import java.util.HashMap;
import java.util.Map;

public class NodeUtils {

	/**
	 * Compare two extent Lamport logical clocks
	 * 
	 * @param ELC1
	 * @param ELC2
	 * @return
	 */
	public static int compareExtentLogicalClock(String ELC1, String ELC2) {
//split the  logical clock string that contains the LC and node id 
		String[] LC1 = ELC1.split(Node.LOGICAL_CLOCK_AND_NODE_ID_SEPAROTR);
		int logicalClock1 = Integer.parseInt(LC1[0]);
		int computerId1 = Integer.parseInt(LC1[1]);

		String[] LC2 = ELC2.split(Node.LOGICAL_CLOCK_AND_NODE_ID_SEPAROTR);
		int logicalClock2 = Integer.parseInt(LC2[0]);
		int computerId2 = Integer.parseInt(LC2[1]);

		if (logicalClock1 < logicalClock2)
			return -1;
		else if (logicalClock1 > logicalClock2)
			return 1;
		// extent Lamport logical clock 
		else if (logicalClock1 == logicalClock2 && computerId1 < computerId2)
			return -1;

		else if (logicalClock1 == logicalClock2 && computerId1 > computerId2)
			return 1;

		return 0;

	}

	/**
	 * This method parse the join result that contains the network nodes ip and
	 * ids
	 * 
	 * @param ipListStr
	 */
	public static  Map<String, String> parseIpList(String ipListStr) {

		ipListStr = ipListStr.replace("{", "");
		ipListStr = ipListStr.replace("}", "");
		ipListStr = ipListStr.trim();

		String[] ipsArray = ipListStr.split(",");

		Map<String, String> ipList = new HashMap<String, String>();
		String[] ipAndId;
		for (int i = 0; i < ipsArray.length; i++) {

			ipAndId = ipsArray[i].split("=");
			ipList.put(ipAndId[0].trim(), ipAndId[1].trim());

		}

		return ipList;

	}
	
	
	/****
	 * Generate random string 
	 * @return
	 */
	public static String generateRandomString() {
		int length= (int)(Math.random()*5+ Math.random()*3 +Math.random()*6)+2;
		StringBuffer buffer = new StringBuffer("_"+(int)(Math.random()*100)+"_");
		String characters = "$$$abcdefghijklmnopqrstuvwxyz@#$%^&&%*ABCDEFGHIJKLMNOPQRS#@%&TUVWXYZ1234567890";		
		int charactersLength = characters.length();
		for (int i = 0; i < length; i++) {
			double index = Math.random() * charactersLength;
			buffer.append(characters.charAt((int) index));
		}
		
		return buffer.toString();
	}
}
