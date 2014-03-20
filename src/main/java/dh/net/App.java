package dh.net;

import dh.net.dns.*;

import java.io.*;
import java.net.*;
import java.util.*;

public class App
{
  public static void printPacketAsBits(byte data[], int numBytes)
  {
      int newLine = 0;
      for(int i = 0; i < numBytes; ++i)
      {
        for(int bit = 7; bit >= 0; --bit)
        {
          // Prepare mask
          int mask = 1;
          mask = mask << bit;

          if((mask & data[i]) != 0)
            System.out.print("1");
          else
            System.out.print("0");

          System.out.print(" ");
        }
        if(++newLine > 1)
        {
          newLine = 0;
          System.out.println();
        }
      }

    System.out.println();
  }

  public static void main( String[] args )
  {
    Question.Builder qb = new Question.Builder();
    qb.setID(1);
    qb.setRecursionDesired(true);
    //qb.setOpCode(OpCode.STATUS);
    //qb.addQuestion("www.google.co.uk", QType.A, QClass.IN);

    Question googleQuestion = qb.build();

    byte packetToSend[] = googleQuestion.getPacket();

    if(packetToSend != null)
    {
      App.printPacketAsBits(packetToSend, 12);
    }

    try
    {
      byte[] dnsIPAddress = {(byte)193, (byte)0, (byte)14, (byte)129};
      //byte[] dnsIPAddress = {(byte)255, (byte)1, (byte)168, (byte)192};
      DatagramSocket socket = new DatagramSocket();
      InetAddress addr = InetAddress.getByAddress(dnsIPAddress);

      DatagramPacket datagramPacket = new DatagramPacket(packetToSend, 12, addr, 53);
      socket.send(datagramPacket);

      // Get response
      byte recvPacketData[] = new byte[256];
      datagramPacket = new DatagramPacket(recvPacketData, recvPacketData.length);
      socket.receive(datagramPacket);

      System.out.println("Message from Server");
      App.printPacketAsBits(recvPacketData, 12);

      socket.close();
    }
    catch(IOException e)
    {
      e.printStackTrace();
      System.exit(-1);
    }
  }
}

