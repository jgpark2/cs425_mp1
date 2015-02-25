import java.net.*;
import java.io.*;

/*
 * ServerThread.java
 * This class should be on a thread.
 * This thread listens and receives messages through the sockets defined at the start
 * (ip addr and port #). 
 */
 
 
 public class ServerThread implements Runnable
{

	protected int          serverPort   = 7500;
    protected ServerSocket serverSocket = null;
    
    protected boolean      isStopped    = false;
    
    protected Thread       runningThread= null;
    
    
	//run the server thread
	//have public methods such as setting up on a certain port/ip
	//it will call methods in ServerThreads to convey messages
	
	//NodeThreads m;
	
    public ServerThread(/*NodeThreads m1, int port*/) { 
        //this.m = m1;
        //this.serverPort = port;
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
        
        
        //reference: http://docs.oracle.com/javase/tutorial/networking/sockets/clientServer.html
        try {
        	serverSocket = new ServerSocket(serverPort);
        } catch (IOException e) {
			System.out.println("Could not listen on port "+serverPort);
			System.exit(-1);
			return;
        }
        
        System.out.println("Listening...");
        
        Socket clientSocket;
        BufferedReader in;
        PrintWriter out;
    	//Listen for incoming connection
    	try {
			clientSocket = serverSocket.accept();
		} catch (IOException e) {
			System.out.println("Accept failed: "+serverPort);
			System.exit(-1);
			return;
		}
		
		//Grab the bound socket's input and output streams
		try {
			in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
			out = new PrintWriter(clientSocket.getOutputStream(), true);
		
		    String msgInput, msgOutput;
		    
		    msgOutput = "Tell me anything and I'll repeat it with modification. Say 'Thanks' if you've had enough";
		    out.println(msgOutput);
		    
		    //While client keeps writing to its own output Stream...
		    while((msgInput = in.readLine())!=null) {
		        
		    	msgOutput = msgInput + "WHEEEEEEE";
		    	
		    	if (msgInput.equals("Thanks"))
		    		msgOutput = "Bye";
		    	
		    	out.println(msgOutput);
		    	
		    	if(msgOutput.equals("Bye")) //Sending disconnect msg
		    		break;
		    
		    }
			
    	
    	} catch (IOException e) {
			System.out.println("Reader and Writer failed");
			System.exit(-1);
			return;
		}
        
        /*
        while(! isStopped()){
            Socket clientSocket = null;
            
            try {
                clientSocket = this.serverSocket.accept();
            } catch (IOException e) {
                if(isStopped()) {
                    System.out.println("Server Stopped.") ;
                    return;
                }
                throw new RuntimeException("Error accepting client connection", e);
            }
            new Thread(  new WorkerRunnable(clientSocket, "Multithreaded Server")  ).start();
        }
        System.out.println("Server Stopped.") ;*/
        
        //m.Question("Whee");
    }
    
    
    /*http://tutorials.jenkov.com/java-multithreaded-servers/multithreaded-server.html*/
    private synchronized boolean isStopped() {
        return this.isStopped;
    }
    
	
}
