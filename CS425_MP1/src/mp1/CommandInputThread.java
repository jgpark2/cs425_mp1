package mp1;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringWriter;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;

/*
 * CommandInputThread: 1 per Node object
 * Takes input from System.in or command file and sends that request
 * to the correct communication channel
 */
public class CommandInputThread extends Thread {
	
	private Node node;
	private NodeInfo [] nodesinfo;
	private NodeInfo leaderInfo;
	private int myIdx; //index into NodeInfo array
	
	private BufferedReader cmdin; //this can be from FileReader OR System.in	
	
	private Random r;
	//These are calculated once and saved to be more efficient
	private Double [] millismaxdelays;
	private int [] intmaxdelays;
	
	//because of Java, this can't be a generic array
	private ArrayList < ArrayBlockingQueue<MessageType> > mqnodearr;
	private ArrayBlockingQueue<String> mqleader;
	private int mqmax = 1023;
	private MessageType [] last;
	
	//Indicates whether the current command is finished executing or not
	public volatile boolean cmdComplete = false;
    

	public CommandInputThread(Node node, BufferedReader inputstream) {
		this.node = node;
    	myIdx = node.myIdx;
    	nodesinfo = node.getNodesInfo();
    	leaderInfo = node.leaderInfo;
    	
		cmdin = inputstream;
		
		//Translate delay into necessary types
		millismaxdelays = new Double[4];
		intmaxdelays = new int[4];
		r = new Random();
		mqnodearr = new ArrayList < ArrayBlockingQueue<MessageType> >(4);
		mqleader = new ArrayBlockingQueue<String>(mqmax);
		last = new MessageType[4];
		
		for (int i=0; i<4; i++) {
			millismaxdelays[i] = new Double(nodesinfo[i].max_delay*1000.0);
			intmaxdelays[i] = millismaxdelays[i].intValue();
			mqnodearr.add(new ArrayBlockingQueue<MessageType>(mqmax));
			last[i] = new MessageType();
		}
		
		new Thread(this, "CommandInput").start();
	}

