package dh.net;

import dh.net.dns.*;

import java.io.*;
import java.net.*;
import java.util.*;

public class App
{
    public static void main( String[] args )
    {
        Question dnsQuestion = new Question.Builder().build();

    	if(args.length < 1)
    	{
    		System.out.println("Specify: client|server");
    		System.exit(-1);
    	}

    	System.out.println(args[0]);
    	if(args[0].compareTo("client") == 0)
    	{
    		try
    		{
	    		String hostname = "localhost";
	    		DatagramSocket socket = new DatagramSocket();
	    		InetAddress addr = InetAddress.getByName(hostname);

	    		for(int x = 0; x < 5; ++x)
	    		{
	    			byte buff[] = new byte[256];
	    			DatagramPacket packet = new DatagramPacket(buff, buff.length, addr, 4445);
	    			socket.send(packet);

	    			// Get response
	    			packet = new DatagramPacket(buff, buff.length);
	    			socket.receive(packet);

	    			String message = new String(packet.getData(), 0, packet.getLength());
	    			System.out.println("Message from Server: " + message);
	    		}

	    		socket.close();
    		}
    		catch(IOException e)
    		{
    			e.printStackTrace();
    			System.exit(-1);
    		}

    	}
    	else if(args[0].compareTo("server") == 0)
    	{
    		System.out.println("Starting Datagram Server");
    		DatagramSocket serverSocket = null;
    		try
    		{
    			serverSocket = new DatagramSocket(4445);
    		}
    		catch(IOException e)
    		{
    			e.printStackTrace();
    			System.out.println("Could not start server.");
    			System.exit(-1);
    		}

    		String quoteArray[] = new String[5];
	   		quoteArray[0] = new String("Two birds one stone.");
	   		quoteArray[1] = new String("Reach for the sky!");
	   		quoteArray[2] = new String("Follow your dreams");
	   		quoteArray[3] = new String("The chicken or egg.");
	   		quoteArray[4] = new String("Ooooh Java.");

	   		int currentQuote = 0;
	   		while(currentQuote < 5)
	   		{
	   			try
	   			{
		   			byte[] buff = new byte[256];

		   			// Receive a request
		   			DatagramPacket packet = new DatagramPacket(buff, buff.length);
		   			serverSocket.receive(packet);

		   			// Get next response
		   			buff = quoteArray[currentQuote++].getBytes();

		   			// Send Response.
		   			InetAddress addr = packet.getAddress();
		   			int port = packet.getPort();
		   			packet = new DatagramPacket(buff, buff.length, addr, port);
		   			serverSocket.send(packet);
				}
				catch(IOException e)
				{
					e.printStackTrace();
					currentQuote = 5;
				}
	   		}
			serverSocket.close();
    	}
    	else
    	{
    		System.out.println("Unkown Application Mode");
    		System.exit(-1);
    	}
    }
}
