
using System;
using System.Collections;

namespace PDS
{
    class NodeUtils
    {
        /// <summary>
        ///  Compare two extent logical clocks
        /// </summary>
        /// <param name="ELC1"></param>
        /// <param name="ELC2"></param>
        /// <returns></returns>
        public static  int compareExtentLogicalClock(String ELC1, String ELC2)
        {
           
            //split the extend Lamport logical clock string 
            String[] LC1 = ELC1.Split(Node.LOGICAL_CLOCK_AND_NODE_ID_SEPAROTR);
            int logicalClock1 = int.Parse(LC1[0]);
            int computerId1 = int.Parse(LC1[1]);

            String[] LC2 = ELC2.Split(Node.LOGICAL_CLOCK_AND_NODE_ID_SEPAROTR);
            int logicalClock2 = int.Parse(LC2[0]);
            int computerId2 = int.Parse(LC2[1]);

            //extent Lamport logical clock comparesion 
            if (logicalClock1 < logicalClock2)
                return -1;
            else if (logicalClock1 > logicalClock2)
                return 1;
            else if (logicalClock1 == logicalClock2 && computerId1 < computerId2)
                return -1;

            else if (logicalClock1 == logicalClock2 && computerId1 > computerId2)
                return 1;

            return 0;

        }

        /// <summary>
        /// Parse the join request result that contain the network's node information
        /// </summary>
        /// <param name="ipListStr"></param>
        /// <returns></returns>
        public static Hashtable parseIpList(String ipListStr)
        {
            ipListStr = ipListStr.Replace("{", "");
            ipListStr = ipListStr.Replace("}", "");
            ipListStr = ipListStr.Trim();

            String[] ipsArray = ipListStr.Split(',');

            Hashtable ipList = new Hashtable();
            String[] ipAndId;
            for (int i = 0; i < ipsArray.Length; i++)
            {

                ipAndId = ipsArray[i].Split('=');
                ipList.Add(ipAndId[0].Trim(), ipAndId[1].Trim());

            }

            return ipList;

        }
    }
}
