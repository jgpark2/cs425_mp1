package mp1_1;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.File;
import java.io.FileInputStream;
import java.net.ServerSocket;
import java.net.Socket;

/*
 * Node: represents one node in our distributed system
 * Holds 1 CommandInputThread
 *       3 MessageReceiverThread (1 receiving from every other node)
 *       3 MessageDelayerThread (1 delaying for every other node)
 */
public class Node {

	private NodeInfo[] nodesinfo;
	private NodeInfo myInfo;
	public int myIdx; //index into NodeInfo array

	private ServerSocket server;
	
	private CommandInputThread cmdin;
	private MessageReceiverThread [] receivers;
	private MessageDelayerThread [] senders;

	private NodeThreads threads;
	private ServerThread st;
	//private ClientThread ct;
	private MessageThread mt;
	
	public static void main(String[] args) throws Exception
	{
		if (args.length != 1) {
			System.out.println("Usage: java Node [node_id]");
			System.exit(1);
			return;
		}
		
		Node node = new Node();
		
		if (node.parseConfig(args) == -1) {
			System.out.println("Failed to parse config");
			System.exit(1);
			return;
		}
		
		node.start();
		
		
	}
	
	
	private int parseConfig(String[] args)
	{
		nodesinfo = new NodeInfo[4];
		
		
		for (int i=0; i<4; i++)
			nodesinfo[i] = new NodeInfo();
		
		String id = args[0].toUpperCase();
		
		if (id.compareTo("A") != 0
			&& id.compareTo("B") != 0
			&& id.compareTo("C") != 0
			&& id.compareTo("D") != 0) {
			System.out.println("node ids must be A, B, C, or D");
			return -1;
		}

		//Parsing config file
		//File configfile = new File("/home/galbrth2/cs425/cs425_mp1/CS425_MP1/src/config"); //can't seem to make this a non-absolute path
		File configfile = new File("config");
		FileInputStream fis = null;

		try {
			fis = new FileInputStream(configfile);
			StringWriter str = new StringWriter();
			int content;
			
			for (int i=0; i<4; i++) { //four nodes id, ip
				content = fis.read(); //node id
				str.write(content);
				nodesinfo[i].id = str.toString();
				
				if (str.toString().compareTo(id)==0) {
					System.out.println("Identified this node as "+str.toString());
					myInfo = nodesinfo[i];
					myIdx = i;
				}
				
				
				str = new StringWriter();
				
				content = fis.read(); //,
				
				while ((char)(content = fis.read()) != '\n') //node ip
					str.write(content);

				nodesinfo[i].ip = str.toString();
				str = new StringWriter();
			}
			
			for (int i=0; i<4; i++) { //four nodes port
				content = fis.read(); //node id
				
				content = fis.read(); //,
				
				while ((char)(content = fis.read()) != '\n') //node port
					str.write(content);

				Integer portnumint = new Integer(str.toString());
				str = new StringWriter();
				nodesinfo[i].port = portnumint.intValue();
			}
			
			for (int i=0; i<4; i++) { //four nodes max delay
				content = fis.read(); //node id

				content = fis.read(); //,

				while ((char)(content = fis.read()) != '\n') //node max delay
					str.write(content);
				
				Double mddouble = new Double(str.toString());
				str = new StringWriter();
				nodesinfo[i].max_delay = mddouble.doubleValue();
			}
			
		} catch (Exception e) { //if config file can't be parsed, exit
			e.printStackTrace();
			return -1;
		} finally {
			try {
				if (fis != null) fis.close();
			} catch (Exception ex) {
				ex.printStackTrace();
			}
		}
		
		for (int i=0; i<4; i++) { //test that node info is correct
			System.out.print("Node " + nodesinfo[i].id + ": ");
			System.out.print("IP: " + nodesinfo[i].ip + " ");
			System.out.print("Port: " + nodesinfo[i].port + " ");
			System.out.println("Max delay: " + nodesinfo[i].max_delay);
		}
		return 0;
	}
	
	public void start() {

		receivers = new MessageReceiverThread[4];
        senders = new MessageDelayerThread[4];
        for (int i=0; i<4; i++) {
        	receivers[i] = null;
        	senders[i] = null;
        }


        //Start the Client thread which will eventually spawn 3 RECV threads
//		new Thread(new Client(nodesinfo, myInfo), "Receiver").start();
		//Start the CommandInputThread thread that will eventually spawn 3 MessageDelayerThread Threads (sockets)
        cmdin = new CommandInputThread(this);
        
		
//    	int serverPort = myInfo.port;
//    	ServerSocket serverSocket;
		//reference: http://docs.oracle.com/javase/tutorial/networking/sockets/clientServer.html
		//http://stackoverflow.com/questions/10131377/socket-programming-multiple-client-to-one-server
//    	System.out.println("Now listening on "+serverPort+"...");
    	
        try {
//        	serverSocket = new ServerSocket(serverPort);
			server = new ServerSocket(nodesinfo[myIdx].port);
        } catch (IOException e) {
//			System.out.println("Could not listen on port "+serverPort);
			System.out.println("Could not listen on port " + nodesinfo[myIdx].port);
			e.printStackTrace();
			System.exit(-1);
			return;
        }
        
        //Start the SEND thread for each connection
        //We should keep a list of these sockets...
        Socket socket;
		int count = 0;

        while(count < 3){
            try{
            	socket = server.accept();
//              socket = serverSocket.accept();
//              System.out.println("Connection established with a client.");
//              ServerThread st=new ServerThread(nodesinfo, myInfo, socket);
//              st.start();
                new MessageReceiverThread(this, socket);
				count++;
            } catch(Exception e){
            	System.out.println("Connection accept failed: "+nodesinfo[myIdx].port);
				e.printStackTrace();
            }
        }
        //new Thread(new ServerThread(nodesinfo, myInfo), "Sender").start();
    	
        
		//threads = new NodeThreads(nodesinfo);

	}
	
	
	//This methods are used to fill in the Socket arrays once a connections is initialized
		public void setReceivingThreadIndex(int idx, MessageReceiverThread receiver) {
			if ((idx < 0) || (idx > 3) || (receiver == null))
				return;
			receivers[idx] = receiver;
		}
		
		public void setSendingThreadIndex(int idx, MessageDelayerThread delayer) {
			if ((idx < 0) || (idx > 3) || (delayer == null))
				return;
			senders[idx] = delayer;
		}
		
		public MessageReceiverThread getReceivingThread(int idx) {
			if ((idx < 0) || (idx > 3) || (idx == myIdx))
				return null;
			return receivers[idx];
		}
		
		public MessageDelayerThread getSendingThread(int idx) {
			if ((idx < 0) || (idx > 3) || (idx == myIdx))
				return null;
			return senders[idx];
		}
		
		public CommandInputThread getCommandInputThread() {
			return cmdin;
		}
		
		
		public NodeInfo [] getNodesInfo() {
			return nodesinfo;
		}
		

		public int getIndexFromId(String id) {
			int i = -1;
			if (id.compareToIgnoreCase("a") == 0)
				i = 0;
			else if (id.compareToIgnoreCase("b") == 0)
				i = 1;
			else if (id.compareToIgnoreCase("c") == 0)
				i = 2;
			else if (id.compareToIgnoreCase("d") == 0)
				i = 3;
				
			return i;
		}
		
		
		public void setDefaultNodesInfo() {
			nodesinfo = new NodeInfo[4];
			for (int i=0; i<4; i++) {
				nodesinfo[i] = new NodeInfo();
				nodesinfo[i].id = "nosocket";
				nodesinfo[i].max_delay = 7.0;
			}
		}
	
}
