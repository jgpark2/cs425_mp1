package mp1_1;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Queue;
import java.util.Random;
import java.io.StringWriter;
import java.util.Date;
import java.text.SimpleDateFormat;

public class MessageThread extends Thread
{
	//Sends messages after applying delay
	
	NodeThreads t;
	Queue<String> mq; //queue to hold a delayed message
	Queue<Long> tq; //queue to hold when a message should be delivered
	
	private NodeInfo[] nodesInfo;
   	private NodeInfo myInfo;
   	private NodeInfo serverNode; //node that this messagethread is sending messages to
   	private Socket client;
   	
	protected PrintWriter outs;
	protected BufferedReader ins;
	protected BufferedReader sysIn;
	
	
	MessageThread(NodeInfo[] allNodes, NodeInfo curNode, NodeInfo serverNode, Socket mySocket, BufferedReader systemInput) {
		nodesInfo = allNodes;
		myInfo = curNode;
		this.serverNode = serverNode;
		client = mySocket;
		
		sysIn = systemInput;
    	
		//this.t = t;
		//new Thread(this, "Answer").start();
	}
	
	@Override
	public void run() {
		String msgInput=null, msgOutput = null;
		
		//While the server keeps writing to us on its own output Stream...
		try {
			
			outs = new PrintWriter(client.getOutputStream(), true);
			ins = new BufferedReader(new InputStreamReader(client.getInputStream()));
			
			//sysIn = new BufferedReader(new InputStreamReader(System.in));
			
			//send this node's id to server so it can know who it's connected to
			outs.println(myInfo.id); //gets received in ServerThread:line 70
			
			
			while(true) {
				System.out.print("Waiting for server");
				while (msgInput == null) {
					msgInput = ins.readLine();
					Date now = new Date();
					SimpleDateFormat format = new SimpleDateFormat("HH:mm:ss:SSS");
					//System.out.println("Server" + serverNode.id+": " + msgInput);
					System.out.print("Received \""+msgInput+"\" from " + serverNode.id);
					System.out.print(", Max delay is " + myInfo.max_delay + " s, ");
					System.out.println("system time is "+format.format(now));
				
					if (msgInput.equals("Bye")) //Received disconnect Msg
						break;
				}
				msgInput = null;

				
				while(msgOutput == null) {
					System.out.print("Waiting for input");
					msgOutput = sysIn.readLine(); //Grab command line input
					msgOutput = parseCommand(msgOutput);
					//System.out.println("After it's parsed, msgOutput is \""+msgOutput+"\"");
				}
				if (msgOutput.compareTo("")==0) //Special case where we read a message our thread doesn't care about
					continue;
				//System.out.println("Client: " + msgOutput);
				System.out.print("Sent \""+msgOutput+"\" to "+serverNode.id+", system time is ");
				SimpleDateFormat format = new SimpleDateFormat("HH:mm:ss:SSS");
				Date now = new Date();
				System.out.println(format.format(now));
				//addMessageToQueue(msgOutput);
				outs.println(msgOutput);
				
				msgOutput = null;
				
			}
			
			
		} catch (IOException e) {
			System.out.println("With "+ serverNode.id+", Socket reading error");
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
			System.out.println("Connection Closed with "+serverNode.id);
	    }	
		/*while (true)
			sendQueuedMessages();*/
	}
	
	private String parseCommand(String cmd) { //"Send Hello B"
		int len;
		if ((len = cmd.length()) < 6) {
			//System.out.println("cmd was too short");
			return null; //null for re-input
		}
		
		if (cmd.charAt(len-2) != ' ') {
			//System.out.println("cmd did not have space");
			return null; //null for re-input
		}
		
		StringWriter id = new StringWriter();
		id.append(cmd.charAt(len-1));
		//Only allow A, B, C, or D as servers
		if (id.toString().compareToIgnoreCase("A")!=0 && id.toString().compareToIgnoreCase("B")!=0
				&& id.toString().compareToIgnoreCase("C")!=0 && id.toString().compareToIgnoreCase("D")!=0) {
			System.out.print("Server id must be one of: A,B,C,D");
			//System.out.println(" cmd did not have serverid");
			return null; //null for re-input
		}
		//TODO: ignore messages designated to myself->later i think we CAN send messages to our self, just dont delay it or anything
		if (id.toString().compareToIgnoreCase(myInfo.id) == 0) {
			return null;
		}
		
		//Only manage messages that are supposed to be sent to the destination we handle.
		//Check this last since we want to check this AFTER we know that the destination input is valid to begin with
		if (id.toString().compareToIgnoreCase(serverNode.id) != 0) {
			//System.out.print("id: \"" + id.toString() + "\", serverid: "+serverNode.id);
			//System.out.println(" cmd did not have serverid");
			return ""; //empty string signifies that this message will be sent by another MessageThread
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
	
	
	public void addMessageToQueue(String m) {
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
				if (t <= System.currentTimeMillis()) { //time to send this message
					String m = mq.remove();
					t = tq.remove();
					
					//SEND m
					outs.println(m);
				}
				else break; //no messages need to be send right now
			}
		}
	}

	
	
}
