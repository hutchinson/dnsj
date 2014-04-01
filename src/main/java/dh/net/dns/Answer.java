package dh.net.dns;

import dh.net.dns.QClass;
import dh.net.dns.QType;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class Answer
{
  public static class ResourceRecord
  {
    String domainName;
    QType type;
    QClass recordClass;
    long ttl;
  
    // TODO: figure out how to parse represent the data in a nice
    // OO way..
    int dataLength;
    byte[] data;

    public String toString()
    {
      String result = "Domain: " + domainName + " | " +
                      "RR Type: " + type + " | " +
                      "Class : " + recordClass + " | " +
                      "TTL: " + ttl + " | " +
                      "RDLENGTH: " + dataLength;

      if(type == QType.A)
      {
        String ipAddress = " | " + String.valueOf(0xFF & data[0]) + "." +
                           String.valueOf(0xFF & data[1]) + "." +
                           String.valueOf(0xFF & data[2]) + "." +
                           String.valueOf(0xFF & data[3]);
        result += ipAddress;
      }
      return result;
    }
  }

  /**
   * Static factory method to produce an Answer instance from a byte stream,
   * most commonly returned from a DNS Server.
   */
  public static Answer answerFromByteStream(byte[] answerStream)
  {
    Answer result = new Answer();

    if(answerStream.length < 12)
    {
      System.out.println("Byte stream less than minimum header size.");
      return null;
    }

    // Parse the header.
    Header.Builder headerBuilder = new Header.Builder();
    ByteBuffer fullPacketBuffer = ByteBuffer.wrap(answerStream);
    fullPacketBuffer.order(ByteOrder.BIG_ENDIAN);

    // Grab the ID
    headerBuilder.setID(fullPacketBuffer.getShort());

    // Grab the options
    int options = fullPacketBuffer.getShort();

    // Determine each setting.
    int rcode = options & 0x000F;
    headerBuilder.setRCode((byte)rcode);

    int recursionAvailable = options & 0x80;
    headerBuilder.setRAFlag(recursionAvailable != 0);
    
    int recursionDesired = options & 0x100;
    headerBuilder.setRDFlag(recursionDesired != 0);

    int truncated = options & 0x200;
    headerBuilder.setTruncatedFlag(truncated != 0);

    int authorativeAnswer = options & 0x400;
    headerBuilder.setAAFlag(authorativeAnswer != 0);

    // TODO: This needs a re-think
    int opCode = options & 0x7800;
    switch(opCode)
    {
      case 0x0:
        headerBuilder.setQueryType(OpCode.QUERY);
      break;

      case 0x800:
        headerBuilder.setQueryType(OpCode.IQUERY);
      break;

      case 0x1000:
        headerBuilder.setQueryType(OpCode.STATUS);
      break;
    }
    
    int isQuery = options & 0x8000;
    headerBuilder.setQueryFlag(isQuery == 0);

    // Next the counts.
    int qCount = fullPacketBuffer.getShort();
    headerBuilder.setQCount(qCount);
    int anCount = fullPacketBuffer.getShort();
    headerBuilder.setACount(anCount);
    int nsCount = fullPacketBuffer.getShort();
    headerBuilder.setNSCount(nsCount);
    int arCount = fullPacketBuffer.getShort();
    headerBuilder.setARCount(arCount);

    result.header = headerBuilder.build();
    System.out.println(result.header);

    // Now we know what the packet contains, parse its contents.
    int numQuestionsToSkip = result.header.getQuestionCount();
    // Eat the questions
    while(numQuestionsToSkip > 0)
    {
      byte b = fullPacketBuffer.get();
      // TODO: This will only work if the question wasn't compressed
      // which we assume for now, it wasn't.
      // Read the domain name until a '\0'
      while(b != 0x0)
      {
        System.out.print((char)b);
        b = fullPacketBuffer.get();
      }
      System.out.println();
      fullPacketBuffer.getShort();
      fullPacketBuffer.getShort();

      --numQuestionsToSkip;
    }

    // Parse each answer from the 'answer section'
    int numAnswers = result.header.getAnswerCount();
    System.out.println("Answer Record: " + numAnswers);
    while(numAnswers > 0)
    {
      ResourceRecord rr = Answer.nextRecord(fullPacketBuffer);
      System.out.println(rr);
      --numAnswers;
    }

    // Parse each authority nameserver record.
    int numAuthorityAnswers = result.header.getAuthorityRecordCount();
    System.out.println("Authoritative Nameserver Record: " + numAuthorityAnswers);
    while(numAuthorityAnswers > 0)
    {
      ResourceRecord rr = Answer.nextRecord(fullPacketBuffer);
      System.out.println(rr);

      --numAuthorityAnswers;
    }

    // Parse the addition record section
    int additionalRecords = result.header.getAdditionalRecordCount();
    System.out.println("Additional Records: " + additionalRecords);
    while(additionalRecords > 0)
    {
      // Answer, Authority and additional record sections all share the same
      // format.
      //
      ResourceRecord rr = Answer.nextRecord(fullPacketBuffer);
      System.out.println(rr);

      --additionalRecords;
    }
    return result;
  }

  /**
   * Return a string that contains the domain name following
   * DNS compression rules from the offset to the NULL octet.
   *
   * NOTE: THIS IS A ROUGH IMPLEMENTATION WITH A LOT OF DUPLICATED
   * LOGIC, TO BE CLEANED UP!
   */
  private static String domainViaPointer(byte[] packet, int offset)
  {
    String result = "";
    byte currStrLen;

    while( (currStrLen = packet[offset++]) != 0x0)
    {
      // Are we a pointer to another string or a string ourself?
      if((currStrLen & 0xC0) != 0)
      {
        int newOffset = currStrLen & 0x3F;
        newOffset = newOffset << 8;
        int lowByte = packet[offset++] & 0xFF;
        newOffset |= lowByte;

        // TODO: By exiting out of the loop we assume that the call to
        // domainViaPointer at this juncture caused us to find the
        // end of the string this may not be the case!!!
        result += domainViaPointer(packet, newOffset);
        break;
      }
      else
      {
        byte[] currStr = new byte[currStrLen];
        int index = 0;
        while(index < currStrLen)
        {
          currStr[index] = packet[offset++];
          ++index;
        }

        // Append to result
        result += "." + new String(currStr);
      }
    }

    return result;
  }

  /**
   * We're using direct byte buffer access to make things
   * easier in this function
   */
  public static ResourceRecord nextRecord(ByteBuffer packet)
  {
    ResourceRecord result = new ResourceRecord();

    // Each RR begins with the domain to which the RR pertains.
    String domain = "";
    // A domain name can be defined as
    // 1). A regular sequence of labels.
    // 2). A pointer.
    // 3). A sequence of labels ending in a pointer.
    //
    // Is this case (2)
    int currStrLen = packet.get();
    if((currStrLen & 0xC0) !=0)
    {
      int offset = currStrLen & 0x3F;
      offset = offset << 8;
      // TODO: Find better way to interpret 'un-signed'
      // data.
      int lowByte = packet.get() & 0xFF;
      offset |= lowByte;
      domain = Answer.domainViaPointer(packet.array(), offset);
    }
    else
    {
      while(currStrLen != 0x0)
      {
        if((currStrLen & 0xC0) != 0x0)
        {
          // This is case (3).
          int offset = currStrLen & 0x3F;
          offset = offset << 8;
          int lowByte = packet.get() & 0xFF;
          offset |= lowByte;

          domain += "." + Answer.domainViaPointer(packet.array(), offset);
          break;
        }
        else
        {
          byte[] curStr = new byte[currStrLen];
          packet.put(curStr, 0, currStrLen);
          domain += "." + new String(curStr);
          currStrLen = packet.get();
        }
      }
    }

    // Trim the leading '.' added as an artefact of the algorithm.
    result.domainName = domain.substring(1);

    // Next is type.
    int type = packet.getShort();
    result.type = QType.valueOf(type);

    int inClass = packet.getShort();
    result.recordClass = QClass.valueOf(inClass);

    long ttl = packet.getInt();
    result.ttl = ttl;

    int rdlength = packet.getShort();
    result.dataLength = rdlength;

    byte[] rdData = new byte[rdlength];
    packet.get(rdData, 0, rdlength);
    result.data = rdData;

    return result;
  }

  private Answer() {}
  private Header header;

}

