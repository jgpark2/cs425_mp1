

public class NodeThreads
{
	//like Chat, this will contain the server thread, the client thread,
	//	and the message-sending thread
	//  it may have public methods to send key, delete key, etc.
	//	that will get called by Server
	//contains info about all the nodes, just like the Node class
	
	NodeInfo[] info;
	
	boolean flag = false;

    public synchronized void Question(String msg) {
        if (flag) {
            try {
                wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        System.out.println(msg);
        flag = true;
        notify();
    }

    public synchronized void Answer(String msg) {
        if (!flag) {
            try {
                wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        System.out.println(msg);
        flag = false;
        notify();
    }
    
    public NodeInfo getNodeInfo(String id) {
    	if (id.compareTo("A") == 0)
    		return info[0];
    	else if (id.compareTo("B") == 0)
    		return info[1];
    	else if (id.compareTo("C") == 0)
    		return info[2];
    	else if (id.compareTo("D") == 0)
    		return info[3];
    	else
    		return new NodeInfo();
    }
    
    NodeThreads(NodeInfo[] ni) {
    	info = ni;
    }
}