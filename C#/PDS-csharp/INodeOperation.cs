using System.ServiceModel;
namespace PDS
{ /// <summary>
  /// 
  /// </summary>
    [ServiceContract()]
    interface INodeOperation
    {

        /// <summary>
        /// 
        /// </summary>
        /// <returns></returns>

        [OperationContract(Action = "node.handleNewJoinRequest")]
        string handleNewJoinRequest(string newNodeIp, string newNodeId, string senderLogicalClock);



        /// <summary>
        /// 
        /// </summary>
        /// <returns></returns>

        [OperationContract(Action = "node.handleForwardNewJoinRequest")]
        bool handleForwardNewJoinRequest(string newNodeIp, string newNodeId, string senderLogicalClock);



        /// <summary>
        /// 
        /// </summary>
        /// <returns></returns>

        [OperationContract(Action = "node.handleNewSignOffRequest")]
        bool handleNewSignOffRequest(string ip, string senderLogicalClock);





        /// <summary>
        /// 
        /// </summary>
        /// <returns></returns>

        [OperationContract(Action = "node.handleNewElectionMessage")]
        string handleNewElectionMessage(string senderNodeIp, string senderLogicalClock);




        /// <summary>
        /// 
        /// </summary>
        /// <returns></returns>

        [OperationContract(Action = "node.handleNewCoordinationMessage")]
        bool handleNewCoordinationMessage(string coordinadorIp, string senderLogicalClock);

        /// <summary>
        /// 
        /// </summary>
        /// <param name="mutualExclusionAlgorithm"></param>
        /// <param name="resourceId"></param>
        /// <param name="senderLogicalClock"></param>
        /// <returns></returns>
        [OperationContract(Action = "node.start")]
        bool start(string mutualExclusionAlgorithm, string resourceId, string senderLogicalClock);
        /// <summary>
        /// 
        /// </summary>
        /// <param name="mutualExclusionAlgorithm"></param>
        /// <param name="senderNodeIp"></param>
        /// <param name="senderLC"></param>
        /// <param name="resourceId"></param>
        /// <returns></returns>
        [OperationContract(Action = "node.handleNewAccessRequest")]
        string handleNewAccessRequest(string mutualExclusionAlgorithm,
               string senderNodeIp, string senderLC, string resourceId);
        /// <summary>
        /// 
        /// </summary>
        /// <param name="mutualExclusionAlgorithm"></param>
        /// <param name="senderIp"></param>
        /// <param name="resourceId"></param>
        /// <returns></returns>
        [OperationContract(Action = "node.handleAllowAccessResponse")]
        bool handleAllowAccessResponse(string mutualExclusionAlgorithm, string senderIp, string resourceId);

        /// <summary>
        /// 
        /// </summary>
        /// <param name="senderIp"></param>
        /// <param name="resourceId"></param>
        /// <param name="senderLogicalClock"></param>
        /// <returns></returns>
        [OperationContract(Action = "node.readResource")]
        string readResource(string senderIp, string resourceId,
                string senderLogicalClock);

        /// <summary>
        /// 
        /// </summary>
        /// <param name="senderIp"></param>
        /// <param name="resourceId"></param>
        /// <param name="newResourceValue"></param>
        /// <param name="senderLogicalClock"></param>
        /// <returns></returns>
        [OperationContract(Action = "node.updateResource")]
        bool updateResource(string senderIp, string resourceId,
               string newResourceValue, string senderLogicalClock);



        /// <summary>
        /// 
        /// </summary>
        /// <param name="mutualExclusionAlgorithm"></param>
        /// <param name="senderIp"></param>
        /// <param name="resourceId"></param>
        /// <param name="senderLogicalClock"></param>
        /// <returns></returns>
             [OperationContract(Action = "node.handleReleaseResource")]
             bool handleReleaseResource(string mutualExclusionAlgorithm,
            string senderIp, string resourceId, string senderLogicalClock);
    }
}
