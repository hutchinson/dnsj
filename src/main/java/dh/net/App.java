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

  public static void printQuestion(byte[] packet)
  {
    int offset = 12;
    System.out.print("Domain Name: ");
    while(offset < packet.length)
    {
      if(packet[offset] > 45)
        System.out.print((char)packet[offset++]);
      else
        System.out.print(packet[offset++]);
    }
    System.out.println();
  }

  public static void main( String[] args )
  {
    DnsResolver resolver = new DnsResolver();
    Answer.ResourceRecord rr = resolver.query("www.google.com", QType.A, QClass.IN);
    System.out.println("Query Result: " + rr);
  }
}

