package mp1;

import java.net.*;
import java.io.*;
import java.util.*;

/*
 * This class should be on a thread.
 * blahblahblah
 */
public class Client implements Runnable
{
	protected int    serverPort   = 7500;
    protected String serverAddr = "";
    
    private NodeInfo[] nodesInfo;
   	private NodeInfo myInfo;
   	private NodeInfo serverNode;
   	private Socket client = null;
   	
   	
   	
    public Client(NodeInfo[] allNodes, NodeInfo curNode, NodeInfo serverNode) {
    	nodesInfo = allNodes;
    	myInfo = curNode;
    	
    	//The info of the Node server to which this client would connect to
    	this.serverNode = serverNode;
    	
		System.out.println("Identified server to connect to as: "+serverNode.id);
    	serverPort = serverNode.port;
    	serverAddr = serverNode.ip;
    }
    
    
    public void run(){		
		
    	//new Thread(new MessageThread(), "Delayer._.").start();
    	
		//connect to 3 other servers constantly (change client to client array)
    	while(client==null) {
	    	try {
				client = new Socket(serverAddr, serverPort);
				System.out.println("Now connected to "+serverAddr+":"+serverPort);
                MessageThread mt=new MessageThread(nodesInfo, myInfo, serverNode, client);
                mt.start();
					
			} catch (IOException e) {
				//System.out.println("Connect to server failed");
				client=null;
			}
    	}
	}
}
