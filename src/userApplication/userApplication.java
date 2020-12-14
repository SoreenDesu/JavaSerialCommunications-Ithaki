/** A program designed to communicate with the Ithaki Server of the Aristotle University of Thessaloniki
 * in terms of serial communications. 
 * 
 * It requires the external JAR ithakimodem.jar to be installed. Request codes are granted from the page of Ithaki.
 */

package userApplication;

import java.io.*;
import ithakimodem.*;

public class userApplication {

	static final String echoRequest = "EXXXX";
	static final String imageRequest = "MXXXX";
	static final String damagedImageRequest = "GXXXX";
	static final String gpsRequest = "PXXXX";
	static final String ACK = "QXXXX";
	static final String NACK = "RXXXX";
	
	public static final void main(String[] argv) throws IOException {
		
		//Example of complete session
		userApplication.echoTimeStatistics();	
		userApplication.receiveImage(imageRequest, 81000, "image");
		userApplication.receiveImage(damagedImageRequest, 81000, "damagedimage");
		userApplication.gpsTracking("R=1028090", 6, 9);
		userApplication.arqTimeStatistics();
	}
	
	/**	
	 * Function to read a message sent that ends with the endString given.
	 * Byte array format is prefered for the arq XOR comparison needed at a later stage.
	 * @param modem Indicates the Modem object to read the incoming message.
	 * @param endString Indicates the expected ending sequence of the incoming message, and that's where 'reading' is interrupted. 
	 */
	
	static final byte[] readMessage(final Modem modem, final String endString) {
		String messageReceived = "";
		for(; ;) {
			messageReceived += (char)modem.read();
			if (messageReceived.indexOf(endString) != -1) {
				break;
			}
		}
		return messageReceived.getBytes();
	}
	
	/**	
	 * Function to start up and return a new Modem object for a certain purpose.
	 * Using the same modem for a whole session might prove buggy at times.
	 */
	
    static final Modem establishConnection() {
		Modem modem;
		String message = " ";
		modem = new Modem();
		modem.setSpeed(80000);
		modem.setTimeout(2000);
		modem.open("ithaki");
		readMessage(modem, "tested.\r\n\n\n");
		System.out.print(message);
		return modem;
	}
	
	/**	
	 * Function to receive and record echo type messages, as well as the time need for each one to be sent.
	 */
	
	static final void echoTimeStatistics() {
		
		Modem echoModem = establishConnection();							
		System.out.println("Initializing echo message reception for 6 minutes.");
		try {
			File echoStats = new File("echo.csv");							
			PrintWriter echoWriter = new PrintWriter(echoStats);			
			StringBuilder echoStringBuilder = new StringBuilder();			
			long echoPacketSent;											
			long echoPacketReceived;										
			long echoResponseTime;											
			int echoPacketCounter = 0;										
			byte[] echoResponse;											
			long timeLimit = System.currentTimeMillis() + 360000;			

			while (System.currentTimeMillis() < timeLimit) {				
				echoPacketCounter++;										
				echoPacketSent = System.currentTimeMillis();				
				echoModem.write((echoRequest + "\r").getBytes());			
				echoResponse = readMessage(echoModem, "PSTOP");				
				String echoMessage = new String(echoResponse);
				System.out.println(echoMessage);
				echoPacketReceived = System.currentTimeMillis();			
				echoResponseTime = echoPacketReceived - echoPacketSent;		
				echoStringBuilder.append("Packet #" + echoPacketCounter);	
				echoStringBuilder.append(", ");
				echoStringBuilder.append("Response Time:");
				echoStringBuilder.append(", ");
				echoStringBuilder.append(echoResponseTime);
				echoStringBuilder.append(", ");
				echoStringBuilder.append("ms");
				echoStringBuilder.append(", ");
				echoStringBuilder.append(echoMessage.trim());
				echoStringBuilder.append("\n");
			}
			
			echoWriter.write(echoStringBuilder.toString());					
			echoWriter.close();												
		} catch (FileNotFoundException fnfexception) {
			fnfexception.printStackTrace();
		}
		
		System.out.println("Reception of echo messages is complete.");
		echoModem.close();													
	}
	
	/**	
	 * Function to receive a desired image based on the request code given. 
	 *  @param requestCode Indicates the code that will be used to receive the appropriate image.
	 *  @param imageSizeRestriction Indicates the size of the image file that will be received. Max 81 kB for a normal/damaged image, 150 kB for a gps image.
	 *  @param imageName Indicates the name that will be given to the image file created.
	 */
	
