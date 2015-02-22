

public class ServerThread implements Runnable
{
	//run the server thread
	//have public methods such as setting up on a certain port/ip
	//it will call methods in ServerThreads to convey messages
	
	NodeThreads m;
	
    public ServerThread(NodeThreads m1) {
        this.m = m1;
        new Thread(this, "Sender").start();
    }

    public void run() {
    	
	
        m.Question("Whee");
    }
    
	
}