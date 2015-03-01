package mp1;
import java.io.StringWriter;
import java.io.File;
import java.io.FileInputStream;

public class Node {

	private NodeInfo[] nodesinfo;
	private NodeInfo myInfo;
	private NodeThreads threads;
	private ServerThread st;
	//private ClientThread ct;
	private MessageThread mt;
	
	public static void main(String[] args) throws Exception
	{
		if (args.length != 1) {
			System.out.println("Usage: java Node [node_id]");
			System.exit(1);
			return;
		}
		
		Node node = new Node();
		
		if (node.parse(args) == -1) {
			System.out.println("Failed to parse config");
			System.exit(1);
			return;
		}
		
		node.start();
		
		
	}
	
	
	private int parse(String[] args)
	{
		nodesinfo = new NodeInfo[4];
		
		
		for (int i=0; i<4; i++)
			nodesinfo[i] = new NodeInfo();
		
		String id = args[0].toUpperCase();
		
		if (id.compareTo("A") != 0
			&& id.compareTo("B") != 0
			&& id.compareTo("C") != 0
			&& id.compareTo("D") != 0) {
			System.out.println("node ids must be A, B, C, or D");
			return -1;
		}

		//Parsing config file
		//File configfile = new File("/home/galbrth2/cs425/cs425_mp1/CS425_MP1/src/config"); //can't seem to make this a non-absolute path
		File configfile = new File("config");
		FileInputStream fis = null;

		try {
			fis = new FileInputStream(configfile);
			StringWriter str = new StringWriter();
			int content;
			
			for (int i=0; i<4; i++) { //four nodes id, ip
				content = fis.read(); //node id
				str.write(content);
				nodesinfo[i].id = str.toString();
				
				if (str.toString().compareTo(id)==0) {
					System.out.println("Identified this node as "+str.toString());
					myInfo = nodesinfo[i];
				}
				
				
				str = new StringWriter();
				
				content = fis.read(); //,
				
				while ((char)(content = fis.read()) != '\n') //node ip
					str.write(content);

				nodesinfo[i].ip = str.toString();
				str = new StringWriter();
			}
			
			for (int i=0; i<4; i++) { //four nodes port
				content = fis.read(); //node id
				
				content = fis.read(); //,
				
				while ((char)(content = fis.read()) != '\n') //node port
					str.write(content);

				Integer portnumint = new Integer(str.toString());
				str = new StringWriter();
				nodesinfo[i].port = portnumint.intValue();
			}
			
			for (int i=0; i<4; i++) { //four nodes max delay
				content = fis.read(); //node id

				content = fis.read(); //,

				while ((char)(content = fis.read()) != '\n') //node max delay
					str.write(content);
				
				Double mddouble = new Double(str.toString());
				str = new StringWriter();
				nodesinfo[i].max_delay = mddouble.doubleValue();
			}
			
		} catch (Exception e) { //if config file can't be parsed, exit
			e.printStackTrace();
			return -1;
		} finally {
			try {
				if (fis != null) fis.close();
			} catch (Exception ex) {
				ex.printStackTrace();
			}
		}
		
		for (int i=0; i<4; i++) { //test that node info is correct
			System.out.print("Node " + nodesinfo[i].id + ": ");
			System.out.print("IP: " + nodesinfo[i].ip + " ");
			System.out.print("Port: " + nodesinfo[i].port + " ");
			System.out.println("Max delay: " + nodesinfo[i].max_delay);
		}
		return 0;
	}
	
	public void start() {        
        //Start the SEND thread
        new Thread(new ServerThread(nodesinfo, myInfo), "Sender").start();
        
        //Start the RECV threads
        //For now, have only 2 servers connect to eachother, A connects to B and vice versa only
		NodeInfo tempNode;
		if (myInfo.id.compareTo("A")==0)
			tempNode = nodesinfo[1];
		else tempNode = nodesinfo[0];
		
		new Thread(new Client(nodesinfo, myInfo, tempNode), "Receiver").start();
	      
        //new Thread(new MessageThread(), "Delayer._.").start();
        
        
		//threads = new NodeThreads(nodesinfo);
		//mt = new MessageThread(threads);
	}
}