	static final void receiveImage(final String requestCode, final int imageSizeRestriction, final String imageName) {
		System.out.println("Initializing image reception of " + imageName + ".jpg.");
		Modem imageModem = establishConnection();							
		byte[] imageResponse = new byte[imageSizeRestriction];				
		imageModem.write((requestCode + "\r").getBytes());					
		
		for(int i = 0; i < imageSizeRestriction; i++) {						
			imageResponse[i] = (byte)imageModem.read();
			if((imageResponse[i] == 0xD9) && (imageResponse[i-1] == 0xFF)) {
				break;
			}
		}

		File imageFile = new File(imageName + ".jpg");						
		try {																
			FileOutputStream fileOutputStream = new FileOutputStream(imageFile);
			if (imageFile.exists() == false) {								
				imageFile.createNewFile();
			}
			
			fileOutputStream.write(imageResponse);
			fileOutputStream.flush();
			fileOutputStream.close();

		} catch (IOException ioexception) {
			ioexception.printStackTrace();
		}
		System.out.println("Reception of " + imageName + ".jpg is complete.");
		imageModem.close();													
	}

	/**
	 * Function to track GPS points received, complete the proper calculations and finally receive a GPS image of pinpoints.
	 * @param Rparameter Indicates a special type of parameter to be added along the GPS request code.
	 * @param numberOfPins Indicates the number of pins that will be shown in the image.
	 * @param timeGap Indicated the step of seconds between each pin.
	 */
	
	static final void gpsTracking(final String Rparameter, final int numberOfPins, final int timeGap) {
		System.out.println("Initializing reception of "+ numberOfPins + " GPS Pinpoints with a " + timeGap + " second step.");
		Modem gpsModem = establishConnection();								
		gpsModem.write((gpsRequest + Rparameter + "\r").getBytes());		
		byte[] gpsResponse = readMessage(gpsModem, "STOP ITHAKI GPS TRACKING\r\n");
		gpsModem.close();													
		String gpsMessage = new String(gpsResponse);
		gpsMessage = gpsMessage.replace("START ITHAKI GPS TRACKING\r\n", "");	
		gpsMessage = gpsMessage.replace("STOP ITHAKI GPS TRACKING\r\n", "");									
		System.out.println(gpsMessage);
		String[] gpsSamples = gpsMessage.split("\n", -1);							
		int[] gpsPins = new int[numberOfPins];										
		gpsPins[0] = 0;																
		int pinCounter = 1;															
		int initialTime;															
		initialTime = (int) Double.parseDouble((gpsSamples[0].split(",", -2))[1]);	
		initialTime = (initialTime/10000) * 3600 + ((initialTime/100) % 100) * 60 + (initialTime % 100);		
		int followupTime;															
		
		for(int j = 0; j < gpsSamples.length; j++) {								
			followupTime = (int)Double.parseDouble((gpsSamples[j].split(",", -2))[1]); 		
			followupTime = (followupTime/10000) * 3600 + ((followupTime/100) % 100) * 60 + (followupTime % 100);
			if(followupTime > (initialTime + timeGap)) {							
				initialTime = followupTime;									
				gpsPins[pinCounter] = j;										
				pinCounter++;												
			}
			
			if(pinCounter == numberOfPins) {										
				break;
			}
		}
		
		int[][] pinCoordinates = new int[numberOfPins][6];						
		
		for(int i = 0; i < numberOfPins; i++) {
			String[] pin = gpsSamples[gpsPins[i]].split(",", -1);					
			System.out.println("Coordinates of pin #" + (i+1) + ": " + Double.parseDouble(pin[4]));
			/*
			 * Convertion of coordinates from Degrees and decimal minutes to Degrees Minutes Seconds
			 */
			pinCoordinates[i][0] = (int)Double.parseDouble(pin[4]) / 100;			//Latitude - Degrees
			pinCoordinates[i][1] = ( (int) Double.parseDouble(pin[4]) % 100);		//Latitude - Minutes
			pinCoordinates[i][2] = (int)((Double.parseDouble(pin[4]) % 1) * 60);	//Latitude - Seconds
			pinCoordinates[i][3] = (int) Double.parseDouble(pin[2]) / 100;			//Longitude - Degrees
			pinCoordinates[i][4] = ((int) Double.parseDouble(pin[2]) % 100);		//Longitude - Minutes
			pinCoordinates[i][5] = (int) ((Double.parseDouble(pin[2]) % 1) * 60);	//Longitude - Seconds
			
		}
		
		String gpsImageRequestCode = gpsRequest; 									
		
		for(int i = 0; i < numberOfPins; i++) {
			
			gpsImageRequestCode += "T=";
			
			for(int j = 0; j < 6; j++) {
				if((pinCoordinates[i][j] / 10) == 0) {
					gpsImageRequestCode += "0";
				}
				gpsImageRequestCode += pinCoordinates[i][j];
			}
		}
		
		System.out.println("Reception of GPS Pinpoints is complete. An image request code has been formed.");
		receiveImage(gpsImageRequestCode, 150000, "gpsimage");						
	}
	
