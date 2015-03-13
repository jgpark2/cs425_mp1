package mp1;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
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
    				System.out.print("Now connected to "+nodesinfo[i].ip+":"+nodesinfo[i].port);
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
				System.out.print("Now connected to "+leaderInfo.ip+":" +leaderInfo.port);
				System.out.println(" (leader)");
				node.setToLeaderSendingThread(new MessageSenderThread(node, mqleader, leaderconn));
			} catch (Exception e) {
				leaderconn = null;
			}
		}

		
		//Get commands from either System.in or an input file
		String cmd = "";
		
        try {
			while ((cmd = cmdin.readLine()) != null) { //no longer support exit, just read until end of input file

				//Discard any old references and make a new MT object
				MessageType msg = new MessageType(cmd, System.currentTimeMillis());
				cmdComplete = false;
				
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
						System.out.println("Message was not correctly fomatted; try again");
						continue;
					}
				}
				
				//do not restart the loop until the command is finished being processed
				while (!cmdComplete) {}
			}
		} catch (IOException e) {
			System.out.println("Failed to get command");
			e.printStackTrace();
			return;
		}
        
        System.out.println("CommandInputThread reached end of file, exiting");
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
		//Calculate random delay
		int randint = r.nextInt(intmaxdelays[recvIdx]); //random number of milliseconds
				
		//if last[recvIdx] is no longer in the channel, its ts will definitely be smaller
		msg.ts = new Long(Math.max(msg.ts + (long)randint, last[recvIdx].ts.longValue()));
			
		try {
			mqnodearr.get(recvIdx).put(msg);
			last[recvIdx] = msg;
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
		this.cmdComplete = true;
	}
	
	
	/*
	 * If the command is of the form "get key model", this
	 * method appends the relevant information to the message
	 * and sends the message to the CentralServer
	 * This method assumes that parseForIncorrectFormat has already been called
	 * final format: get key model <requestingnodeid> <requestnumber> <reqorack>
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
					System.out.print("get("+key+") = (NO KEY FOUND)");
				else
					System.out.println("get("+key+") = "+value.value);
				
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
					System.out.print("get("+key+") = ("+value.value+", ");
					SimpleDateFormat format = new SimpleDateFormat("HH:mm:ss.SSS");
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
				System.out.println("Message was not correctly fomatted in parseGet; try again");
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
				break;
			}
			
			case 2: {
				//sequential consistency: send to CentralServer, wait for 4 acks to be received
				//This format of the message represents a unique identifier for the request
				msg.msg = msg.msg + " " + nodesinfo[myIdx].id + " " + node.reqcnt;
				node.recvacks.put(msg.msg, new AckTracker(4));
				msg.msg = msg.msg + " " + "req";
				addMessageToLeaderQueue(msg); //this method adds the timestamp
				break;
			}
			
			case 3: {
				//eventual consistency, W=1: only wait for 1 replica to write (ours)
				
				Datum toinsert = new Datum(value, msg.ts.longValue());
				node.sharedData.put(key, toinsert);
				System.out.println("Inserted key "+key);
				
				//We need to tell them all to write, but we don't need to wait for the acks
				//This format of the message represents a unique identifier for the request
				msg.msg = msg.msg + " " + nodesinfo[myIdx].id + " " + node.reqcnt;
				node.recvacks.put(msg.msg, new AckTracker(0));
				msg.msg = msg.msg + " " + "req" + " " + msg.ts.longValue();
				for (int idx=0; idx<4; idx++) {
					if (idx != myIdx)
						addMessageToNodeQueue(msg, idx); //this method adds no timestamp
				}
				
				System.out.println("Inserted key "+key);
				this.cmdComplete = true;
				break;
			}
			
			case 4: {
				//eventual consistency, W=2: send to 1 other Node, wait for 1 ack
				
				Datum toinsert = new Datum(value, msg.ts.longValue());
				node.sharedData.put(key, toinsert);
				System.out.println("Inserted key "+key);
				
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
				System.out.println("Message was not correctly fomatted in parseInsert; try again");
				this.cmdComplete = true;
				return;
			}
		}
		
		//In addition, regardless of the consistency model, this insert should occur in globalData
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
					System.out.println("Key "+key+" changed from "+old.value+" to "+value);
					
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
					System.out.println("Key "+key+" changed from "+old.value+" to "+value);

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
				System.out.println("Message was not correctly fomatted; try again");
				this.cmdComplete = true;
				return;
			}
		}
				
		//Both linearizability and sequential consistency run this:
		if (node.sharedData.get(key) == null) { //key does not exist in system
			System.out.println("Key "+key+" does not exist in system, attempted update failed");
			return;
		}
		
		//This format of the message represents a unique identifier for the request
		msg.msg = msg.msg + " " + nodesinfo[myIdx].id + " " + node.reqcnt;
		node.recvacks.put(msg.msg, new AckTracker(4));
		msg.msg = msg.msg + " " + "req";
		addMessageToLeaderQueue(msg); //this method adds the timestamp
		
		MessageType writeglobal = new MessageType("", msg.ts);
		writeglobal.msg = "writeglobal "+key+" "+value;
		addMessageToLeaderQueue(writeglobal); //this method adds the timestamp
		
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
			System.out.println("Message was not correctly fomatted; try again");
			this.cmdComplete = true;
			return;
		}
		
		StringWriter writer = new StringWriter();
		for (int i=5; i<(len-2); i++)
			writer.append(cmd.msg.charAt(i));
		cmd.msg = writer.toString();
		
		addMessageToNodeQueue(cmd, recvIdx);
		System.out.print("Sent \""+cmd.msg+"\" to "+nodesinfo[recvIdx].id+", system time is ");
		SimpleDateFormat format = new SimpleDateFormat("HH:mm:ss.SSS");
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
			System.out.println("Server id must be one of: A,B,C,D");
			//System.out.println(" cmd did not have serverid");
			return -1;
		}
		
		//TODO: ignore messages designated to myself->later i think we CAN send messages to our self, just dont delay it or anything
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
		
		addMessageToLeaderQueue(msg); //this method adds the timestamp
	}
	
	
	/*
	 * When a MessageReceiverThread receives a get message,
	 * this method either sends a reply, completes a get operation, or does nothing
	 * Updates this.cmdComplete when the necessary number of acks have been received
	 */
	public void respondToGetMessage(String input) {
		
		int len = input.length();
		StringBuilder builder;
		int i = 4;
		
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
		
		
		//Determine if input is req or ack messages
		int reqat = input.lastIndexOf("req");
		int ackat = input.lastIndexOf("ack");
		int reqorackat = Math.max(reqat, ackat);
		
		
		//value (behind req/ack string
		
		//Extract timestamp of get operation invocation
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
		
		//GET
		switch (model) {
			case 1:	//linearizability: (the request only gets sent back to the requestingNode)
				if (reqat != -1) { //req
					//print out "get(<key>) = <value>" from sharedData
					System.out.println("get("+key+") = "+node.sharedData.get(key).value);
					
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
					String ack = new String(input.substring(0, reqat) + val + " ");
					if (assocTS==0)
						ack = ack + "null ack ";
					else
						ack = ack + assocTS + " ack ";
					
					//ack = ack + input.substring(reqat + 4); //... <timestamp>
					
					long ts = System.currentTimeMillis();
					addMessageToNodeQueue(new MessageType(ack+" "+ts, ts), reqNodeIdx);
					
				}
				else { //ack
					//look in recvacks to see how many acks we have received with identifier:
					//get key model <requestingnodeid> <requestnumber>
					if (acks == null || acks.toreceive < 1) {
						//do nothing
					}
					else if (acks.toreceive==1 && input.lastIndexOf("null") == -1) { //TODO can assocts be null but value be not null?
						//if recvacks has 1 more ack to receive (R=1) and ack doesn't have "null" as value,
						//store 0 in recvacks
						acks.toreceive = 0;
						node.recvacks.put(identifier, acks);
						//print "get(<key>) = (<value>, <associatedvaluetimestamp>)"
						//TODO replace node.sharedData with the input message????
						System.out.println("get("+key+") = ("+node.sharedData.get(key).value+", "+node.sharedData.get(key).timestamp+")");
						//then print "(<value>, <associatedvaluetimestamp>)" on a new line...
						System.out.println("("+node.sharedData.get(key).value+", "+node.sharedData.get(key).timestamp+")");
						cmdComplete = true;
					}
					else if (acks.toreceive==1 && input.lastIndexOf("null") != -1) {
						//if recvacks has 1 more ack to receive (R=1) and ack has "null" as value,
						//store 1 more null ack received in recvacks' nullacks ArrayList
						acks.nullacks.add(identifier);
						
						if(acks.nullacks.size()>=3){
							//if 3 such null acks have been received now,
							//print "get(<key>) = (NO KEY FOUND)"
							System.out.println("get("+key+") = (NO KEY FOUND)");
							//store 0 in recvacks
							acks.toreceive = 0;
							
							cmdComplete = true;
						}
						
						node.recvacks.put(identifier, acks);
					}
					else {
						//yay						
					}
				}
				break;
				
			case 4:	//eventual consistency R=2:
				//req: same as req for eventual consistency R=1
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
					String ack = new String(input.substring(0, reqat) + val + " ");
					if (assocTS==0)
						ack = ack + "null ack ";
					else
						ack = ack + assocTS + " ack ";
					
					//ack = ack + input.substring(reqat + 4); //... <timestamp>
					
					long ts = System.currentTimeMillis();
					addMessageToNodeQueue(new MessageType(ack+" "+ts, ts), reqNodeIdx);
					
				}
				else {	//ack: look in recvacks to see how many acks we have received with identifier:
					//get key model <requestingnodeid> <requestnumber>
					if (acks != null && acks.toreceive == 2 && input.lastIndexOf("null") == -1) {
						//if recvacks has 2 more acks to receive (R=2) and this ack doesn't have "null" as value,
						//store 1 in recvacks
						acks.toreceive = 1;
						//store the received (value,associatedtimestamp) in recvacks' validacks ArrayList
						acks.validacks.add(input);
						
						node.recvacks.put(identifier, acks);
					}
					else if (acks.toreceive == 2 && input.lastIndexOf("null") != -1) {
						//if recvacks has 2 more acks to receive (R=2) and this ackhas "null" as value,
						//store 1 more null ack received recvacks' nullacks ArrayList
						acks.toreceive+=1;
						
						//if 3 such null acks have been received now,
						if(acks.nullacks.size()>=3) {
							//print "get(<key>) = (NO KEY FOUND)"
							System.out.println( "get("+key+") = (NO KEY FOUND)");
							//store 0 in recvacks
							acks.toreceive = 0;
							
							cmdComplete = true;
						}
						node.recvacks.put(identifier, acks);
					}
					else if (acks.toreceive == 1 && input.lastIndexOf("null") == -1){
						//if recvacks has 1 more ack to receive and this ack doesn't have "null" as value,
						//store 0 in recvacks
						acks.toreceive = 0;
						
						//compare the previously received ack (stored in recvacks' validacks ArrayList) and the current received one
						//Technically previously received one is the last index, but since we do it on 2nd msg receive, it's also the first index
						
						//print "get(<key>) = (<value>, <associatedvaluetimestamp>)" for the more recent one
						//Extract timestamp
						String prevAssocTS = acks.validacks.get(0);
						int j = prevAssocTS.lastIndexOf("ack")-2;
						StringBuilder build = new StringBuilder();
						while (prevAssocTS.charAt(j) != ' ') { //move thru key
							build.append(prevAssocTS.charAt(j));
							j--;
						}
						
						long prevTS = Long.parseLong(build.toString());
						
						//TODO replace node.sharedData with the input message????
						if (prevTS < writets) {
							//writets is more recent
							System.out.println("get("+key+") = ("+node.sharedData.get(key).value+", "+node.sharedData.get(key).timestamp+")");
						}
						else
							System.out.println("get("+key+") = ("+node.sharedData.get(key).value+", "+node.sharedData.get(key).timestamp+")");
							
						
						//print "(<value>, <timestamp>)" for the both
						cmdComplete = true;
					}
					else if (acks.toreceive == 1 && input.lastIndexOf("null") != -1){
						//if recvacks has 1 more ack to receive and this ack has "null" as value,
						//TODO:
						//if 1 such null ack has already been received:
							//print "get(<key>) = (<value>, <associatedvaluetimestamp>)" for the non-null ack in recvacks' validacks ArrayList
							//store 0 in recvacks
							//mark cmdComplete as true
						//if no such null acks have been received yet:
							//store 1 more null ack received in recvacks' nullacks ArrayList
					}
				}
		}
		
		//System.out.println("Received get message: "+input);
	}
	
	
	/*
	 * When a MessageReceiverThread receives an insert message,
	 * this method either sends a reply, completes an insert operation, or does nothing
	 * Updates this.cmdComplete when the necessary number of acks have been received
	 */
	public void respondToInsertMessage(String input) {
		// TODO Implement
		//INSERT
		//linearizability:
			//req: insert the key and value and attached timestamp into sharedData, print "Inserted key <key>"
				//send an ack to leader of form, where <senttimestamp> is time when this Node sent message to leader
				//insert key value model <requestingnodeid> <requestnumber> ack <timestamp> <senttimestamp>
			//ack: look in recvacks to see how many acks we have received with identifier:
				//insert key value model <requestingnodeid> <requestnumber>
				//if recvacks has more than 1 more acks to receive, store that number decremented
				//if recvacks has 1 more ack to receive, store 0 in recvacks and print "Inserted key <key>"
					//mark cmdComplete as true
	
		//sequential consistency: same as linearizability
	
		//eventual consistency W=1:
			//req: insert the key and value and attached timestamp into sharedData, print "Inserted key <key>"
				//an ack is unnecessary because the requester only checked 1 insert (itself)
			//ack: not applicable, see line above
	
		//eventual consistency W=2:
			//req: insert the key and value and attached timestamp into sharedData, print "Inserted key <key>"
				//send an ack to requesting node of form (don't have to append any timestamp)
				//insert key value model <requestingnodeid> <requestnumber> ack <timestamp>
			//ack: look in recvacks to see how many acks we have received with identifier:
				//insert key value model <requestingnodeid> <requestnumber>
				//if recvacks has 1 more ack to receive, store 0 in recvacks
					//print "Inserted key <key>"
					//mark cmdComplete as true
				//if recvacks has 0, do nothing
		System.out.println("Received insert message: "+input);
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
					System.out.println("Key "+key+" changed from "+old.value+" to "+value);					
					
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
						System.out.println("Key "+key+" updated to "+value);
						
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
						//update key model <requestingnodeid> <requestnumber> ack null <timestamp> <senttimestamp>
						String ack = identifier+" ack null "+input.substring(reqorackat + 4);
						long ts = System.currentTimeMillis();
						addMessageToNodeQueue(new MessageType(ack+" "+ts, ts), reqNodeIdx);
					}
					
					else {
						//update the copy of key in sharedData to value
						node.sharedData.put(key, new Datum(value,writets));
						
						//print "Key <key> changed from <oldvalue> to <value>"
						System.out.println("Key "+key+" changed from "+oldvalue.value+" to "+value);
						
						//respond to requestingnode on peer channel with an ack of the form
						//update key value model <requestingnodeid> <requestnumber> ack <timestamp> <senttimestamp>
						String ack = identifier+" ack "+input.substring(reqorackat + 4);
						long ts = System.currentTimeMillis();
						addMessageToNodeQueue(new MessageType(ack+" "+ts, ts), reqNodeIdx);
					}					
				}
				
				else { //ack
					
					// TODO Implement
					
					//look in recvacks to see how many acks we have received with identifier
					if (acks == null || acks.toreceive < 1) {
						//do nothing
					}
					else {
						
						if (input.lastIndexOf("null") == -1) { //this ack does not say null
							//store 0 in recvacks and print "Key <key> updated to <value>"
							//send "writeglobal <key> <value> <timestamp>" (the original ts of message) to leader
							//mark cmdComplete as true
						}
						else { //this ack says null
							//store 1 more null ack received in recvacks' nullacks ArrayList
							//if 3 such null acks have been received,
								//print "Key <key> does not exist in system, attempted update failed"
								//store 0 in recvacks
								//mark cmdComplete as true
						}
						
					}
					
				}
				
				break;
			}
			case 4: { //eventual consistecy W=2:
				
				if (reqat != -1) { //req
					
					//req: if this Node's sharedData has a copy of the key, update it to value
					//print "Key <key> changed from <oldvalue> to <value>"
					//respond to requestingnode on peer channel with an ack of the form
					//update key value model <requestingnodeid> <requestnumber> ack <timestamp> <senttimestamp>
				//req: if this Node's sharedData does not have a copy of the key,
					//respond to requestingnode on peer channel with an ack of the form
					//update key model <requestingnodeid> <requestnumber> ack null <timestamp> <senttimestamp>
					
				}
				
				else { //ack
					
					//ack: look in recvacks to see how many acks we have received with identifier:
					//update key model <requestingnodeid> <requestnumber>
					//if recvacks has 2 more acks to receive, and this ack does not say null after ack
						//store 1 in recvacks
					//if recvacks has 2 more acks to receive, and this ack says null after ack
						//store 1 more null ack received in recvacks' nullacks ArrayList
						//if 3 such null acks have been received, print
							//"Key <key> does not exist in system, attempted update failed"
							//store 0 in recvacks
							//then mark cmdComplete as true
					//if recvacks has 1 more ack to receive, and this ack does not say null after ack
						//store 0 in recvacks, print "Key <key> updated to <value>"
						//send "writeglobal <key> <value> <timestamp>" (the original ts of message) to leader
						//mark cmdComplete as true
					//if recvacks has 1 more ack to receive, and this ack says null after ack
						//store 1 more null ack received in recvacks' nullacks ArrayList
						//if 3 such null acks have now been received, send out a special message to 1 Node
							//telling them to insert this key/value/timestamp
							//wait until you have received the ack for this, then print
							//"Key <key> updated to <value>"
							//store 0 in recvacks
							//send "writeglobal <key> <value> <timestamp>" (the original ts of message) to leader
							//mark cmdComplete as true
						//if 2 such null acks have now been received and sharedData does not have this key,
							//insert this key/value/<timestamp> into sharedData
							//print "Key <key> changed from null to <value>"
							//print "Key <key> updated to <value>"
							//store 0 in recvacks
							//send "writeglobal <key> <value> <timestamp>" (the original ts of message) to leader
							//mark cmdComplete as true
					//if recvacks has 0 more acks to receive, do nothing
					
				}

				break;
			}
		}
		
		System.out.println("Received update message: "+input);
	}
	
	
	/*
	 * When a MessageReceiverThread receives a search message,
	 * this method either sends a reply, processes a reply, or completes a search operation
	 * Updates this.cmdComplete when the necessary number of acks have been received
	 */
	public void respondToSearchMessage(String input) {
		// TODO Implement
		//SEARCH
		//req: respond with an ack of the form
			//search key <requestingnodeid> <requestnumber> <myNodeId> ack <timestamp>
			//if this node doesn't have the key in sharedData, put "null" as <myNodeId> (else put this node's id)
		//ack: look in recvacks to see how many acks we have received with identifier:
			//search key <requestingnodeid> <requestnumber>
			//if recvacks has more than 1 more ack to receive, and this ack does not say "null"
				//store that number decremented in recvacks, store <myNodeId> in recvacks' validacks ArrayList
			//if recvacks has more than 1 more ack to receive, and this ack says "null"
				//store that number decremented in recvacks
			//if recvacks has 1 ack to receive, store 0 in recvacks and
				//print out "Key exists in these replicas: "
				//this Node's id if it has key, then print out <myNodeId> for this ack if it's not "null"
				//then print out all the nodeids stored in recvacks' validacks ArrayList
				//mark cmdComplete as true
		System.out.println("Received search message: "+input);
	}
	
	
	/*
	 * When a MessageReceiverThread receives a repair message,
	 * this method makes sure this Node's sharedData is up-to-date
	 * with the most recent write in the system
	 * Updates this.cmdComplete when the necessary number of acks have been received
	 */
	public void respondToRepairMessage(String input) {
		// TODO Implement
		//REPAIR: repair <key> <value> <associatedtimestamp> <key> <value> <associatedtimestamp> ...
		//for every key in the message
			//if it's not in this Node's sharedData
			//or its associated timestamp in sharedData is less recent than the one in the message
				//update sharedData with the message's value and associated timestamp
			//else this Node's sharedData has the more recent write info, so do nothing
		//System.out.println("Received repair message: "+input);
	}
	
}
