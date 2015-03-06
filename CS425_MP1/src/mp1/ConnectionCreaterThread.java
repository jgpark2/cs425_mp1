package mp1;

/*
 * ConnectionCreaterThread: 1 per CentralLeader object
 * Functions as a separate thread running to create client connections
 * to become MessageSenderThreads
 */
public class ConnectionCreaterThread extends Thread {
	
	CentralServer centralServer;

	public ConnectionCreaterThread(CentralServer centralServer) {
		this.centralServer = centralServer;
	}

}
