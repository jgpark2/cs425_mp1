public class ServerThread implements Runnable
{

	protected int          serverPort   = 7500;
    protected ServerSocket serverSocket = null;
    protected boolean      isStopped    = false;
    protected Thread       runningThread= null;
    
    
	//run the server thread
	//have public methods such as setting up on a certain port/ip
	//it will call methods in ServerThreads to convey messages
	
	NodeThreads m;
	
    public ServerThread(NodeThreads m1, int port) { 
        this.m = m1;
        this.serverPort = port;
        new Thread(this, "Sender").start();
    }

    public void run() {
    	synchronized(this){
            this.runningThread = Thread.currentThread();
        }
        
        
        try {
            this.serverSocket = new ServerSocket(this.serverPort);
        } catch (IOException e) {
            throw new RuntimeException("Cannot open port XXXX", e);
        }
        
        
        while(! isStopped()){
            Socket clientSocket = null;
            
            try {
                clientSocket = this.serverSocket.accept();
            } catch (IOException e) {
                if(isStopped()) {
                    System.out.println("Server Stopped.") ;
                    return;
                }
                throw new RuntimeException("Error accepting client connection", e);
            }
            new Thread(  new WorkerRunnable(clientSocket, "Multithreaded Server")  ).start();
        }
        System.out.println("Server Stopped.") ;
        //m.Question("Whee");
    }
    
    
    /*http://tutorials.jenkov.com/java-multithreaded-servers/multithreaded-server.html*/
    private synchronized boolean isStopped() {
        return this.isStopped;
    }
    
	
}
