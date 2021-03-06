package mp1;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;

/*
 * CentralServer: represents the central leader in our distributed system
 * Holds 1 MessageRouterThread
 *       4 MessageRelayThread (1 receiving from every node)
 *       4 MessageDelayerThread (1 sending to every node)
 *       1 RepairThread (sleeps and sends repair message)
 * This class spawns threads that send periodic inconsistency repair messages for
 * eventual consistency models, supports the search utility tool, and
 * implements totally-ordered broadcast for the linearizability and sequential
 * consistency models
 */
public class CentralServer {
	
	//Structures to hold information from config file
	private NodeInfo[] nodesinfo;
	public NodeInfo leaderInfo;

	//The socket that the CentralServer listens on
	private ServerSocket server;
	
	//Queue that implements total-ordering (all messages get queued)
	private ArrayBlockingQueue<String> mqin;
	private int mqmax = 1023;
	
	private MessageRouterThread router; //this will create the MessageDelayerThreads on Socket connection
	private MessageRelayThread [] receivers; //spawned from CentralServer
	private MessageDelayerThread [] senders; //spawned from MessageRouterThread
	
	//Implements last-writer-win rule, only gets written to for eventual consistency
	//The periodic repair messages contain information from this
	public ConcurrentHashMap<String, Datum> globalData;

	
	public static void main(String[] args) {
		
		CentralServer leader = new CentralServer();
		
		if (leader.parseConfig() == -1) {
			System.out.println("Failed to parse config");
			System.exit(-1);
			return;
		}
		
		leader.start();
	}


	/*
	 * Reads in the config file (should sit in the directory the code runs from)
	 * and returns -1 if any of the formatting is incorrect
	 */
	private int parseConfig() {
		
		nodesinfo = new NodeInfo[4];
		leaderInfo = new NodeInfo();
		
		for (int i=0; i<4; i++)
			nodesinfo[i] = new NodeInfo();

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
	 * CentralServer spawns necessary threads after config has been read
	 */
	private void start() {
		
		//Initialization of class member variables
		receivers = new MessageRelayThread[4];
        senders = new MessageDelayerThread[4];
        for (int i=0; i<4; i++) {
        	receivers[i] = null;
        	senders[i] = null;
        }
        mqin = new ArrayBlockingQueue<String>(mqmax);
        globalData = new ConcurrentHashMap<String, Datum>();

		//Start the MessageRouterThread thread that will eventually spawn 3 MessageDelayerThread Threads (sockets)
        router = new MessageRouterThread(this, mqin, mqmax);
        
    	
        try {
			server = new ServerSocket(leaderInfo.port);
        } catch (IOException e) {
			System.out.println("Could not listen on port "+leaderInfo.port);
			e.printStackTrace();
			System.exit(-1);
			return;
        }
        
        System.out.println("Leader started on "+leaderInfo.ip+":"+leaderInfo.port);
        
        Socket socket;
		int count = 0;
		//Connect to all 4 Nodes (A,B,C,D)
        while (count < 4) {
        	
            try{
            	socket = server.accept();
            	BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            	String input = "";
    			while ((input = in.readLine())==null) {} //get Node info (index into NodeInfo array)
    			int idx = Integer.parseInt(input);
    			setReceivingThreadIndex(idx, new MessageRelayThread(this, socket, in, mqin, idx));
    			
				count++;
            } catch(Exception e){
            	System.out.println("Connection accept failed");
				e.printStackTrace();
            }
            
        }
        
        //Spawn the periodic inconsistency message repair thread
        new RepairThread(this);
	}
	
	
	//This methods are used to fill in the Socket arrays once a connections is initialized
	public void setReceivingThreadIndex(int idx, MessageRelayThread receiver) {
		if ((idx < 0) || (idx > 3) || (receiver == null))
			return;
		receivers[idx] = receiver;
	}
		
	public void setSendingThreadIndex(int idx, MessageDelayerThread sender) {
		if ((idx < 0) || (idx > 3) || (sender == null))
			return;
		senders[idx] = sender;
	}
			
	public MessageRelayThread getReceivingThread(int idx) {
		if ((idx < 0) || (idx > 3))
			return null;
		return receivers[idx];
	}
			
	public MessageDelayerThread getSendingThread(int idx) {
		if ((idx < 0) || (idx > 3))
			return null;
		return senders[idx];
	}
			
	public MessageRouterThread getConnectionRouterThread() {
		return router;
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

}