	/**
	 * Function to examine if the held message of an ARQ packet is valid, compared to the FCS it is paired with. Returns true or false accordingly.
	 * @param ARQ Indicates the ARQ message, in byte array form, to be examined.
	 */
	
	static boolean arqPacketValidity(final byte[] ARQ) {
		
		String arqPacket = new String(ARQ);											
		String arqFCS = arqPacket.substring(arqPacket.length() - 9, arqPacket.length() - 6);		
		String arqHeldMessage = arqPacket.substring(arqPacket.length() -27, arqPacket.length() - 11);	
		char[] arqHeldMessageArray = arqHeldMessage.toCharArray();										
		
		int result = (int) arqHeldMessageArray[0];
		
		for(int i = 1; i < arqHeldMessageArray.length; i++ ) {										
			result = result ^ ((int) arqHeldMessageArray[i]);
		}
		
		if(result == Integer.parseInt(arqFCS)) {
			return true;
		} else {
			return false;
		}	
	}
	
	/**
	 * Function to receive and record ARQ type messages, as well as the time needed for them to be sent properly. Also records the failed attempts.
	 */
	
	static final void arqTimeStatistics() {
		System.out.println("Initializing reception of ARQ messages for 6 minutes.");
		Modem arqModem = establishConnection();										
		try {
			File arqStats = new File("arq.csv");									
			PrintWriter arqWriter = new PrintWriter(arqStats); 						
			StringBuilder arqStringBuilder = new StringBuilder();					
			long arqPacketSent = 0;													
			long arqPacketReceived;													
			long arqResponseTime;													
			int arqPacketCounter = 0;												
			int arqFails = 0;														
			byte[] arqResponse;														
			String requestCode = ACK;												
			long timeLimit = System.currentTimeMillis() + 360000;					
			while (System.currentTimeMillis() < timeLimit) {
				if(requestCode == ACK) {											
					arqPacketCounter++;											
					arqPacketSent = System.currentTimeMillis();					
				}
				arqModem.write((requestCode + "\r").getBytes());
				arqResponse = readMessage(arqModem, "PSTOP");
				String arqMessage = new String(arqResponse);
				System.out.println(arqMessage);
				
				if(arqPacketValidity(arqResponse)) {								
					arqPacketReceived = System.currentTimeMillis();					
					arqResponseTime = arqPacketReceived - arqPacketSent;			
					arqStringBuilder.append("Packet #" + arqPacketCounter);			
					arqStringBuilder.append(", ");
					arqStringBuilder.append("Response Time: ");
					arqStringBuilder.append(", ");
					arqStringBuilder.append(arqResponseTime);
					arqStringBuilder.append(", ");
					arqStringBuilder.append("ms");
					arqStringBuilder.append(", ");
					arqStringBuilder.append("Number of Fails: " + arqFails);
					arqStringBuilder.append(", ");
					arqStringBuilder.append(arqMessage.trim());
					arqStringBuilder.append("\n");
					requestCode = ACK;												
					arqFails = 0;													
					} else {													
					arqStringBuilder.append("Failed attempt for packet #" + arqPacketCounter);
					arqStringBuilder.append(", ");
					arqStringBuilder.append("---");
					arqStringBuilder.append(", ");
					arqStringBuilder.append("---");
					arqStringBuilder.append(", ");
					arqStringBuilder.append(arqMessage.trim());
					arqStringBuilder.append("\n");
					requestCode = NACK;												
					arqFails++;														
					}
			}

			arqWriter.write(arqStringBuilder.toString());							
			arqWriter.close();													
		} catch (FileNotFoundException fnfexception) {
			fnfexception.printStackTrace();
		}
		
		System.out.println("Reception of ARQ messages is complete.");
		arqModem.close();															
	}
}
