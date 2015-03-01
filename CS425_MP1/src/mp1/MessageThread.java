package mp1;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Queue;
import java.util.Random;
import java.io.StringWriter;

public class MessageThread extends Thread
{
	//Sends messages after applying delay
	
	NodeThreads t;
	Queue<Integer> mq; //whatever object we make to hold messages
	Queue<Long> tq; //queue to hold when a message should be delivered
	
	private NodeInfo[] nodesInfo;
   	private NodeInfo myInfo;
   	private NodeInfo serverNode; //node that this messagethread is sending messages to
   	private Socket client;
   	
	protected PrintWriter outs;
	protected BufferedReader ins;
	protected BufferedReader sysIn;
	
	
	MessageThread(NodeInfo[] allNodes, NodeInfo curNode, NodeInfo serverNode, Socket mySocket) {
		nodesInfo = allNodes;
		myInfo = curNode;
		this.serverNode = serverNode;
		client = mySocket;
    	
		//this.t = t;
		//new Thread(this, "Answer").start();
	}
	
	@Override
	public void run() {
		String msgInput, msgOutput;
		
		//While the server keeps writing to us on its own output Stream...
		try {
			
			outs = new PrintWriter(client.getOutputStream(), true);
			ins = new BufferedReader(new InputStreamReader(client.getInputStream()));
			sysIn = new BufferedReader(new InputStreamReader(System.in));
			
			
			while(true) {
				msgInput = ins.readLine();
				if (msgInput != null) {
					System.out.println("Server: " + msgInput);
				
					if (msgInput.equals("Bye")) //Received disconnect Msg
						break;
				}
				
					msgOutput = sysIn.readLine(); //Grab command line input
					msgOutput = parseCommand(msgOutput);
					if (msgOutput != null) {
						System.out.println("Client: " + msgOutput);
						outs.println(msgOutput);
					}
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
		/*while (true)
			sendQueuedMessages();*/
	}
	
	private String parseCommand(String cmd) { //"Send Hello B"
		int len;
		if ((len = cmd.length()) < 6) {
			//System.out.println("cmd was too short");
			return null;
		}
		
		if (cmd.charAt(len-2) != ' ') {
			//System.out.println("cmd did not have space");
			return null;
		}
		
		StringWriter id = new StringWriter();
		id.append(cmd.charAt(len-1));
		if (id.toString().compareTo(serverNode.id) != 0) {
			//System.out.print("id: \"" + id.toString() + "\", serverid: "+serverNode.id);
			//System.out.println(" cmd did not have serverid");
			return null; //this message will be sent by another MessageThread
		}
		
		id = new StringWriter();
		int i=0;
		for (i=0; i<5; i++) //should be "send "
			id.append(cmd.charAt(i));
		if (id.toString().compareToIgnoreCase("send ") != 0) {
			//System.out.print("id: \"" + id.toString() + "\"");
			//System.out.println("cmd did not have send");
			return null;
		}
		
		id = new StringWriter();
		for (i=5; i<(len-2); i++)
			id.append(cmd.charAt(i));
		
		return id.toString();
	}
	
	
	public void addMessageToQueue(Integer m) {
		//input checking: checking for valid receiver
		
		Long now = System.currentTimeMillis();
		
		String recvid = ""; //get id out of message info?
		NodeInfo recvinfo = t.getNodeInfo(recvid);
		Double millismaxdelay = new Double(recvinfo.max_delay*1000.0);
		int recvmaxdelay = millismaxdelay.intValue();
		
		Random r = new Random();
		int randint = r.nextInt(recvmaxdelay); //random number of milliseconds
		Long randdelay = new Long((long)randint);
		
		Long tosend = now + randdelay;
		
		if (mq.size() > 0) { //previous message is still in the channel
			/*
			 * When a certain message M is sent at time T, suppose that there is some previously sent
			 * message still in the channel (i.e., not delivered to the server at the other end). In particular,
			 * let M2 be the last such message sent on the channel. Then message M should be delivered
			 * at time max(T+R, P), while ensuring the FIFO property for the channel, where P is the
			 * delivery time for M2.
			 */
			int count = 0;
			Long p = new Long(0); //delivery time of last message sent on the channel
			while (count < tq.size()) {
				Long temp = tq.remove();
				if (count == mq.size() - 1)
					p = temp;
				
				tq.add(temp);
				count++;
			}
			
			if (tosend.compareTo(p) < 0) //tosend is less than p; max(tosend,p) = p
				tosend = p;
		}
		
		mq.add(m);
		tq.add(tosend);
	}
	
	private void sendQueuedMessages() {
		while (true) {
			if (!mq.isEmpty()) {
				Long t = tq.peek();
				if (t >= System.currentTimeMillis()) { //time to send this message
					Integer m = mq.remove();
					t = tq.remove();
					
					//SEND m
				}
				else break; //no messages need to be send right now
			}
		}
	}

	
	
}
