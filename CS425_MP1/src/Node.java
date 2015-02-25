import java.io.StringWriter;
import java.io.File;
import java.io.FileInputStream;

public class Node {
	/*
	private NodeInfo[] nodesinfo;
	private NodeThreads threads;
	private ServerThread st;
	//private ClientThread ct;
	private MessageThread mt;
	private String id = "";
	
	private int parse(String[] args)
	{
		nodesinfo = new NodeInfo[4];
		for (int i=0; i<4; i++)
			nodesinfo[i] = new NodeInfo();

		if (args.length != 1) {
			System.out.println("usage: java Node [node_id]");
			return -1;
		}
		
		id = args[0].toUpperCase();
		
		if (id.compareTo("A") != 0
			&& id.compareTo("B") != 0
			&& id.compareTo("C") != 0
			&& id.compareTo("D") != 0) {
			System.out.println("node ids must be A, B, C, D");
			return -1;
		}

		//Parsing config file
		File configfile = new File("/home/galbrth2/cs425/cs425_mp1/CS425_MP1/src/config"); //can't seem to make this a non-absolute path
		FileInputStream fis = null;

		try {
			fis = new FileInputStream(configfile);
			StringWriter str = new StringWriter();
			int content;
			
			for (int i=0; i<4; i++) { //four nodes id, ip
				content = fis.read(); //node id
				str.write(content);
				nodesinfo[i].id = str.toString();
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
		threads = new NodeThreads(nodesinfo);
		st = new ServerThread(threads);
		//ct = new ClientThread(threads);
		mt = new MessageThread(threads);
	}
	
	*/
	
	public static void main(String[] args) throws Exception
	{/*
		Node node = new Node();
		if (node.parse(args) == -1)
			return;
		
		node.start();
		
		//
		NodeThreads m = new NodeThreads();
		*/
		//Start the RECV thread
        //new Thread(new Client(), "Receiver").start();
        
        //Start the SEND thread
        new Thread(new ServerThread(), "Sender").start();
        
        //new Thread(new MessageThread(), "Delayer._.").start();
	}

}
