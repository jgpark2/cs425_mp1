package mp1_1;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.Date;

/*
 * MessageReceiverThread: 3 per Node object
 * Receives messages from a socket connection
 */
public class MessageReceiverThread extends Thread {
	
	private Node node;
	private NodeInfo [] nodesinfo;
	private int myIdx; //index into NodeInfo array
	private int recvIdx; //index into NodeInfo array
	
	private Socket client;
	private BufferedReader ins;
	

	public MessageReceiverThread(Node node, Socket socket) {
		this.node = node;
		this.nodesinfo = node.getNodesInfo();
		myIdx = node.myIdx;
		client = socket;
		
		new Thread(this, "ReceiveMessage").start();
	}
	

	@Override
	public void run() {
		
		try {
			ins = new BufferedReader(new InputStreamReader(client.getInputStream()));
		} catch (IOException e) {
			System.out.println("Could not initialize socket streams");
			e.printStackTrace();
			try {
				client.close();
				ins.close();
			} catch (IOException e1) {
				e1.printStackTrace();
			}
			return;
		}
		
		String input = "";
		//exchange myInfo/recvInfo with socket
		try {
			while ((input = ins.readLine())==null) {}
			recvIdx = Integer.parseInt(input);
			//System.out.println("Received "+recvIdx+" as recvIdx");
			node.setReceivingThreadIndex(recvIdx, this);
		} catch (IOException e1) {
			System.out.println("Could not receive initial info exchange");
			e1.printStackTrace();
			try {
				client.close();
				ins.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
			return;
		}
		
		try {
			//while delayer keeps writing to its output stream...
			while (((input = ins.readLine()) != null) && (input.compareToIgnoreCase("exit") != 0)) {
				
				Date now = new Date();
				SimpleDateFormat format = new SimpleDateFormat("HH:mm:ss:SSS");
		    	
		    	System.out.print("Received \""+input+"\" from " + nodesinfo[recvIdx].id);
				System.out.print(", Max delay is " + nodesinfo[myIdx].max_delay + " s, ");
				System.out.println("system time is "+format.format(now));
			}
		} catch (IOException e) {
			e.printStackTrace();
			return;
		}
		
		//This code should get run if we intend all nodes to exit at the same time
		CommandInputThread cmdint = node.getCommandInputThread();
		MessageType msg = new MessageType("exit", new Long(0));
		cmdint.addMessageToAllQueues(msg);
		
		System.out.println("MessageReceiverThread from node "+nodesinfo[recvIdx].id+" received exit, exiting");
		try {
			client.close();
			ins.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
	}
	
}
