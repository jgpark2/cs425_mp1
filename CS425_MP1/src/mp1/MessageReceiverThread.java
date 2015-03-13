package mp1;

import java.io.BufferedReader;
import java.io.IOException;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.Date;

/*
 * MessageReceiverThread: 3 per Node object
 * (or 1 per Node object with CentralServer)
 * Receives messages from a socket connection and processes them
 */
public class MessageReceiverThread extends Thread {
	
	private Node node;
	private NodeInfo [] nodesinfo;
	private int myIdx; //index into NodeInfo array
	
	private int recvIdx; //index into NodeInfo array
	private String recvId;
	
	private Socket client;
	private BufferedReader ins;
	

	public MessageReceiverThread(Node node, Socket socket,
			BufferedReader ins, int recvIdx) {
		this.node = node;
		this.nodesinfo = node.getNodesInfo();
		myIdx = node.myIdx;
		this.recvIdx = recvIdx;
		recvId = node.getIdFromIndex(recvIdx);
		client = socket;
		this.ins = ins;
		
		new Thread(this, "ReceiveMessage").start();
	}
	

	@Override
	public void run() {
		
		//With CentralServer
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
					System.out.println("Key "+key+" deleted");
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
					SimpleDateFormat format = new SimpleDateFormat("HH:mm:ss.SSS");
					
					System.out.print("Received \""+input+"\" from " + recvId);
					System.out.print(", Max delay is " + nodesinfo[myIdx].max_delay + " s, ");
					System.out.println("system time is "+format.format(now));
				}
				
			}
		} catch (IOException e) {
			e.printStackTrace();
			return;
		}
		
		
		//This code should get run if we intend all nodes to exit at the same time
/*
		CommandInputThread cmdint = node.getCommandInputThread();
		MessageType msg = new MessageType("exit", new Long(0));
		cmdint.addMessageToAllQueues(msg);
		
		System.out.println("MessageReceiverThread from node "+recvId+" received exit, exiting");
		try {
			client.close();
			ins.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
*/
		
	}
	
}
