package mp1;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.io.File;
import java.io.FileInputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ConcurrentHashMap;

/*
 * Node: represents one node in our distributed system  
 * Holds 1 CommandInputThread
 *       4 MessageReceiverThread (3 from Nodes, 1 from CentralServer)
 *       3 MessageDelayerThread (3 to Nodes)
 *       1 MessageSenderThread (1 to CentralServer)
 * This class spawns threads that take operation commands and execute them
 * and threads that send and receive over socket connections with
 * other Nodes or the central leader
 */
public class Node {

	//Structures to hold information from config file
	private NodeInfo[] nodesinfo;
	public NodeInfo leaderInfo;
	public int myIdx; //index into NodeInfo array
	
	//Number of operation requests made by this Node
	public int reqcnt = 0;
	
	//Map that tracks how many acks we have received/are yet to receive for a message
	public ConcurrentHashMap<String, AckTracker> recvacks;

	//The socket that the Node listens on
	private ServerSocket server;
	
	//Command input
	public BufferedReader inputs = null;
	
	//Threads
	private CommandInputThread cmdin;
	private MessageReceiverThread [] receivers;
	private MessageDelayerThread [] senders;
	
	//This node's replica of the key-value store
	public ConcurrentHashMap<String, Datum> sharedData;


	public static void main(String[] args) throws Exception
	{		
		Node node;
		
		if (args.length == 1)
			node = new Node(null);
		else if (args.length == 2)
			node = new Node(args[1]);
		else {
			System.out.println("Usage: Node [node_id]");
			System.out.println("Optional usage: Node [node_id] [input_file]");
			System.exit(-1);
			return;
		}
		
		if (node.parseConfig(args) == -1) {
			System.out.println("Failed to parse config");
			System.exit(-1);
			return;
		}
		
		node.start();
		
		
	}
	
	
	/*
	 * Takes input file name as argument
	 */
	public Node(String filename) {
		if (filename != null) {
			File inputfile = new File(filename);
			try {
				inputs = new BufferedReader(new FileReader(inputfile));
			} catch (FileNotFoundException e) {
				System.out.println("Could not open input file");
				e.printStackTrace();
				inputs = null;
				return;
			}
		}
		else { //we are using System.in for commands, not an input file
			inputs = new BufferedReader(new InputStreamReader(System.in));
		}
	}
	
	
	/*
	 * Reads in the config file (should sit in the directory the code runs from)
	 * and returns -1 if any of the formatting is incorrect
	 */
	private int parseConfig(String[] args)
	{
		nodesinfo = new NodeInfo[4];
		leaderInfo = new NodeInfo();
		
		
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
				
				if (str.toString().compareTo(id)==0)
					myIdx = i;
				
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
			
			//Central server node
			str = new StringWriter();
			content = fis.read(); //node id
			str.write(content);
			leaderInfo.id = str.toString();
			str = new StringWriter();
			content = fis.read(); //,
			while ((char)(content = fis.read()) != '\n') //node ip
				str.write(content);
			leaderInfo.ip = str.toString();
			str = new StringWriter();
			content = fis.read(); //node id
			content = fis.read(); //,
			while ((char)(content = fis.read()) != '\n') //node port
				str.write(content);
			Integer portnumint = new Integer(str.toString());
			str = new StringWriter();
			leaderInfo.port = portnumint.intValue();
			
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

		return 0;
	}
	
	
	/*
	 * Node spawns necessary threads after config has been read
	 */
	public void start() {

		//Initialization
		receivers = new MessageReceiverThread[4];
        senders = new MessageDelayerThread[4];
        for (int i=0; i<4; i++) {
        	receivers[i] = null;
        	senders[i] = null;
        }
        recvacks = new ConcurrentHashMap<String, AckTracker>();
        sharedData = new ConcurrentHashMap<String, Datum>();

		//Start the CommandInputThread thread that will eventually spawn MessageDelayerThread Threads
        cmdin = new CommandInputThread(this, inputs);
        
        try {
			server = new ServerSocket(nodesinfo[myIdx].port);
        } catch (IOException e) {
			System.out.println("Could not listen on port " + nodesinfo[myIdx].port);
			e.printStackTrace();
			System.exit(-1);
			return;
        }
        
        System.out.println("\nNode "+nodesinfo[myIdx].id+" started on "
        		+nodesinfo[myIdx].ip+":"+nodesinfo[myIdx].port+"\n");
        
        Socket socket;
        int count = 0;
        
        while (count < 4) { //connect with 3 Nodes and 1 CentralServer
        	try {
        		socket = server.accept();
        		BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
    			String input = "";
    			while ((input = in.readLine())==null) {} //get recvIdx from client
    			int idx = Integer.parseInt(input);
    			setReceivingThreadIndex(idx, new MessageReceiverThread(this, socket, in, idx));

    			count++;
        	} catch (Exception e) {
        		System.out.println("Connection accept with CentralServer failed");
        		e.printStackTrace();
        		return;
        	}
        }

	}
	
	
	//This methods are used to fill in the Socket arrays once a connections is initialized
	public void setReceivingThreadIndex(int idx, MessageReceiverThread receiver) {
		if ((idx < -1) || (idx > 3) || (receiver == null))
			return;
		if (idx == -1) {
		} else
			receivers[idx] = receiver;
	}
		
	public void setSendingThreadIndex(int idx, MessageDelayerThread delayer) {
		if ((idx < 0) || (idx > 3) || (delayer == null))
			return;
		senders[idx] = delayer;
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
	
	public String getIdFromIndex(int idx) {
		if (idx == -1)
			return "leader";
		if (idx >= 0 && idx < 4)
			return nodesinfo[idx].id;
		return "";
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
