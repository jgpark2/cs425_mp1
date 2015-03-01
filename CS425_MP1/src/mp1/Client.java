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
   	private Socket client;
   	
   	protected PrintWriter outs;
	protected BufferedReader ins;
	protected BufferedReader sysIn;
   	
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
		
		
		//connect to server of given addr and port
		try {
			client = new Socket(serverAddr, serverPort);
			outs = new PrintWriter(client.getOutputStream(), true);
			ins = new BufferedReader(new InputStreamReader(client.getInputStream()));
			sysIn = new BufferedReader(new InputStreamReader(System.in));
		} catch (IOException e) {
			System.out.println("Connect, in, out failed");
			return;
		}
		
		String msgInput, msgOutput;
		
		System.out.println("Now connected to "+serverAddr+":"+serverPort);
		
		//While the server keeps writing to us on its own output Stream...
		try {
			
			while((msgInput = ins.readLine())!=null) {
				System.out.println("Server: " + msgInput);
				
				if (msgInput.equals("Bye")) //Received disconnect Msg
					break;
				
				msgOutput = sysIn.readLine(); //Grab command line input
				System.out.println("Client: " + msgOutput);
				outs.println(msgOutput);
			}
			
			
		} catch (IOException e) {
			System.out.println("Socket reading error");
			e.printStackTrace();
		} finally{
			try {
				client.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
			try {
				ins.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
			outs.close();
			try {
				sysIn.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
			System.out.println("Connection Closed");
	    }
	}
}
