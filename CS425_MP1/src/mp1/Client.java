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
   	private Socket[] client = {null,null,null,null};
   	BufferedReader sysIn;
   	BufferedReader[] mtSysIn = {null,null,null,null};
   	
   	
   	
    public Client(NodeInfo[] allNodes, NodeInfo curNode) {
    	nodesInfo = allNodes;
    	myInfo = curNode;
    }
    
    
    public void run(){
    	
    	sysIn = new BufferedReader(new InputStreamReader(System.in));
    	
    	System.out.print("Identified the servers to connect to as: ");
    	for (int i=0; i<4; ++i) {
    		mtSysIn[i] = sysIn;
    		if (nodesInfo[i].id.compareTo(myInfo.id)!=0) {
    			System.out.print(nodesInfo[i].id+", ");    				
    		}
    	}
    	System.out.println(" ");
    	
    	for (int i=0; i<4; ++i) {
    		//Skip my own server
    		if (nodesInfo[i].id.compareTo(myInfo.id)==0) 
    			continue;
    		
    		//connect to 3 other servers one by one
    		serverPort = nodesInfo[i].port;
        	serverAddr = nodesInfo[i].ip;
        	while(client[i]==null) {
    	    	try {
    				client[i] = new Socket(serverAddr, serverPort);
    				System.out.println("Now connected to "+serverAddr+":"+serverPort);
                    MessageThread mt=new MessageThread(nodesInfo, myInfo, nodesInfo[i], client[i], mtSysIn[i]);
                    mt.start();
    					
    			} catch (IOException e) {
    				//System.out.println("Connect to server failed");
    				client[i]=null;
    			}
        	}
    	}
    	
		
	}
}
