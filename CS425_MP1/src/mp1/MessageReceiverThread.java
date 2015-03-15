package mp1;

import java.io.BufferedReader;
import java.io.IOException;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.Date;

/*
 * MessageReceiverThread: 3 per Node object
 * Receives messages from a socket connection and processes them
 */
public class MessageReceiverThread extends Thread {
	
	//The Node that this thread belongs to
	private Node node;
	
	//Structures to hold information from config file
	private NodeInfo [] nodesinfo;
	private int myIdx; //index into NodeInfo array
	
	//Node id of the node that this thread is receiving messages from
	private String recvId;
	
	//Stream from socket connection
	private BufferedReader ins;
	

	public MessageReceiverThread(Node node, Socket socket,
			BufferedReader ins, int recvIdx) {
		this.node = node;
		this.nodesinfo = node.getNodesInfo();
		myIdx = node.myIdx;
		recvId = node.getIdFromIndex(recvIdx);
		this.ins = ins;
		
		new Thread(this, "ReceiveMessage").start();
	}
	

	/*
	 * This thread's main purpose is to wait until a message is received
	 * over the socket connection, then process it
	 */
	@Override
	public void run() {
		
		String input = "";
		try {
			//while CentralServer or a Node keeps writing to its output stream...
			while ((input = ins.readLine()) != null) {
				
				//get key model <requestingnodeid> <requestnumber> <value> <reqorack> <timestamp>
				if (input.lastIndexOf("get ") == 0) {
					node.getCommandInputThread().respondToGetMessage(input);
				}
				
				//delete key <timestamp>
				//since we don't care about consistency here, cmdComplete has already been flagged
				else if (input.lastIndexOf("delete ") == 0) {
					//Extract key out of msg
					int i = 7;
					while (input.charAt(i) != ' ') //move through key
						i++;
					
					String key = input.substring(7, i);
					
					node.sharedData.remove(key); //remove key from our replica
					System.out.println("Server: Key "+key+" deleted");
				}
				
				//insert key value model <requestingnodeid> <requestnumber> <reqorack> <timestamp>
				else if (input.lastIndexOf("insert ") == 0) {
					node.getCommandInputThread().respondToInsertMessage(input);
				}
				
				//update key value model <requestingnodeid> <requestnumber> <reqorack> <timestamp>
				else if (input.lastIndexOf("update ") == 0) {
					node.getCommandInputThread().respondToUpdateMessage(input);
				}
				
				//search key <requestingnodeid> <requestnumber> <NodeId> <reqorack> <timestamp>
				else if (input.lastIndexOf("search ") == 0) {
					node.getCommandInputThread().respondToSearchMessage(input);
				}
				
				//repair <key> <value> <associatedtimestamp> ...
				else if (input.lastIndexOf("repair") == 0) {
					node.getCommandInputThread().respondToRepairMessage(input);
				}
				
				//copy <key> <value> <requestingnodeid> <requestnumber> <reqorack> <timestamp>
				else if (input.lastIndexOf("copy ") == 0) {
					node.getCommandInputThread().respondToSpecialCopyMessage(input);
				}

				//send message destination, simply print out
				else {
					Date now = new Date();
					SimpleDateFormat format = new SimpleDateFormat("H:mm:ss.SSS");
					
					System.out.print("Client: Received \""+input+"\" from " + recvId);
					System.out.print(", Max delay is " + nodesinfo[myIdx].max_delay + " s, ");
					System.out.println("system time is "+format.format(now));
				}
				
			}
		} catch (IOException e) {
			e.printStackTrace();
			return;
		}
		
	}
	
}
