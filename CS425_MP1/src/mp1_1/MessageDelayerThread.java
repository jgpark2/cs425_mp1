package mp1_1;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.concurrent.BlockingQueue;

/*
 * MessageDelayerThread: 3 per Node object
 * Takes input from the blocking queue, sleeps for the desired amount,
 * and sends that request over the socket associated with this
 */
public class MessageDelayerThread extends Thread {

	private Node node;
	private NodeInfo [] nodesinfo;
	private int myIdx; //index into NodeInfo array
	private int recvIdx; //index into NodeInfo array
	
	private BlockingQueue<MessageType> mq;
	private int mqmax;
	
	private Socket receiver;
	private PrintWriter outs;
	

    public MessageDelayerThread(Node node, BlockingQueue<MessageType> mq,
    			int maxsize, int recvIdx, Socket connection) {
    	this.node = node;
    	nodesinfo = node.getNodesInfo();
    	myIdx = node.myIdx;
    	this.recvIdx = recvIdx;
		this.mq = mq;
		mqmax = maxsize;
		receiver = connection;
		
		new Thread(this, "DelayInput"+nodesinfo[recvIdx].id).start();
	}

	public void run() {
		
		try {
			outs = new PrintWriter(receiver.getOutputStream(), true);
		} catch (IOException e) {
			System.out.println("Unable to open socket stream");
			e.printStackTrace();
			try {
				receiver.close();
				outs.close();
			} catch (IOException e1) {
				e1.printStackTrace();
			}
			return;
		}
		
		//send myIdx to the MessageReceiverThread on the other end
		outs.println(myIdx);
    	
		try{
            MessageType msg;
            //consuming messages until exit message is received
            while((msg = mq.take()).msg.compareToIgnoreCase("exit") != 0) {
            	long tosleep = msg.ts - System.currentTimeMillis();
            	if (tosleep > 0)
            		Thread.sleep(tosleep);
            	//System.out.println("Consumed "+msg.msg);
            	//while MessageReceiverThread keeps reading from its input stream...
            	outs.println(msg.msg);
            }
            outs.println(msg.msg);
        }catch(InterruptedException e) {
            e.printStackTrace();
        }
		
		System.out.println("MessageDelayerThread to node "+nodesinfo[recvIdx].id+" received exit, exiting");
		try {
			receiver.close();
			outs.close();
		} catch (IOException e1) {
			e1.printStackTrace();
		}
		
    }
 
}
