package mp1;

/*
 * This simple class holds information from the config file about a particular
 * Node or CentralServer
 */
public class NodeInfo {
	
	//Either "leader" or Node id (A,B,C,D)
	public String id = "";
	//ip address of the server
	public String ip = "";
	//port of the server
	public int port = -1;
	//Represents the maximum delay of a message sent to the socket
	public double max_delay = -1;
}
