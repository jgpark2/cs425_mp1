package mp1;

import java.util.ArrayList;

/*
 * This simple class is used to store received acks
 */
public class AckTracker {
	public int toreceive;
	public ArrayList<String> nullacks = new ArrayList<String>();
	public ArrayList<String> validacks = new ArrayList<String>();
	
	public AckTracker() {
		toreceive = 0;
	}
	
	public AckTracker(int torecv) {
		toreceive = torecv;
	}
}
