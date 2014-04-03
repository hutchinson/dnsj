package dh.net.dns;

import dh.net.dns.*;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * DnsSystem represents provides the ability to invoke queries into the
 * global DNS system and return various asynchronous events.
 *
 * The rough design at present is:
 *
 * // call a competition callback when the question is fully resolved.
 * public void query(Question, CompletionCallback)
 *
 * // Synchronous response
 * public Answer query(Question)
 *
 * Example registration methods that can eventually be used in the 
 * UI front end for this system.
 * public void registerOnNewZoneCallback(callback)
 * public void registerOnNewNSAddedToZone(callback)
 * public void registerOnNewARecordAddedToZoneCallback(callback)
 *
 * The query system works via a job queue, the initial query will result
 * in an QueryRootServerJob being pushed to the job queue.
 *
 * When we receive responses from servers we add an InterpretResponseJob
 * to the queue which may result in further queries being made.
 */
public class DnsSystem
{
  public DnsSystem()
  {
    jobQueue = new LinkedBlockingQueue<DnsJob>();
  }

  public void query(Question dnsQuery)
  {
    try
    {
      byte[] dnsIPAddress = {(byte)193, (byte)0, (byte)14, (byte)129};
      DatagramSocket socket = new DatagramSocket();
      InetAddress addr = InetAddress.getByAddress(dnsIPAddress);

      byte packetToSend[] = dnsQuery.getPacket();
      DatagramPacket datagramPacket =
        new DatagramPacket(packetToSend, packetToSend.length, addr, 53);
      socket.send(datagramPacket);

      // Get response
      byte recvPacketData[] = new byte[65536];
      datagramPacket = new DatagramPacket(recvPacketData, recvPacketData.length);
      socket.receive(datagramPacket);

      System.out.println("Binary Header from Server");

      Answer answer = Answer.answerFromByteStream(recvPacketData);

      socket.close();
    }
    catch(IOException e)
    {
      e.printStackTrace();
      System.exit(-1);
    }
  }

  private BlockingQueue<DnsJob> jobQueue;
}
