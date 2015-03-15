package mp1_1;

import java.net.*;
import java.io.*;
import java.util.Date;
import java.text.SimpleDateFormat;

/*
 * ServerThread.java
 * This class should be on a thread.
 * This thread listens and receives messages through the sockets defined at the start
 * (port #). 
 */
 
 
public class ServerThread extends Thread
{

	protected int          serverPort   = 7500;
    protected ServerSocket serverSocket = null;
    protected Socket		clientSocket = null;
    protected String		clientId = "";
    
    protected boolean      isStopped    = false;
    
    protected Thread       runningThread= null;
    
    protected BufferedReader ins = null;
    protected PrintWriter outs = null;
    
    
	//run the server thread
	//have public methods such as setting up on a certain port/ip
	//it will call methods in ServerThreads to convey messages
	
    private NodeInfo[] nodesInfo;
	private NodeInfo myInfo;
	
    public ServerThread(NodeInfo[] allNodes, NodeInfo curNode, Socket socket) { 
        //this.m = m1;
        //this.serverPort = port;
    	nodesInfo = allNodes;
    	myInfo = curNode;
    	
    	serverPort = myInfo.port;
    	
    	clientSocket = socket;
        /*try {
        	localHost = InetAddress.getLocalHost();
		} catch (UnknownHostException uhe) {
			System.out.println("Problems identifying local host");
			uhe.printStackTrace();  System.exit(1);
		}*/
    }

    public void run() {
    	/*synchronized(this){
            this.runningThread = Thread.currentThread();
        }*/
    	
        
		//Grab the bound socket's input and output streams
		try {
			ins = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
			outs = new PrintWriter(clientSocket.getOutputStream(), true);
		
		    String msgInput, msgOutput;
		    
		    while ((msgInput = ins.readLine())==null) {}
		    clientId = msgInput; //MessageThread:line 57
		    
		    msgOutput = "Tell me anything and I'll repeat it with modification. Say 'Thanks' if you've had enough";
		    outs.println(msgOutput);
		    
		    //While client keeps writing to its own output Stream...
		    while((msgInput = ins.readLine())!=null) {
		    	Date now = new Date();
				SimpleDateFormat format = new SimpleDateFormat("HH:mm:ss:SSS");
		    	
		    	System.out.print("Received \""+msgInput+"\" from " + clientId);
				System.out.print(", Max delay is " + myInfo.max_delay + " s, ");
				System.out.println("system time is "+format.format(now));
		        
		    	msgOutput = msgInput + "WHEEEEEEE";
		    	
		    	if (msgInput.equals("Thanks"))
		    		msgOutput = "Bye";
		    	
		    	System.out.print("Sent \""+msgOutput+"\" to "+clientId+", system time is ");
				now = new Date();
				System.out.println(format.format(now));
		    	outs.println(msgOutput);
		    	
		    	if(msgOutput.equals("Bye")) //Sending disconnect msg
		    		break;
		    
		    }
			
    	
    	} catch (IOException e) {
			System.out.println("Reader and Writer failed");
			System.exit(-1);
			return;
		}
        
        //m.Question("Whee");
		finally {
			try {
				System.out.println("Connection Closing with a client...");
		        if (ins!=null){
		            ins.close(); 
		            System.out.println("Socket Input Stream with a Client Closed");
		        }

		        if(outs!=null){
		            outs.close();
		            System.out.println("Socket Out with a Client Closed");
		        }
		        if (clientSocket!=null){
			        clientSocket.close();
			        System.out.println("Socket with a Client Closed");
		        }
		    } catch(IOException ie){
		        System.out.println("Socket with a Client Close Error");
		    }
		}//end finally
    }
    
    
    /*http://tutorials.jenkov.com/java-multithreaded-servers/multithreaded-server.html*/
    private synchronized boolean isStopped() {
        return this.isStopped;
    }
    
	
}
