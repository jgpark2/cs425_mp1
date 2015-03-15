package mp1;

/*
 * This simple class holds information about a message before it is compressed
 * into just a string to be sent along a communication channel
 */
public class MessageType {
	
	//The message string that will be sent along a communication channel
	public String msg;
	
	//The time this message was received; becomes the time to send the
	//message at when calculating a random channel delay
	public Long ts;
	
	public MessageType() {
		msg = "";
		ts = new Long(0);
	}
	
	public MessageType(String msg, Long time) {
		this.msg = new String(msg);
		this.ts = time;
	}
	
	public MessageType(MessageType msg) {
		this.msg = new String(msg.msg);
		this.ts = msg.ts;
	}

}
