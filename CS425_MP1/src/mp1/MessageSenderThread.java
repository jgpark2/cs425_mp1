package mp1;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.concurrent.ArrayBlockingQueue;

/*
 * MessageSenderThread: 1 per Node object
 * Takes input from the blocking queue,
 * and sends that request to the CentralServer
 */
public class MessageSenderThread extends Thread {

	private Node node;
	private NodeInfo [] nodesinfo;
	private int myIdx;
	
	private Socket socket;
	private ArrayBlockingQueue<String> mqout;
	
	private PrintWriter outs;
	
	
	public MessageSenderThread(Node node, ArrayBlockingQueue<String> mq,
			Socket socket) {
		this.node = node;
		nodesinfo = node.getNodesInfo();
		myIdx = node.myIdx;
		this.socket = socket;
		mqout = mq;
		
		new Thread(this, "SenderToLeader").start();
	}


	public void run() {
		
		try {
			outs = new PrintWriter(socket.getOutputStream(), true);
		} catch (IOException e) {
			System.out.println("Unable to open socket stream");
			e.printStackTrace();
			try {
				socket.close();
				outs.close();
			} catch (IOException e1) {
				e1.printStackTrace();
			}
			return;
		}
		
		//send myIdx to the MessageRelayThread on the other end (at the CentralServer)
		outs.println(myIdx);
		
		try {
            String msg;
            //consuming messages until exit message is received
            while((msg = mqout.take()).compareToIgnoreCase("exit") != 0) {
            	//while MessageReceiverThread keeps reading from its input stream...
            	outs.println(msg);
            }
            outs.println(msg);
        } catch(InterruptedException e) {
            e.printStackTrace();
        }
		
	}

}
