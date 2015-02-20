import java.net.ServerSocket;
import java.net.Socket;
import java.io.PrintWriter;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.File;
import java.io.FileInputStream;

public class Node {
	
	private NodeInfo[] nodesinfo;
	private String id = "";
	
	private int parse(String[] args)
	{
		nodesinfo = new NodeInfo[4];
		for (int i=0; i<4; i++)
			nodesinfo[i] = new NodeInfo();
		System.out.print("args: ");
		for (int i=0; i<args.length; i++)
			System.out.print(args[i] + " ");
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
		File configfile = new File("config.txt");
		FileInputStream fis = null;

		try {
			fis = new FileInputStream(configfile);
			
			int content;
			for (int i=0; i<4; i++) { //four nodes id, ip
				content = fis.read(); //node id
				char[] carr = new char[1];
				carr[0] = (char)content;
				System.out.println("content: "+carr[0]+", id: "+(new String(carr)));
				nodesinfo[i].id = new String(carr);
				
				content = fis.read();
				if ((char)content != ',') throw new Exception("config file incorrect");
				
				while ((char)(content = fis.read()) != '\n') { //node ip
					carr[0] = (char)content;
					nodesinfo[i].ip.concat(new String(carr));
				}
			}
			
			for (int i=0; i<4; i++) { //four nodes port
				content = fis.read(); //node id
				char[] carr = new char[1];
				carr[0] = (char)content;
				if (nodesinfo[i].id.compareTo(new String(carr)) != 0) {
					throw new Exception ("config file incorrect");
				}
				
				content = fis.read();
				if ((char)content != ',') throw new Exception("config file incorrect");
				
				String portnumstr = "";
				while ((char)(content = fis.read()) != '\n') { //node port
					carr[0] = (char)content;
					portnumstr.concat(new String(carr));
				}
				Integer portnumint = new Integer(portnumstr);
				nodesinfo[i].port = portnumint.intValue();
			}
			
			for (int i=0; i<4; i++) { //four nodes max delay
				content = fis.read(); //node id
				char[] carr = new char[1];
				carr[0] = (char)content;
				if (nodesinfo[i].id.compareTo(new String(carr)) != 0) {
					throw new Exception ("config file incorrect");
				}
				
				content = fis.read();
				if ((char)content != ',') throw new Exception("config file incorrect");
				
				String mdstr = "";
				while ((char)(content = fis.read()) != '\n') { //node max delay
					carr[0] = (char)content;
					mdstr.concat(new String(carr));
				}
				Double mddouble = new Double(mdstr);
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
	
	public static void main(String[] args) throws Exception
	{
		Node node = new Node();
		if (node.parse(args) == -1)
			return;
	}

}
