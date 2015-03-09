package mp1;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.Random;
import java.util.concurrent.ArrayBlockingQueue;

/*
 * CommandInputThread: 1 per Node object
 * Takes input from System.in or command file and sends that request
 * to the correct communication channel
 */
public class CommandInputThread extends Thread {
	
	private Node node;
	private NodeInfo [] nodesinfo;
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
    

    public CommandInputThread(Node node) {
    	this.node = node;
    	myIdx = node.myIdx;
    	nodesinfo = node.getNodesInfo();
    	
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
				leaderconn = new Socket("127.0.0.1", 7504);//CENTRALSERVER PORT FROM CONFIG
				System.out.print("Now connected to "+"127.0.0.1"+":7504");//CENTRALSERVER PORT FROM CONFIG
				System.out.println(" (leader)");
				node.setToLeaderSendingThread(new MessageSenderThread(node, mqleader, leaderconn));
			} catch (Exception e) {
				leaderconn = null;
			}
		}

		//Get message commands from System.in
		MessageType msg = new MessageType("", new Long(0));
		
        while (msg.msg.compareToIgnoreCase("exit") != 0) {
        	
        	Long now = new Long(0);

        	try {
				msg.msg = sysin.readLine();
				now = System.currentTimeMillis();
				//THIS TIME WILL GET APPENDED TO THE END OF THE MESSAGE IF NOT "SEND" TYPE
			} catch (IOException e) {
				e.printStackTrace();
			}
        	
        	//parse message input
        	int recvIdx = parseCommandForRecvIdx(msg.msg);
        	if (recvIdx < 0) {
        		//System.out.println("Message was not correctly fomatted; try again");
        		try {
					mqleader.put(msg.msg);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
        		continue;
        	}
        	msg.msg = parseCommandForMessage(msg.msg);
        	
        	//Calculate random delay
			int randint = r.nextInt(intmaxdelays[recvIdx]); //random number of milliseconds
			
			//if last[recvIdx] is no longer in the channel, its ts will definitely be smaller
			msg.ts = new Long(Math.max(now + (long)randint, last[recvIdx].ts.longValue()));
			
            try {
                mqnodearr.get(recvIdx).put(msg);
                last[recvIdx] = msg;
                System.out.print("Sent \""+msg.msg+"\" to "+nodesinfo[recvIdx].id+", system time is ");
				SimpleDateFormat format = new SimpleDateFormat("HH:mm:ss.SSS");
				Date curdate = new Date();
				System.out.println(format.format(curdate));
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
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
	
	
	//This method is assumed to never be called before parseCommandForRecvIdx
	private String parseCommandForMessage(String cmd) { //"Send Hello B"
		int len = cmd.length();
		
		StringWriter msg = new StringWriter();
		for (int i=5; i<(len-2); i++)
			msg.append(cmd.charAt(i));

		return msg.toString();
	}
	
	
	//All the input checking will occur here
	private int parseCommandForRecvIdx(String cmd) { //"Send Hello B"
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
		
		str = new StringWriter();
		for (int i=0; i<5; i++)
			str.append(cmd.charAt(i));
		if (str.toString().compareToIgnoreCase("send ") != 0) {
			//System.out.println("cmd did not have send");
			return -1;
		}
		
		return node.getIndexFromId(id);
	}
	

	/*
	 * When a MessageReceiverThread receives a delete/get/insert/update message,
	 * this method either sends a reply, updates a key/value, or does nothing
	 */
	public void respondToMessage(String input) {
		// TODO Do things with the key/value Dictionary
		
		System.out.println("Responded to "+input);
		
	}
	
}
