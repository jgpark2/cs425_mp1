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
   	
    public Client(NodeInfo[] allNodes, NodeInfo curNode, NodeInfo serverNode) {
    	nodesInfo = allNodes;
    	myInfo = curNode;
    	
    	//The info of the Node server to which this client would connect to
    	this.serverNode = serverNode;
    	
    	System.out.println("Identified server to connect to as: "+serverNode.id);
    	serverPort = serverNode.port;
    	serverAddr = serverNode.ip;
    }
    
    
    public void run() {		
		PrintWriter out;
		BufferedReader in;
		
		//connect to server of given addr and port
		try {
			client = new Socket(serverAddr, serverPort);
			out = new PrintWriter(client.getOutputStream(), true);
			in = new BufferedReader(new InputStreamReader(client.getInputStream()));
		} catch (IOException e) {
			System.out.println("Connect, in, out failed");
			System.exit(-1);
			return;
		}
		
		String msgInput, msgOutput;
		Scanner sc = new Scanner(System.in);
		
		System.out.println("Now connected to "+serverAddr+":"+serverPort);
		
		//While the server keeps writing to us on its own output Stream...
		try {
			
			
			while((msgInput = in.readLine())!=null) {
				System.out.println("Server: " + msgInput);
				
				if (msgInput.equals("Bye")) //Recevied disconnect Msg
					break;
				
				msgOutput = sc.next();
				System.out.println("Client: " + msgOutput);
				out.println(msgOutput);
			}
			
			
			
			
			client.close();
			
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
