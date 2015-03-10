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
	
	private BufferedReader sysin;
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
    

	//TODO: add support for inputfis
	public CommandInputThread(Node node, FileInputStream inputfis) {
		this.node = node;
    	myIdx = node.myIdx;
    	nodesinfo = node.getNodesInfo();
    	leaderInfo = node.leaderInfo;
    	
		sysin = new BufferedReader(new InputStreamReader(System.in));
		
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

		//Get message commands from System.in
		MessageType msg = new MessageType("", new Long(0));
		
        while (msg.msg.compareToIgnoreCase("exit") != 0) {

        	try {
				msg.msg = sysin.readLine();
				cmdComplete = false;
				msg.ts = System.currentTimeMillis();
			} catch (IOException e) {
				e.printStackTrace();
			}
        	
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
        		default: {
        			System.out.println("Message was not correctly fomatted; try again");
        			continue;
        		}
        	}
        	
        	//do not restart the loop until the command is finished being processed
        	while (!cmdComplete) {}
        }
        
        //Send exit message to all channels ?
        msg = new MessageType("exit", new Long(0));
        addMessageToAllQueues(msg);
        System.out.println("CommandInputThread received \"exit\", exiting");
        try {
			sysin.close();
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
				
				//This format of the message represents a unique identifier for the request
				msg.msg = msg.msg + " " + nodesinfo[myIdx].id + " " + node.reqcnt;
				node.recvacks.put(msg.msg, 1);
				msg.msg = msg.msg + " " + "req";
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
				
				if (value == null) //key is not in replica
					System.out.print("get("+key+") = (NO KEY FOUND)");
				
				else {
					System.out.print("get("+key+") = ("+value.value+", ");
					SimpleDateFormat format = new SimpleDateFormat("HH:mm:ss.SSS");
					Date curdate = new Date(value.timestamp);
					System.out.println(format.format(curdate) + ")");
				}
				
				this.cmdComplete = true;
				break;
			}
			
			case 4: {
				//eventual consistency, R=2: send to 1 other Nodes, wait for 1 ack
				
				//This format of the message represents a unique identifier for the request
				msg.msg = msg.msg + " " + nodesinfo[myIdx].id + " " + node.reqcnt;
				node.recvacks.put(msg.msg, 1);
				msg.msg = msg.msg + " " + "req" + " " + msg.ts.longValue();
				addMessageToNodeQueue(msg, (myIdx + 1)%4);
				break;
			}
			
			default: {
				System.out.println("Message was not correctly fomatted; try again");
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
		//Extract model out of msg
		String modelstr = msg.msg.substring(msg.msg.length()-1);
		int model = Integer.parseInt(modelstr);
		
		node.reqcnt++;
		
		switch (model) {
			case 1: {
				//linearizability: send to CentralServer, wait for 3 acks to be received
				break;
			}
			
			case 2: {
				//sequential consistency: send to CentralServer, wait for 3 acks to be received
				break;
			}
			
			case 3: {
				//eventual consistency, W=1: send to 1 replica (ours)
				
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
				
				Datum toinsert = new Datum(value, msg.ts.longValue());
				node.sharedData.put(key, toinsert);
				System.out.println("Inserted key "+key);
				
				this.cmdComplete = true;
				return;
			}
			
			case 4: {
				//eventual consistency, W=2: send to 1 other Node, wait for 1 ack
				
				//This format of the message represents a unique identifier for the request
				msg.msg = msg.msg + " " + nodesinfo[myIdx].id + " " + node.reqcnt;
				node.recvacks.put(msg.msg, 1);
				msg.msg = msg.msg + " " + "req" + " " + msg.ts.longValue();
				addMessageToNodeQueue(msg, (myIdx + 1)%4);
				return;
			}
			
			default: {
				System.out.println("Message was not correctly fomatted; try again");
				this.cmdComplete = true;
				return;
			}
		}
		
		//Both linearizability and sequential consistency run this:
		//This format of the message represents a unique identifier for the request
		msg.msg = msg.msg + " " + nodesinfo[myIdx].id + " " + node.reqcnt;
		node.recvacks.put(msg.msg, 3);
		msg.msg = msg.msg + " " + "req";
		addMessageToLeaderQueue(msg); //this method adds the timestamp
		
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
				
		switch (model) {
			case 1: {
				//linearizability: send to CentralServer, wait for 3 acks to be received
				break;
			}
					
			case 2: {
				//sequential consistency: send to CentralServer, wait for 3 acks to be received
				break;
			}
					
			case 3: {
				//eventual consistency, W=1: send to 1 replica (ours)
				
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
						
				Datum toinsert = new Datum(value, msg.ts.longValue());
				Datum old = node.sharedData.put(key, toinsert);
				if (old == null) //key was not previously in replica
					System.out.println("Key "+key+" changed from null to "+value);
				else
					System.out.println("Key "+key+" changed from "+old.value+" to "+value);
				
				this.cmdComplete = true;
				return;
			}
					
			case 4: {
				//eventual consistency, W=2: send to 1 other Node, wait for 1 ack
				
				//This format of the message represents a unique identifier for the request
				msg.msg = msg.msg + " " + nodesinfo[myIdx].id + " " + node.reqcnt;
				node.recvacks.put(msg.msg, 1);
				msg.msg = msg.msg + " " + "req" + " " + msg.ts.longValue();
				addMessageToNodeQueue(msg, (myIdx + 1)%4);
				return;
			}
					
			default: {
				System.out.println("Message was not correctly fomatted; try again");
				this.cmdComplete = true;
				return;
			}
		}
				
		//Both linearizability and sequential consistency run this:
		//This format of the message represents a unique identifier for the request
		msg.msg = msg.msg + " " + nodesinfo[myIdx].id + " " + node.reqcnt;
		node.recvacks.put(msg.msg, 3);
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
			System.out.println("cmd was too short");
			return -1;
		}
		
		if (cmd.charAt(len-2) != ' ') {
			System.out.println("cmd did not have space");
			return -1;
		}
		
		StringWriter str = new StringWriter();
		str.append(cmd.charAt(len-1));
		String id = str.toString();
		//Only allow A, B, C, or D as servers
		if (id.compareToIgnoreCase("A")!=0 && id.compareToIgnoreCase("B")!=0
				&& id.compareToIgnoreCase("C")!=0 && id.compareToIgnoreCase("D")!=0) {
			System.out.println("Server id must be one of: A,B,C,D");
			System.out.println(" cmd did not have serverid");
			return -1;
		}
		
		//TODO: ignore messages designated to myself->later i think we CAN send messages to our self, just dont delay it or anything
		if (id.compareToIgnoreCase(nodesinfo[myIdx].id) == 0) {
			return -1;
		}
		
		return node.getIndexFromId(id);
	}


	/*
	 * If the command is of the form "show-all", this method prints displays all
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
	 * When a MessageReceiverThread receives a get/insert/update message,
	 * this method either sends a reply, updates a key/value, or does nothing
	 * Updates this.cmdComplete when the necessary number of acks have been received
	 * Delete messages are taken care of in MessageReceiverThread
	 */
	public void respondToMessage(String input) {
		// TODO Do things with the key/value Dictionary
		
		String output = input;
		
		System.out.println("Responded to "+input);
	}
	
}