	public void run() {

		//Create socket connections
		for (int i=0; i<4; i++) {
			//Skip my own server
			if (i == myIdx)
				continue;
			
			Socket servconn = null;
			while (servconn == null) {
				try {
					servconn = new Socket(nodesinfo[i].ip, nodesinfo[i].port);
    				System.out.print("Server: Now connected to "+nodesinfo[i].ip+":"+nodesinfo[i].port);
    				System.out.println(" (node "+nodesinfo[i].id+")");
    				node.setSendingThreadIndex(i,
    						new MessageDelayerThread(nodesinfo, myIdx, mqnodearr.get(i), nodesinfo[i].id, servconn));
    			} catch (Exception e) {
    				servconn=null;
    			}
			}
		}
		
		Socket leaderconn = null;
		while (leaderconn == null) {
			try {
				leaderconn = new Socket(leaderInfo.ip, leaderInfo.port);
				System.out.print("Server: Now connected to "+leaderInfo.ip+":" +leaderInfo.port);
				System.out.println(" (leader)");
				node.setToLeaderSendingThread(new MessageSenderThread(node, mqleader, leaderconn));
			} catch (Exception e) {
				leaderconn = null;
			}
		}
		System.out.println();

		
		//Get commands from either System.in or an input file
		String cmd = "";
		
        try {
			while ((cmd = cmdin.readLine()) != null) { //no longer support exit, just read until end of input file

				//Discard any old references and make a new MT object
				MessageType msg = new MessageType(cmd, System.currentTimeMillis());
				cmdComplete = false;
//				System.out.println("Beginning to execute command "+cmd);
				
				//parse and send along message input
				int error = parseForIncorrectFormat(msg);
				switch (error) {
					case 0: { //delete
						parseDelete(msg);
						break;
					}
					case 1: { //get
						parseGet(msg);
						break;
					}
					case 2: { //insert
						parseInsert(msg);
						break;
					}
					case 3: { //send
						parseCommandForMessage(msg);
						break;
					}
					case 4: { //update
						parseUpdate(msg);
						break;
					}
					case 5: { //show-all
						show_allUtility();
						break;
					}
					case 6: { //delay T
						parseDelay(msg);
						break;
					}
					case 7: { //search key
						parseSearch(msg);
						break;
					}
					default: {
						System.out.println("Client: Message was not correctly fomatted; try again");
						continue;
					}
				}
				
				//do not restart the loop until the command is finished being processed
				while (!cmdComplete) {}
//				System.out.println("Finished executing command "+cmd+", and cmdComplete is "+cmdComplete);
			}
		} catch (IOException e) {
			System.out.println("Failed to get command");
			e.printStackTrace();
			return;
		}
        
//        System.out.println("CommandInputThread reached end of file, exiting");
        try {
			cmdin.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	

	public void addMessageToAllQueues(MessageType msg) {
		try {
        	for (Iterator< ArrayBlockingQueue<MessageType> > it = mqnodearr.iterator(); it.hasNext();) {
        		it.next().put(msg);
        	}
        	mqleader.put(msg.msg);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
	}
	
	
	/*
	 * Appends the timestamp of the message to the end of the message
	 * Adds message string to MessageSenderThread queue to be sent
	 */
	public void addMessageToLeaderQueue(MessageType msg) {
		String tosend = msg.msg + " " + msg.ts.longValue();
		try {
			mqleader.put(tosend);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
	
	
	/*
	 * Calculates the random delay, gives message to correct MessageDelayerThread
	 */
	public void addMessageToNodeQueue(MessageType msg, int recvIdx) {
		
		MessageType tosend = new MessageType(msg); //remove all references to old object
		
		//Calculate random delay
		int randint = 0;
		if (intmaxdelays[recvIdx] > 0)
			randint = r.nextInt(intmaxdelays[recvIdx]); //random number of milliseconds
		
//		System.out.println("Random delay to "+nodesinfo[recvIdx].id+" with message "+tosend.msg+" is "+randint+" milliseconds");
				
		//if last[recvIdx] is no longer in the channel, its ts will definitely be smaller
		tosend.ts = new Long(Math.max(tosend.ts + (long)randint, last[recvIdx].ts.longValue()));
//		System.out.println("Adjusted receive time to "+nodesinfo[recvIdx].id+" with message "+tosend.msg+" is "+tosend.ts);
			
		try {
			mqnodearr.get(recvIdx).put(tosend);
			last[recvIdx] = new MessageType(tosend);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
	
	
	/*
	 * Take command and check number of arguments
	 * Returns an integer indicating which type of message it is
	 */
	private int parseForIncorrectFormat(MessageType msg) {
		
//		System.out.println("Entering parseForIncorrectFormat, msg is "+msg.msg);
		int type = -1;
		String adjustedmsg = new String(msg.msg);
		StringBuilder builder = new StringBuilder();
		int len = msg.msg.length();
		
		//delete key
		if (msg.msg.lastIndexOf("delete ") == 0) {
			boolean hasKey = false;
			builder.append("delete ");
			int i = 7;
			while (len > i && msg.msg.charAt(i) == ' ') //move past extra spaces
				i++;
			while (len > i && msg.msg.charAt(i) != ' ') { //add key to builder
				builder.append(msg.msg.charAt(i));
				hasKey = true;
				i++;
			}
			//command does not have all arguments or more info after arguments
			if (!hasKey || i < len)
				return type;
				
			adjustedmsg = builder.toString();
			type = 0;
		}
		
		//get key model
		else if (msg.msg.lastIndexOf("get ") == 0) {
			boolean hasKey = false, hasModel = false;
			builder.append("get ");
			int i = 4;
			while (len > i && msg.msg.charAt(i) == ' ') //move past extra spaces
				i++;
			while (len > i && msg.msg.charAt(i) != ' ') { //add key to builder
				builder.append(msg.msg.charAt(i));
				hasKey = true;
				i++;
			}
			builder.append(" "); //space between key and model
			while (len > i && msg.msg.charAt(i) == ' ') //move past extra spaces
				i++;
			while (len > i && msg.msg.charAt(i) != ' ') { //add model to builder
				builder.append(msg.msg.charAt(i));
				hasModel = true;
				i++;
			}
			//command does not have all arguments or more info after arguments
			if (!hasKey || !hasModel || i < len)
				return type;
			
			adjustedmsg = builder.toString();
			type = 1;
		}
		
		//insert key value model
		else if (msg.msg.lastIndexOf("insert ") == 0) {
			boolean hasKey = false, hasValue = false, hasModel = false;
			builder.append("insert ");
			int i = 7;
			while (len > i && msg.msg.charAt(i) == ' ') //move past extra spaces
				i++;
			while (len > i && msg.msg.charAt(i) != ' ') { //add key to builder
				builder.append(msg.msg.charAt(i));
				hasKey = true;
				i++;
			}
			builder.append(" "); //space between key and value
			while (len > i && msg.msg.charAt(i) == ' ') //move past extra spaces
				i++;
			while (len > i && msg.msg.charAt(i) != ' ') { //add value to builder
				builder.append(msg.msg.charAt(i));
				hasValue = true;
				i++;
			}
			builder.append(" "); //space between value and model
			while (len > i && msg.msg.charAt(i) == ' ') //move past extra spaces
				i++;
			while (len > i && msg.msg.charAt(i) != ' ') { //add model to builder
				builder.append(msg.msg.charAt(i));
				hasModel = true;
				i++;
			}
			//command does not have all arguments or more info after arguments
			if (!hasKey || !hasModel || !hasValue || i < len)
				return type;
			
			adjustedmsg = builder.toString();
			type = 2;
		}
		
		//send message destination
		else if (msg.msg.lastIndexOf("send ") == 0) {
			//the "send" message error checking occurs in parseCommandForRecvIdx
			type = 3;
		}
		
		//update key value model
		else if (msg.msg.lastIndexOf("update ") == 0) {
			boolean hasKey = false, hasValue = false, hasModel = false;
			builder.append("update ");
			int i = 7;
			while (len > i && msg.msg.charAt(i) == ' ') //move past extra spaces
				i++;
			while (len > i && msg.msg.charAt(i) != ' ') { //add key to builder
				builder.append(msg.msg.charAt(i));
				hasKey = true;
				i++;
			}
			builder.append(" "); //space between key and value
			while (len > i && msg.msg.charAt(i) == ' ') //move past extra spaces
				i++;
			while (len > i && msg.msg.charAt(i) != ' ') { //add value to builder
				builder.append(msg.msg.charAt(i));
				hasValue = true;
				i++;
			}
			builder.append(" "); //space between value and model
			while (len > i && msg.msg.charAt(i) == ' ') //move past extra spaces
				i++;
			while (len > i && msg.msg.charAt(i) != ' ') { //add model to builder
				builder.append(msg.msg.charAt(i));
				hasModel = true;
				i++;
			}
			//command does not have all arguments or more info after arguments
			if (!hasKey || !hasModel || !hasValue || i < len)
				return type;
			
			adjustedmsg = builder.toString();
			type = 4;
		}
		
		//show-all utility tool
		else if (msg.msg.lastIndexOf("show-all") == 0) {
			type = 5;
		}
		
		//delay T
		else if (msg.msg.lastIndexOf("delay ") == 0) {
			boolean hasT = false;
			builder.append("delay ");
			int i = 6;
			while (len > i && msg.msg.charAt(i) == ' ') //move past extra spaces
				i++;
			while (len > i && msg.msg.charAt(i) != ' ') { //add T to builder
				builder.append(msg.msg.charAt(i));
				hasT = true;
				i++;
			}
			//command does not have all arguments or more info after arguments
			if (!hasT || i < len)
				return type;
				
			adjustedmsg = builder.toString();
			type = 6;
		}
		
		//search key
		else if (msg.msg.lastIndexOf("search ") == 0) {
			boolean hasKey = false;
			builder.append("search ");
			int i = 7;
			while (len > i && msg.msg.charAt(i) == ' ') //move past extra spaces
				i++;
			while (len > i && msg.msg.charAt(i) != ' ') { //add key to builder
				builder.append(msg.msg.charAt(i));
				hasKey = true;
				i++;
			}
			//command does not have all arguments or more info after arguments
			if (!hasKey || i < len)
				return type;
			
			adjustedmsg = builder.toString();
			type = 7;
		}
		
		msg.msg = adjustedmsg;
//		System.out.println("Exiting parseForIncorrectFormat, msg is "+msg.msg);
		return type;
	}
	
	
	/*
	 * If the command is of the form "delete key", this
	 * method appends the relevant information to the message
	 * and sends the message to the CentralServer
	 * Since delete consistency can be whatever we want (Piazza @244), we won't
	 * wait for acks
	 * This method assumes that parseForIncorrectFormat has already been called
	 * final format: delete key <timestamp>
	 */
	private void parseDelete(MessageType msg) {
		//We want to send this out even if this Node has no replica of the key

		addMessageToLeaderQueue(msg); //this method adds the timestamp
//		System.out.println("Marking cmdComplete as true in parseDelete");
		this.cmdComplete = true;
	}
	
	
	/*
	 * If the command is of the form "get key model", this
	 * method appends the relevant information to the message
	 * and sends the message to the CentralServer
	 * This method assumes that parseForIncorrectFormat has already been called
	 * final format: get key model <requestingnodeid> <requestnumber> <reqorack> <timestamp>
	 */
	private void parseGet(MessageType msg) {
		//Extract model out of msg
		String modelstr = msg.msg.substring(msg.msg.length()-1);
		int model = Integer.parseInt(modelstr);
		
		node.reqcnt++;
		
		switch (model) {
			case 1: {
				//linearizability: send to CentralServer, wait for req to be received
				
				msg.msg = msg.msg + " " + nodesinfo[myIdx].id + " " + node.reqcnt + " " + "req";
				addMessageToLeaderQueue(msg); //this method adds the timestamp
				break;
			}
			
			case 2: {
				//sequential consistency: print out our own replica of this data
				
				//Extract key out of msg
				String key = msg.msg.substring(4, msg.msg.length()-2);
				Datum value = node.sharedData.get(key);
				if (value == null) //key is not in replica
					System.out.println("Client: get("+key+") = (NO KEY FOUND)");
				else
					System.out.println("Client: get("+key+") = "+value.value);

				this.cmdComplete = true;
				break;
			}
			
			case 3: {
				//eventual consistency, R=1: read 1 replica (ours)
				
				//Extract key out of msg
				String key = msg.msg.substring(4, msg.msg.length()-2);
				Datum value = node.sharedData.get(key);
				
				if (value == null) { //key is not in replica, send request to all other Nodes
					
					//This format of the message represents a unique identifier for the request
					msg.msg = msg.msg + " " + nodesinfo[myIdx].id + " " + node.reqcnt;
					node.recvacks.put(msg.msg, new AckTracker(1));
					msg.msg = msg.msg + " " + "req" + " " + msg.ts.longValue();
					for (int i = 0; i< 4; i++) {
						if (i != myIdx)
							addMessageToNodeQueue(msg, i); //this method adds no timestamp
					}
				}
				
				else {
					System.out.print("Client: get("+key+") = ("+value.value+", ");
					SimpleDateFormat format = new SimpleDateFormat("H:mm:ss.SSS");
					Date curdate = new Date(value.timestamp);
					System.out.println(format.format(curdate) + ")");
					
					//print every <value, timestamp> pair examined during get request
					System.out.println("("+value.value+", "+format.format(curdate) + ")");

					this.cmdComplete = true;
				}
				
				break;
			}
			
			case 4: {
				//eventual consistency, R=2; wait until 2 Nodes have been read
				
				//Extract key out of msg
				String key = msg.msg.substring(4, msg.msg.length()-2);
				Datum value = node.sharedData.get(key);
				
				//This format of the message represents a unique identifier for the request
				msg.msg = msg.msg + " " + nodesinfo[myIdx].id + " " + node.reqcnt;
				
				if (value == null) { //wait for two acks
					node.recvacks.put(msg.msg, new AckTracker(2));
				}
				else { //our replica contains key, store its value in recvacks, wait for 1 ack
					AckTracker ours = new AckTracker(1); //wait for 1 ack
					//add our key/value as an ack message of form
					//get key model <reqNodeId> <reqcnt> <value> <associatedvaluetimestamp> ack <timestamp>
					String ourack = new String(msg.msg); //get key model <reqNodeId> <reqcnt>
					ourack = ourack + " " + value.value + " " + value.timestamp + " ack "+msg.ts.longValue();
					ours.validacks.add(ourack);
					
					node.recvacks.put(msg.msg, ours);
				}
				
				msg.msg = msg.msg + " " + "req" + " " + msg.ts.longValue();
				for (int i = 0; i< 4; i++) {
					if (i != myIdx)
						addMessageToNodeQueue(msg, i);
				}
				
				break;
			}
			
			default: {
				System.out.println("Client: Message was not correctly fomatted; try again");
				this.cmdComplete = true;
				return;
			}
		}
		
	}


	/*
	 * If the command is of the form "insert key value model", this
	 * method appends the relevant information to the message
	 * and sends the message to the CentralServer
	 * This method assumes that parseForIncorrectFormat has already been called
	 * final format: insert key value model <requestingnodeid> <requestnumber> <reqorack> <timestamp>
	 */
	private void parseInsert(MessageType msg) {
		node.reqcnt++;
		
		//Extract model out of msg
		String modelstr = msg.msg.substring(msg.msg.length()-1);
		int model = Integer.parseInt(modelstr);
		
		//Extract key out of msg
		StringBuilder builder = new StringBuilder();
		int i = 7;
		while (msg.msg.charAt(i) != ' ') { //move through key
			builder.append(msg.msg.charAt(i));
			i++;
		}
		String key = builder.toString();
		
		//Extract value out of msg
		builder = new StringBuilder();
		i++; //move past space between key and value
		while (msg.msg.charAt(i) != ' ') { //move through value
			builder.append(msg.msg.charAt(i));
			i++;
		}
		String value = builder.toString();
		
		switch (model) {
			case 1: {
				//linearizability: send to CentralServer, wait for 4 acks to be received
				//This format of the message represents a unique identifier for the request
				msg.msg = msg.msg + " " + nodesinfo[myIdx].id + " " + node.reqcnt;
				node.recvacks.put(msg.msg, new AckTracker(4));
				msg.msg = msg.msg + " " + "req";
				addMessageToLeaderQueue(msg); //this method adds the timestamp
				return;
			}
			
			case 2: {
				//sequential consistency: send to CentralServer, wait for 4 acks to be received
				//This format of the message represents a unique identifier for the request
				msg.msg = msg.msg + " " + nodesinfo[myIdx].id + " " + node.reqcnt;
				node.recvacks.put(msg.msg, new AckTracker(4));
				msg.msg = msg.msg + " " + "req";
				addMessageToLeaderQueue(msg); //this method adds the timestamp
				return;
			}
			
			case 3: {
				//eventual consistency, W=1: only wait for 1 replica to write (ours)
				
				Datum toinsert = new Datum(value, msg.ts.longValue());
				node.sharedData.put(key, toinsert);
				System.out.println("Server: Inserted key "+key);
				
				//We need to tell them all to write, but we don't need to wait for the acks
				//This format of the message represents a unique identifier for the request
				msg.msg = msg.msg + " " + nodesinfo[myIdx].id + " " + node.reqcnt;
				node.recvacks.put(msg.msg, new AckTracker(0));
				msg.msg = msg.msg + " " + "req" + " " + msg.ts.longValue();
				for (int idx=0; idx<4; idx++) {
					if (idx != myIdx)
						addMessageToNodeQueue(msg, idx); //this method adds no timestamp
				}
				
				System.out.println("Client: Inserted key "+key);
				this.cmdComplete = true;
				break;
			}
			
			case 4: {
				//eventual consistency, W=2: send to 1 other Node, wait for 1 ack
				
				Datum toinsert = new Datum(value, msg.ts.longValue());
				node.sharedData.put(key, toinsert);
				System.out.println("Server: Inserted key "+key);
				
				//We need to tell them all to write, but we only need 1 ack
				//This format of the message represents a unique identifier for the request
				msg.msg = msg.msg + " " + nodesinfo[myIdx].id + " " + node.reqcnt;
				node.recvacks.put(msg.msg, new AckTracker(1));
				msg.msg = msg.msg + " " + "req" + " " + msg.ts.longValue();
				for (int idx=0; idx<4; idx++) {
					if (idx != myIdx)
						addMessageToNodeQueue(msg, idx); //this method adds no timestamp
				}
				
				break;
			}
			
			default: {
				System.out.println("Client: Message was not correctly fomatted in parseInsert; try again");
				this.cmdComplete = true;
				return;
			}
		}
		
		//In addition, for both eventual consistency models, this insert should occur in globalData
		//addMessageToLeaderQueue adds the timestamp of invocation of this insert
		MessageType writeglobal = new MessageType("", msg.ts);
		writeglobal.msg = "writeglobal "+key+" "+value;
		addMessageToLeaderQueue(writeglobal);
		
	}


	/*
	 * If the command is of the form "update key value model", this
	 * method appends the relevant information to the message
	 * and sends the message to the CentralServer
	 * This method assumes that parseForIncorrectFormat has already been called
	 * final format: update key value model <requestingnodeid> <requestnumber> <reqorack> <timestamp>
	 */
	private void parseUpdate(MessageType msg) {
		//Extract model out of msg
		String modelstr = msg.msg.substring(msg.msg.length()-1);
		int model = Integer.parseInt(modelstr);
				
		node.reqcnt++;
		
		//Extract key out of msg
		StringBuilder builder = new StringBuilder();
		int i = 7;
		while (msg.msg.charAt(i) != ' ') { //move through key
			builder.append(msg.msg.charAt(i));
			i++;
		}
		String key = builder.toString();
		
		//Extract value out of msg
		builder = new StringBuilder();
		i++; //move past space between key and value
		while (msg.msg.charAt(i) != ' ') { //move through value
			builder.append(msg.msg.charAt(i));
			i++;
		}
		String value = builder.toString();		
				
		switch (model) {
			case 1: {
				//linearizability: send to CentralServer, wait for 4 acks to be received
				break;
			}
					
			case 2: {
				//sequential consistency: send to CentralServer, wait for 4 acks to be received
				break;
			}
					
			case 3: {
				//eventual consistency, W=1: send to 1 replica (ours) (Piazza post @178)
				
				//This format of the message represents a unique identifier for the request
				msg.msg = msg.msg + " " + nodesinfo[myIdx].id + " " + node.reqcnt;
				
				if (node.sharedData.get(key) == null) { //not yet updated in any replicas, wait for 1 ack

					node.recvacks.put(msg.msg, new AckTracker(1));
					
				}
				else { //already written in 1 replica, wait for no acks
					Datum toinsert = new Datum(value, msg.ts.longValue());
					Datum old = node.sharedData.put(key, toinsert);
					System.out.println("Server: Key "+key+" changed from "+old.value+" to "+value);
					
					node.recvacks.put(msg.msg, new AckTracker(0));
					
					MessageType writeglobal = new MessageType("", msg.ts);
					writeglobal.msg = "writeglobal "+key+" "+value;
					addMessageToLeaderQueue(writeglobal); //this method adds the timestamp
					
					this.cmdComplete = true;
				}
				
				//We need to tell them all to write, regardless of how many acks we need
				msg.msg = msg.msg + " " + "req" + " " + msg.ts.longValue();
				for (int idx=0; idx<4; idx++) {
					if (idx != myIdx)
						addMessageToNodeQueue(msg, idx); //this method adds no timestamp
				}

				return;
			}
					
			case 4: {
				//eventual consistency, W=2: update in 2 Nodes
				
				//This format of the message represents a unique identifier for the request
				msg.msg = msg.msg + " " + nodesinfo[myIdx].id + " " + node.reqcnt;
				
				if (node.sharedData.get(key) == null) { //try to update in other Nodes
					node.recvacks.put(msg.msg, new AckTracker(2));
				}
				else { //update in our Node and one other Node
							
					Datum toinsert = new Datum(value, msg.ts.longValue());
					Datum old = node.sharedData.put(key, toinsert);
					System.out.println("Server: Key "+key+" changed from "+old.value+" to "+value);

					node.recvacks.put(msg.msg, new AckTracker(1));
				}
				
				//We need to tell them all to write, regardless of how many acks we need
				msg.msg = msg.msg + " " + "req" + " " + msg.ts.longValue();
				for (int idx=0; idx<4; idx++) {
					if (idx != myIdx)
						addMessageToNodeQueue(msg, idx);
				}
				
				return;
			}
					
			default: {
				System.out.println("Client: Message was not correctly fomatted; try again");
				this.cmdComplete = true;
				return;
			}
		}
				
		//Both linearizability and sequential consistency run this:
		if (node.sharedData.get(key) == null) { //key does not exist in system
			System.out.println("Client: Key "+key+" does not exist in system, attempted update failed");
			return;
		}
		
		//This format of the message represents a unique identifier for the request
		msg.msg = msg.msg + " " + nodesinfo[myIdx].id + " " + node.reqcnt;
		node.recvacks.put(msg.msg, new AckTracker(4));
		msg.msg = msg.msg + " " + "req";
		addMessageToLeaderQueue(msg); //this method adds the timestamp
		
	}
	
	
	/*
	 * If the command is of the form "send message destination", this
	 * method narrows down the relevant information to the message
	 * and sends the message to the specified Node
	 * This method assumes that parseForIncorrectFormat has already been called
	 */
	private void parseCommandForMessage(MessageType cmd) { //"Send Hello B"
		int len = cmd.msg.length();
		
		int recvIdx = parseCommandForRecvIdx(cmd.msg);
		if (recvIdx < 0) {
			System.out.println("Client: Message was not correctly fomatted; try again");
			this.cmdComplete = true;
			return;
		}
		
		StringWriter writer = new StringWriter();
		for (int i=5; i<(len-2); i++)
			writer.append(cmd.msg.charAt(i));
		cmd.msg = writer.toString();
		
		addMessageToNodeQueue(cmd, recvIdx);
		System.out.print("Client: Sent \""+cmd.msg+"\" to "+nodesinfo[recvIdx].id+", system time is ");
		SimpleDateFormat format = new SimpleDateFormat("H:mm:ss.SSS");
		Date curdate = new Date();
		System.out.println(format.format(curdate));

		this.cmdComplete = true;
	}
	
	
	/*
	 * If the command is of the form "send message destination", this
	 * method extracts the index into the NodeInfo array of the destination
	 * This method also checks this message form for any errors
	 */
	private int parseCommandForRecvIdx(String cmd) {
		int len;
		if ((len = cmd.length()) < 6) {
			//System.out.println("cmd was too short");
			return -1;
		}
		
		if (cmd.charAt(len-2) != ' ') {
			//System.out.println("cmd did not have space");
			return -1;
		}
		
		StringWriter str = new StringWriter();
		str.append(cmd.charAt(len-1));
		String id = str.toString();
		//Only allow A, B, C, or D as servers
		if (id.compareToIgnoreCase("A")!=0 && id.compareToIgnoreCase("B")!=0
				&& id.compareToIgnoreCase("C")!=0 && id.compareToIgnoreCase("D")!=0) {
			System.out.println("Client: Server id must be one of: A,B,C,D");
			//System.out.println(" cmd did not have serverid");
			return -1;
		}

		if (id.compareToIgnoreCase(nodesinfo[myIdx].id) == 0) {
			return -1;
		}
		
		return node.getIndexFromId(id);
	}


	/*
	 * If the command is of the form "show-all", this method displays all
	 * the key-value pairs stored at this replica
	 * This method assumes that parseForIncorrectFormat has already been called
	 */
	private void show_allUtility() {
		Set<String> keyset = node.sharedData.keySet();
		System.out.println("Key-value pairs stored in this replica:");
		Iterator<String> it = keyset.iterator();
		while (it.hasNext()) {
			String key = it.next();
			Datum value = node.sharedData.get(key);
			System.out.println(key + ": " + value.value);
		}

		this.cmdComplete = true;
	}


	/*
	 * If the command is of the form "delay T", this method merely sleeps for
	 * T seconds (in the form of a decimal) and marks the command complete
	 * This method assumes that parseForIncorrectFormat has already been called
	 */
	private void parseDelay(MessageType msg) {
		//Extract T
		String t = msg.msg.substring(6, msg.msg.length());
		
		Double tdouble = new Double(t);
		Double tmillis = new Double(tdouble.doubleValue()*1000.0);
		long tosleep = tmillis.longValue();
		
		if (tosleep > 0) {
			try {
				Thread.sleep(tosleep);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}

		this.cmdComplete = true;
	}


	/*
	 * If the command is of the form "search key", this method sends a request
	 * to all other Nodes to see if their replica contains the key (waits for 3 acks)
	 * This method assumes that parseForIncorrectFormat has already been called
	 * final format: search key <requestingnodeid> <requestnumber> <reqorack> <timestamp>
	 */
	private void parseSearch(MessageType msg) {
		node.reqcnt++;
		
		//This format of the message represents a unique identifier for the request
		msg.msg = msg.msg + " " + nodesinfo[myIdx].id + " " + node.reqcnt;
		node.recvacks.put(msg.msg, new AckTracker(3));
		msg.msg = msg.msg + " " + "req";
		
		msg.ts = new Long(0); //we don't want to delay this utility
		
		addMessageToLeaderQueue(msg); //this method adds the timestamp
	}
	
	public String helperParseGetAckTS(String input, AckTracker acks) {

		//Extract associatedvaluetimestamp
		String prevAssocTS = acks.validacks.get(0);
		int j = prevAssocTS.lastIndexOf("ack")-2;

		String ret = "";
		while (prevAssocTS.charAt(j) != ' ') { //move thru key
			ret = prevAssocTS.charAt(j) + ret;
			j--;
		}
		
		return ret;
	}
	
	public String helperParseGetAckVal(String input){
		//Extract the 'value' suggested by this previously received ack msg
		int i = 4; //"get "
		//skip key
		while (input.charAt(i) != ' ') {
			i++;
		}
		//skip model and reqnodeId,
		//now at request number
		i = i+5;
		while (input.charAt(i) != ' ') {
			i++;
		}
		//value (after req num, behind assocTS string)
		i++; //skip the space
		StringBuilder build = new StringBuilder();
		while (input.charAt(i) != ' ') {
			build.append(input.charAt(i));
			i++;
		}
		
		return build.toString();
	}
	
	
	/*
	 * When a MessageReceiverThread receives a get message,
	 * this method either sends a reply, completes a get operation, or does nothing
	 * Updates this.cmdComplete when the necessary number of acks have been received
	 */
	public void respondToGetMessage(String input) {
		
		//input (if req) : get key model <reqNodeId> <reqnumber> req <timestamp>
		//input (if ack) : get key model <reqNodeId> <reqnumber> <value> <associatedvaluetimestamp> ack <timestamp>

		StringBuilder builder;
		int i = "get ".length();
		
		//Extract key
		builder = new StringBuilder();
		while (input.charAt(i) != ' ') { //move thru key
			builder.append(input.charAt(i));
			i++;
		}
		String key = builder.toString();
		
		//Extract model
		int model = Integer.parseInt(input.substring(i+1, i+2));
		
		//Extract requestingnodeid
		String reqNodeId = input.substring(i+3, i+4);
		int reqNodeIdx = node.getIndexFromId(reqNodeId);
		
		//request number
		i = i+5;
		builder = new StringBuilder();
		while (input.charAt(i) != ' ') {
			builder.append(input.charAt(i));
			i++;
		}
		builder.toString();
		int reqNumberEndIndex = i; //the index for the space after reqNumber for future reference
		
		//Determine if input is req or ack messages
		int reqat = input.lastIndexOf("req");
		int ackat = input.lastIndexOf("ack");
		
		String curMsgVal = "USHUDNOTBESEEINGTHISTXT";
		String curMsgAssocTS = "USHUDNOTBESEEINGTHISTXT";
		if(ackat!=-1) { //if ack get message
			//value (after req num, behind assocTS string)
			i++; //skip the space
			builder = new StringBuilder();
			while (input.charAt(i) != ' ') {
				builder.append(input.charAt(i));
				i++;
			}
			curMsgVal = builder.toString();
			
			//assocTS (behind req/ack string)
			i++; //skip space
			builder = new StringBuilder();
			while (input.charAt(i) != ' ') {
				builder.append(input.charAt(i));
				i++;
			}
			curMsgAssocTS = builder.toString();
		}
		
		//This format of the message represents a unique identifier for the request
		String identifier = input.substring(0, reqNumberEndIndex);
		
		AckTracker acks = node.recvacks.get(identifier);
		
		//GET
		switch (model) {
			case 1:	//linearizability: (the request only gets sent back to the requestingNode)
				if (reqat != -1) { //req
					//print out "get(<key>) = <value>" from sharedData
					if (node.sharedData.get(key)==null)
						System.out.println("Client: get("+key+") = (NO KEY FOUND)");
					else
						System.out.println("Client: get("+key+") = "+node.sharedData.get(key).value);
					
					cmdComplete=true;
				}
				//ack: not applicable, see linearizable totally-ordered broadcast algorithm
				break;
			
			case 2: //sequential consistency: not applicable, requesting node just prints out its own value
				break;
			
			case 3: //eventual consistency R=1:
				if(reqat!=-1) { //req
					Datum getRequest = node.sharedData.get(key);
					
					String val;
					long assocTS;
					
					//if this node doesn't have the key, put "null" as <value> and <associatedvaluetimestamp>
					if (getRequest == null) {
						val = "null";
						assocTS = 0;
					}
					else{
						val = getRequest.value;
						assocTS = getRequest.timestamp;
					}
					
					//send requestingnode (along peer connection) an ack of form
					//get key model <requestingnodeid> <requestnumber> <value> <associatedvaluetimestamp> ack <timestamp>
					String ack = new String(input.substring(0, reqNumberEndIndex) +" " + val + " ");
					if (assocTS==0)
						ack = ack + "null ack";
					else
						ack = ack + assocTS + " ack";
					
					//ack = ack + input.substring(reqat + 4); //... <timestamp>
					
					long ts = System.currentTimeMillis();
					addMessageToNodeQueue(new MessageType(ack+" "+ts, ts), reqNodeIdx);
				}
				
				else { //ack
					
					//look in recvacks to see how many acks we have received with the following identifier:
					//"get key model <requestingnodeid> <requestnumber>"
					if (acks == null || acks.toreceive < 1) {
						//do nothing
					}
					
					else if (acks.toreceive==1 && input.lastIndexOf("null") == -1) {
						//if recvacks has 1 more ack to receive (R=1) and ack doesn't have "null" as value,
						//store 0 in recvacks
						acks.toreceive = 0;
						
						//print "get(<key>) = (<value>, <associatedvaluetimestamp>)"
						System.out.print("Client: get("+key+") = ("+curMsgVal+", ");
						SimpleDateFormat format = new SimpleDateFormat("H:mm:ss.SSS");
						Date curdate = new Date(Long.parseLong(curMsgAssocTS));
						System.out.println(format.format(curdate) + ")");
						
						//then print "(<value>, <associatedvaluetimestamp>)" on a new line...
						System.out.println("("+curMsgVal+", "+format.format(curdate)+")");

						cmdComplete = true;
					}
					
					else if (acks.toreceive==1 && input.lastIndexOf("null") != -1) {
						//if recvacks has 1 more ack to receive (R=1) and ack has "null" as value,
						//store 1 more null ack received in recvacks' nullacks ArrayList
						acks.nullacks.add(input);
						
						if(acks.nullacks.size()>=3){
							//if 3 such null acks have been received now (no one else could find the key)
							//print "get(<key>) = (NO KEY FOUND)"
							System.out.println("Client: get("+key+") = (NO KEY FOUND)");
							//store 0 in recvacks
							acks.toreceive = 0;

							cmdComplete = true;
						}

					}
					else {
						//yay						
					}
				}
				break;
				
			case 4:	//eventual consistency R=2:
				
				//req: same as req for eventual consistency R=1
				if (reqat!=-1) { //req
					Datum getRequest = node.sharedData.get(key);
					
					String val;
					long assocTS;
					
					//if this node doesn't have the key, put "null" as <value> and <associatedvaluetimestamp>
					if (getRequest == null) {
						val = "null";
						assocTS = 0;
					}
					else{
						val = getRequest.value;
						assocTS = getRequest.timestamp;
					}
					
					//send requestingnode (along peer connection) an ack of form
					//get key model <requestingnodeid> <requestnumber> <value> <associatedvaluetimestamp> ack <timestamp>
					String ack = new String(input.substring(0, reqNumberEndIndex) + " " + val + " ");
					if (assocTS==0)
						ack = ack + "null ack";
					else
						ack = ack + assocTS + " ack";
					
					//ack = ack + input.substring(reqat + 4); //... <timestamp>
					
					long ts = System.currentTimeMillis();
					addMessageToNodeQueue(new MessageType(ack+" "+ts, ts), reqNodeIdx);
					
				}
				
				else { //ack
					
					//look in recvacks to see how many acks we have received with followingidentifier:
					//"get key model <requestingnodeid> <requestnumber>"
					if (acks != null && acks.toreceive == 2 && input.lastIndexOf("null") == -1) {
						//if recvacks has 2 more acks to receive (R=2) and this ack doesn't have "null" as value,
						//store 1 in recvacks
						acks.toreceive = 1;
						//store the received (value,associatedtimestamp) in recvacks' validacks ArrayList
						acks.validacks.add(input);
					}
					
					else if (acks.toreceive == 2 && input.lastIndexOf("null") != -1) {
						//if recvacks has 2 more acks to receive (R=2) and this ack has "null" as value,
						//store 1 more null ack received recvacks' nullacks ArrayList
						acks.nullacks.add(input);
						
						//if 3 such null acks have been received now,
						if(acks.nullacks.size()>=3) {
							//print "get(<key>) = (NO KEY FOUND)"
							System.out.println("Client: get("+key+") = (NO KEY FOUND)");
							//store 0 in recvacks
							acks.toreceive = 0;

							cmdComplete = true;
						}

					}
					
					//if recvacks has 1 more ack to receive and this ack doesn't have "null" as value
					else if (acks.toreceive == 1 && input.lastIndexOf("null") == -1) {
						
						//store 0 in recvacks
						acks.toreceive = 0;
						
						//compare the previously received ack (stored in recvacks' validacks ArrayList) and the current received one
						//Technically previously received one is the last index, but since we do it on 2nd msg receive, it's also the first index

						//Extract value & timestamp
						String prevVal = helperParseGetAckVal(input);
						String prevTS_str = helperParseGetAckTS(input,acks);
						long prevTS = Long.parseLong(prevTS_str);
						
						SimpleDateFormat format = new SimpleDateFormat("H:mm:ss.SSS");
						Date morerecentdate = new Date(prevTS);
						String morerecentVal = new String(prevVal);
						
						//print "get(<key>) = (<value>, <associatedvaluetimestamp>)" for the more recent one
						if (prevTS < Long.parseLong(curMsgAssocTS)) {
							morerecentdate = new Date(Long.parseLong(curMsgAssocTS));
							morerecentVal = curMsgVal;
						}
						
						System.out.println("Client: get("+key+") = ("+morerecentVal+", "+format.format(morerecentdate)+")");
							
						//print "(<value>, <assorciatedtimestamp>)" for both examined ack's
						System.out.println("("+curMsgVal+", "+format.format(new Date(Long.parseLong(curMsgAssocTS)))+")");
						System.out.println("("+prevVal+", "+format.format(new Date(prevTS))+")");

						cmdComplete = true;
						
					}
					else if (acks.toreceive == 1 && input.lastIndexOf("null") != -1){
						//if we had already accepted 1 valid ack but received a null ack just now
						//aka: recvacks has 1 more ack to receive and this ack has "null" as value,
						
						//if 1 such null ack has already been received:
						if(acks.nullacks.size()==1) {
							//We received acks back from all 3 other nodes. Must resort to the first valid ack
							
							//print "get(<key>) = (<value>, <associatedvaluetimestamp>)" for the non-null ack
							String prevVal=helperParseGetAckVal(acks.validacks.get(0));
							String prevTS_str = helperParseGetAckTS(input,acks);
							long prevTS;
							if (prevTS_str.lastIndexOf("null") == -1)
								prevTS = Long.parseLong(prevTS_str);
							else
								prevTS = Long.MAX_VALUE;
							
							SimpleDateFormat format = new SimpleDateFormat("H:mm:ss.SSS");
							Date curdate = new Date(prevTS);
							
							System.out.println("Client: get("+key+") = ("+ prevVal +", "+ format.format(curdate) +")");
							
							//store 0 in recvacks
							acks.toreceive=0;
							
							cmdComplete = true;

						}
						
						else if(acks.nullacks.size()==0) { //no such null acks have been received yet

							//store 1 more null ack received in recvacks' nullacks ArrayList
							acks.nullacks.add(input);
						}
						
					}
					
				}
				
		}

	}
	
	public ReceivedInputElements helperMotherOfParse(String input){
		ReceivedInputElements ret = new ReceivedInputElements();

		String[] elems = input.split("\\s+");
		ret.definition 	= elems[0];
		ret.key 		= elems[1];
		ret.key_value 	= elems[2];
		ret.model 		= elems[3];
		ret.reqNodeId 	= elems[4];
		ret.reqNum		= elems[5];
		ret.replyType	= elems[6];
		ret.timestamp 	= elems[7];
		ret.identifier = "";
		for(int i=0; i<6; ++i) {
			ret.identifier += elems[i];
			if (i<5)
				ret.identifier+=" ";
		}
		
		return ret;
	}
	/*
	 * When a MessageReceiverThread receives an insert message,
	 * this method either sends a reply, completes an insert operation, or does nothing
	 * Updates this.cmdComplete when the necessary number of acks have been received
	 */
	public void respondToInsertMessage(String input) {
		//---------------INSERT MESSAGE RESPONSE------------------//
		//input (req) : insert key value model <reqnodeid> <req#> req <timestamp>
		//input (ack) : insert key value model <reqnodeid> <req#> ack <origTS> <ackSentTS>
		
		ReceivedInputElements ins = helperMotherOfParse(input);
		
		AckTracker acks = node.recvacks.get(ins.identifier);
		
		//INSERT
		switch (Integer.parseInt(ins.model)) {
			case 1: //linearizability:

			case 2: //sequential consistency: same as linearizability; thus inapplicable
				if (ins.replyType.compareTo("req")==0) {
					//insert the key and value and attached timestamp into sharedData
					node.sharedData.put(ins.key, new Datum(ins.key_value, Long.parseLong(ins.timestamp)));
					
					System.out.println("Server: Inserted key "+ins.key);
					
					//send an ack to leader of form:
					//insert key value model <requestingnodeid> <requestnumber> ack <timestamp> <senttimestamp>
					//where <senttimestamp> is time when this Node sent message to leader
					String ackMsg = new String();
					ackMsg+=ins.identifier;
					ackMsg+=" ack "+ins.timestamp+" ";
					Long ackTS = System.currentTimeMillis();
					ackMsg += ackTS.toString();
					addMessageToLeaderQueue(new MessageType(ackMsg, ackTS));
				}
				else { //ack:
					//look in recvacks to see how many acks we have received with identifier:
					//insert key value model <requestingnodeid> <requestnumber>
					
					//if recvacks has more than 1 more acks to receive, store that number decremented
					//if recvacks has 1 more ack to receive, store 0 in recvacks and print "Inserted key <key>"
					if(acks.toreceive>0)
						acks.toreceive-=1;
					if (acks.toreceive==0) {
						System.out.println("Client: Inserted key "+ins.key);
						cmdComplete = true;
					}

				}
				break;
			
			case 3: //eventual consistency R=1:
				if (ins.replyType.compareTo("req")==0) {
					//insert the key and value and attached timestamp into sharedData, print "Inserted key <key>"
					node.sharedData.put(ins.key, new Datum(ins.key_value, Long.parseLong(ins.timestamp)));
					System.out.println("Server: Inserted key "+ins.key);
					
					//an ack is unnecessary because the requester only checked 1 insert (itself)
				}
				else { //ack: not applicable, see line above
					//insert the key and value and attached timestamp into sharedData
				}
				break;
				
			case 4:	//eventual consistency R=2:
				
				if (ins.replyType.compareTo("req")==0) { //req:
					//insert the key and value and attached timestamp into sharedData, print "Inserted key <key>"
					node.sharedData.put(ins.key, new Datum(ins.key_value, Long.parseLong(ins.timestamp)));
					System.out.println("Server: Inserted key "+ins.key);
					
					//send an ack to requesting node of form (don't have to append any timestamp)
					//insert key value model <requestingnodeid> <requestnumber> ack <timestamp>
					String ackMsg = new String();
					ackMsg+=ins.identifier;
					ackMsg+=" ack "+ins.timestamp;
					long ts = System.currentTimeMillis();
					addMessageToNodeQueue(new MessageType(ackMsg+" "+ts, ts), node.getIndexFromId(ins.reqNodeId));
				}
				
				else if (ins.replyType.compareTo("ack")==0) {//ack:
					
					//look in recvacks to see how many acks we have received with identifier:
					//insert key value model <requestingnodeid> <requestnumber>		
					if (acks==null) {
						System.out.println("DEBUG:uhoh");
						return;
					}
						
					//if recvacks has 1 more ack to receive, store 0 in recvacks
					if(acks.toreceive==1) {
						acks.toreceive=0;
						System.out.println("Client: Inserted key "+ins.key);

						cmdComplete = true;
					}
					else if (acks.toreceive==0) {
						//do nothing
					}
				} else {
					System.out.println("DEBUG:invalid reponse type, not req and not ack. Was:"+ins.replyType);
				}
				break;
		}
	}
	
	
	/*
	 * When a MessageReceiverThread receives an update message,
	 * this method either sends a reply, completes an update operation, or does nothing
	 * Updates this.cmdComplete when the necessary number of acks have been received
	 * Received format: update key value model <requestingnodeid> <requestnumber> <reqorack> <timestamp>
	 */
	public void respondToUpdateMessage(String input) {
		
		int len = input.length();
		StringBuilder builder;
		int i = 7;
		
		//Extract key
		builder = new StringBuilder();
		while (input.charAt(i) != ' ') { //move thru key
			builder.append(input.charAt(i));
			i++;
		}
		String key = builder.toString();
		
		//Extract value
		builder = new StringBuilder();
		i++; //move past space between key and value
		while (input.charAt(i) != ' ') { //move thru value
			builder.append(input.charAt(i));
			i++;
		}
		String value = builder.toString();
		
		//Extract model
		int model = Integer.parseInt(input.substring(i+1, i+2));
		
		//Extract requestingnodeid
		String reqNodeId = input.substring(i+3, i+4);
		int reqNodeIdx = node.getIndexFromId(reqNodeId);
		
		//Extract requestnumber
		builder = new StringBuilder();
		i += 5; //move to beginning of requestnumber
		while (input.charAt(i) != ' ') { //move thru reqnum
			builder.append(input.charAt(i));
			i++;
		}
		int reqnum = Integer.parseInt(builder.toString());
		
		//Determine if input is req or ack messages
		int reqat = input.lastIndexOf("req");
		int ackat = input.lastIndexOf("ack");
		int reqorackat = Math.max(reqat, ackat);
		
		//Extract timestamp of update operation invocation
		builder = new StringBuilder();
		i  = reqorackat + 4; //move to the timestamp immediately after req/ack
		while (i < len && input.charAt(i) != ' ') { //move thru timestamp
			builder.append(input.charAt(i));
			i++;
		}
		long writets = Long.parseLong(builder.toString());
		
		//This format of the message represents a unique identifier for the request
		String identifier = input.substring(0, reqorackat-1);
		
		AckTracker acks = node.recvacks.get(identifier);

		
		switch (model) {
			case 1: //linearizability and sequential consistency behave exactly the same
			case 2: {
				
				if (reqat != -1) { //req
					
					//update the key and value and attached timestamp in sharedData, print
					//"Key <key> changed from <old value> to <value>"
					Datum toupdate = new Datum(value, writets);
					Datum old = node.sharedData.put(key, toupdate);
					if (old == null) //this shouldn't happen if consistency models aren't mixed
						System.out.println("Server: Key "+key+" changed from null to "+value);
					
					else
						System.out.println("Server: Key "+key+" changed from "+old.value+" to "+value);
					
					//send an ack to leader of form, where <senttimestamp> is time when this Node sent message to leader
					//update key value model <requestingnodeid> <requestnumber> ack <timestamp> <senttimestamp>
					String ack = new String(input.substring(0, reqat) +"ack "); //update key value model <reqNodeId> <reqnum> ack
					ack = ack + input.substring(reqat + 4); //<timestamp>
					addMessageToLeaderQueue(new MessageType(ack, System.currentTimeMillis())); //adds senttimestamp
				}
				
				else { //ack
					
					//look in recvacks to see how many acks we have received with identifier
					if (acks == null || acks.toreceive < 1) {
						//do nothing
					}
					
					else if (acks.toreceive == 1) {
						//if recvacks has 1 more ack to receive, store 0 in recvacks
						acks.toreceive = 0;
						node.recvacks.put(identifier, acks);
						
						//print "Key <key> updated to <value>"
						System.out.println("Client: Key "+key+" updated to "+value);
						
						//mark cmdComplete as true
						cmdComplete = true;
					}
					
					else {
						//if recvacks has more than 1 more acks to receive, store that number decremented
						acks.toreceive--;
						node.recvacks.put(identifier, acks);
					}
				}
				
				break;
			}
			case 3: { //eventual consistency W=1:
				
				if (reqat != -1) { //req
					
					Datum oldvalue = node.sharedData.get(key);
					
					if (oldvalue == null) {
						//respond to requestingnode on peer channel with an ack of the form
						//update key value model <requestingnodeid> <requestnumber> ack null <timestamp> <senttimestamp>
						String ack = identifier+" ack null "+input.substring(reqorackat + 4);
						long ts = System.currentTimeMillis();
						addMessageToNodeQueue(new MessageType(ack+" "+ts, ts), reqNodeIdx);
					}
					
					else {
						//update the copy of key in sharedData to value
						node.sharedData.put(key, new Datum(value,writets));
						
						//print "Key <key> changed from <oldvalue> to <value>"
						System.out.println("Server: Key "+key+" changed from "+oldvalue.value+" to "+value);
						
						//respond to requestingnode on peer channel with an ack of the form
						//update key value model <requestingnodeid> <requestnumber> ack <timestamp> <senttimestamp>
						String ack = identifier+" ack "+input.substring(reqorackat + 4);
						long ts = System.currentTimeMillis();
						addMessageToNodeQueue(new MessageType(ack+" "+ts, ts), reqNodeIdx);
					}					
				}
				
				else { //ack
					
					//look in recvacks to see how many acks we have received with identifier
					if (acks == null || acks.toreceive < 1) {
						//do nothing
					}
					else {
						
						if (input.lastIndexOf("null") == -1) { //this ack does not say null
							//store 0 in recvacks and print "Key <key> updated to <value>"
							acks.toreceive = 0;
							node.recvacks.put(identifier, acks);
							System.out.println("Client: Key "+key+" updated to "+value);
							
							//send "writeglobal <key> <value> <timestamp>" (the original ts of message) to leader
							String writeglobal = "writeglobal "+key+" "+value+" "+writets;
							addMessageToLeaderQueue(new MessageType(writeglobal, new Long(0)));
							
							//mark cmdComplete as true
							cmdComplete = true;
						}
						else { //this ack says null

							//store 1 more null ack received in recvacks' nullacks ArrayList
							acks.nullacks.add(input);
							node.recvacks.put(identifier, acks);
							
							if (acks.nullacks.size() >= 3) { //3 such null acks have been received
								
								//print "Key <key> does not exist in system, attempted update failed"
								System.out.print("Client: Key "+key+" does not exist in system,");
								System.out.println(" attempted update failed");
								
								//store 0 in recvacks
								acks.toreceive = 0;
								node.recvacks.put(identifier, acks);
								
								//mark cmdComplete as true
								cmdComplete = true;
							}

						}
						
					}
					
				}
				
				break;
			}
			case 4: { //eventual consistecy W=2:
				
				if (reqat != -1) { //req

					Datum oldvalue = node.sharedData.get(key);
					
					if (oldvalue != null) { //this Node's sharedData has a copy of the key
						
						//update the copy of key in sharedData to value
						node.sharedData.put(key, new Datum(value,writets));
						
						//print "Key <key> changed from <oldvalue> to <value>"
						System.out.println("Server: Key "+key+" changed from "+oldvalue.value+" to "+value);
						
						//respond to requestingnode on peer channel with an ack of the form
						//update key value model <requestingnodeid> <requestnumber> ack <timestamp> <senttimestamp>
						String ack = identifier+" ack "+input.substring(reqorackat + 4);
						long ts = System.currentTimeMillis();
						addMessageToNodeQueue(new MessageType(ack+" "+ts, ts), reqNodeIdx);
						
					}
					
					else { //this Node's sharedData does not have a copy of the key
						
						//respond to requestingnode on peer channel with an ack of the form
						//update key value model <requestingnodeid> <requestnumber> ack null <timestamp> <senttimestamp>
						String ack = identifier+" ack null "+input.substring(reqorackat + 4);
						long ts = System.currentTimeMillis();
						addMessageToNodeQueue(new MessageType(ack+" "+ts, ts), reqNodeIdx);
					}
					
				}
				
				else { //ack
					
					//look in recvacks to see how many acks we have received with identifier
					if (acks == null || acks.toreceive < 1) {
						//do nothing
					}
					
					else if (acks.toreceive == 1) {

						if (input.lastIndexOf("null") == -1) { //this ack does not say null
							//store 0 in recvacks, print "Key <key> updated to <value>"
							acks.toreceive = 0;
							node.recvacks.put(identifier, acks);
							System.out.println("Client: Key "+key+" updated to "+value);
							
							//send "writeglobal <key> <value> <timestamp>" (the original ts of message) to leader
							String writeglobal = "writeglobal "+key+" "+value+" "+writets;
							addMessageToLeaderQueue(new MessageType(writeglobal, new Long(0)));
							
							//mark cmdComplete as true
							cmdComplete = true;
						}
						
						else { //this ack says null
							//store 1 more null ack received in recvacks' nullacks ArrayList
							acks.nullacks.add(input);
							node.recvacks.put(identifier, acks);
							
							if (acks.nullacks.size() >= 3) { //3 such null acks have now been received
								//store 0 in recvacks (cmdComplete won't be marked)
								acks.toreceive = 0;
								node.recvacks.put(identifier, acks);
								
								//send out a special message to 1 Node: "copy key value reqNodeId reqnum req timestamp"
								//telling them to insert this key/value/timestamp
								String special = "copy "+key+" "+value+" "+reqNodeId+" "+reqnum+" req "+writets;
								long ts = System.currentTimeMillis();
								addMessageToNodeQueue(new MessageType(special+" "+ts, ts), (reqNodeIdx+1)%4);
								
								//wait until you have received the ack for this (see respondToSpecialCopyMessage)
								//then cmdComplete will be marked, and this will no longer matter
							}
							
							//if 2 such null acks have now been received and sharedData does not have this key
							else if (acks.nullacks.size() == 2 && node.sharedData.get(key) == null) {
								//insert this key/value/<timestamp> into sharedData and print special message
								node.sharedData.put(key, new Datum(value,writets));
								System.out.println("Server: Specially inserted "+key+" for eventual consistency update");
								
								//print "Key <key> changed from null to <value>"
								//print "Key <key> updated to <value>"
								System.out.println("Server: Key "+key+" changed from null to "+value);
								System.out.println("Client: Key "+key+" updated to "+value);
								
								//store 0 in recvacks
								acks.toreceive = 0;
								node.recvacks.put(identifier, acks);
								
								//send "writeglobal <key> <value> <timestamp>" (the original ts of message) to leader
								String writeglobal = "writeglobal "+key+" "+value+" "+writets;
								addMessageToLeaderQueue(new MessageType(writeglobal, new Long(0)));
								
								//mark cmdComplete as true
								cmdComplete = true;
							}
							
						}
						
					}
					
					else { //recvacks has 2 more acks to receive
						
						if (input.lastIndexOf("null") == -1) { //this ack does not say null
							//store 1 in recvacks
							acks.toreceive = 1;
							node.recvacks.put(identifier, acks);
						}
						else { //this ack says null
							//store 1 more null ack received in recvacks' nullacks ArrayList
							acks.nullacks.add(input);
							node.recvacks.put(identifier, acks);
							
							if (acks.nullacks.size() >= 3) { //3 such null acks have been received
								//print attempted update failed message
								System.out.print("Client: Key "+key+" does not exist in system,");
								System.out.println(" attempted update failed");
								
								//store 0 in recvacks
								acks.toreceive = 0;
								node.recvacks.put(identifier, acks);
								
								//then mark cmdComplete as true
								cmdComplete = true;
							}
							
						}

					}

				}

				break;
			}
		}

	}
	
	
	/*
	 * This method is for taking care of the case where eventual consistency
	 * required that we make a second copy in the system at a different Node
	 * Message form: "copy key value reqNodeId reqnum req timestamp"
	 * Updates this.cmdcomplete
	 */
	public void respondToSpecialCopyMessage(String input) {

		int len = input.length();
		StringBuilder builder;
		int i = 5;
		
		//Extract key
		builder = new StringBuilder();
		while (input.charAt(i) != ' ') { //move thru key
			builder.append(input.charAt(i));
			i++;
		}
		String key = builder.toString();
		
		//Extract value
		builder = new StringBuilder();
		i++; //move past space between key and value
		while (input.charAt(i) != ' ') { //move thru value
			builder.append(input.charAt(i));
			i++;
		}
		String value = builder.toString();
		
		//Extract requestingnodeid
		i++; //move past space between value and reqNodeId
		String reqNodeId = input.substring(i, i+1);
		int reqNodeIdx = node.getIndexFromId(reqNodeId);
		
		//Determine if input is req or ack messages
		int reqat = input.lastIndexOf("req");
		int ackat = input.lastIndexOf("ack");
		int reqorackat = Math.max(reqat, ackat);
		
		//Extract timestamp of update operation invocation
		builder = new StringBuilder();
		i  = reqorackat + 4; //move to the timestamp immediately after req/ack
		while (i < len && input.charAt(i) != ' ') { //move thru timestamp
			builder.append(input.charAt(i));
			i++;
		}
		long writets = Long.parseLong(builder.toString());
		
		
		if (reqat != -1) { //req
			//insert this into sharedData, then print
			//"Specially inserted <key> for eventual consistency update"
			node.sharedData.put(key, new Datum(value,writets));
			System.out.println("Server: Specially inserted "+key+" for eventual consistency update");
			
			//return an ack of same form, but with ack instead of req
			String ack = new String(input.substring(0, reqat) +"ack "); //copy key value reqNodeId reqnum ack
			ack = ack + input.substring(reqat + 4); //timestamp
			long ts = System.currentTimeMillis();
			addMessageToNodeQueue(new MessageType(ack+" "+ts, ts), reqNodeIdx);
		}
		
		else { //ack
			//print "Key <key> updated to <value>"
			System.out.println("Client: Key "+key+" updated to "+value);
			
			//send "writeglobal <key> <value> <timestamp>" (the original ts of message) to leader
			String writeglobal = "writeglobal "+key+" "+value+" "+writets;
			addMessageToLeaderQueue(new MessageType(writeglobal, new Long(0)));
		
			//mark cmdComplete as true
			cmdComplete = true;
		}

	}
	
	
	/*
	 * When a MessageReceiverThread receives a search message,
	 * this method either sends a reply, processes a reply, or completes a search operation
	 * Message form: search key <requestingnodeid> <requestnumber> <NodeId> <reqorack> <timestamp>
	 * Updates this.cmdComplete when the necessary number of acks have been received
	 */
	public void respondToSearchMessage(String input) {
		
		//Extract key
		StringBuilder builder = new StringBuilder();
		int i = 7;
		while (input.charAt(i) != ' ') { //move thru key
			builder.append(input.charAt(i));
			i++;
		}
		String key = builder.toString();
		
		//Extract identifier
		i += 3; //move past space,reqNodeId,space
		while (input.charAt(i) != ' ') //move past reqnum
			i++;
		//This format of the message represents a unique identifier for the request
		String identifier = input.substring(0,i);
		
		//See if this node has key in replica
		Datum replica = node.sharedData.get(key);
		
		int reqat = input.lastIndexOf("req");
		int ackat = input.lastIndexOf("ack");
		int reqorackat = Math.max(reqat, ackat);

		
		if (reqat != -1) { //req
						
			//if this node doesn't have the key in sharedData, put "null" as <myNodeId>
			String contains = (replica == null) ? "null" : nodesinfo[myIdx].id;
			
			//respond with an ack of the form
			//search key <requestingnodeid> <requestnumber> <myNodeId> ack <timestamp>
			String ack = new String(input.substring(0, reqorackat) + contains + " ack ");
			ack = ack + input.substring(reqorackat + 4); //<timestamp>
			
			//Since we don't want to delay this, our timestamp will be impossibly small
			addMessageToLeaderQueue(new MessageType(ack, new Long(0))); //adds senttimestamp
			
		}
		
		else { //ack
			
			AckTracker acks = node.recvacks.get(identifier);
			//look in recvacks to see how many acks we have received with identifier
			
			if (acks == null || acks.toreceive < 1) {
				//do nothing
			}
			else if (acks.toreceive == 1) { //recvacks has 1 ack to receive
				//store 0 in recvacks
				acks.toreceive = 0;
				node.recvacks.put(identifier, acks);
				
				//print out "Key exists in these replicas: "
				System.out.print("Key "+key+" exists in these replicas: ");
				//print this Node's id if it has key
				if (replica != null)
					System.out.print(nodesinfo[myIdx].id + " ");
				
				//print out <myNodeId> for this ack if it's not "null"
				if (input.lastIndexOf("null") == -1)
					System.out.print(input.substring(reqorackat-2, reqorackat-1) + " ");
				
				//print out all the nodeids stored in recvacks' validacks ArrayList
				for (int idx = 0; idx<acks.validacks.size(); idx++) {
					String ack = acks.validacks.get(idx);
					int nodeIdat = ack.lastIndexOf("ack") - 2;
					System.out.print(ack.substring(nodeIdat, nodeIdat+1) + " ");
				}
				
				System.out.println();
				
				//mark cmdComplete as true
				cmdComplete = true;
			}
			else { //recvacks has more than 1 more ack to receive
				
				if (input.lastIndexOf("null") == -1) //this ack does not say null
					acks.validacks.add(input);
				
				acks.toreceive--;
				node.recvacks.put(identifier, acks);

			}

		}

	}
	
	
	/*
	 * When a MessageReceiverThread receives a repair message,
	 * this method makes sure this Node's sharedData is up-to-date
	 * with the most recent write in the system
	 * Message form: repair <key> <value> <associatedtimestamp> <key> <value> <associatedtimestamp> ...
	 * This is one of the few/only messages without a <senttimestamp> field at the end of it
	 *      so that when we reach the end of the keys, we know
	 */
	public void respondToRepairMessage(String input) {

		//Parse out sent timestamp of message
		int i = input.length()-1;
		while (input.charAt(i)!= ' ') //move thru timestamp
			i--;
		input = input.substring(0, i); //remove timestamp
		
		int len = input.length();
		i = 7;
		StringBuilder builder;
		
		while (i < len) { //for every key in the message
			
			//Extract key
			builder = new StringBuilder();
			while (i < len && input.charAt(i) != ' ') { //move thru key
				builder.append(input.charAt(i));
				i++;
			}
			String key = builder.toString();
			i++; //move past space between key and value
			
			//Extract value
			builder = new StringBuilder();
			while (i < len && input.charAt(i) != ' ') { //move thru value
				builder.append(input.charAt(i));
				i++;
			}
			String value = builder.toString();
			i++; //move past space between value and assocts
			
			//Extract associated timestamp
			builder = new StringBuilder();
			while (i < len && input.charAt(i) != ' ') { //move thru assocts
				builder.append(input.charAt(i));
				i++;
			}
			long ts = Long.parseLong(builder.toString());
			
			i++; //move past potential space between assocts and key
			
			
			Datum curvalue = node.sharedData.get(key);
			
			//if it's not in this Node's sharedData
			//or its associated timestamp in sharedData is less recent than the one in the message
			if (curvalue == null || curvalue.timestamp < ts) {
				//update sharedData with the message's value and associated timestamp
				node.sharedData.put(key, new Datum(value, ts));
			}
			//else this Node's sharedData has the more recent write info, so do nothing
			
		}

	}
	
}
