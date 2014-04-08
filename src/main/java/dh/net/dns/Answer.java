package dh.net.dns;

import dh.net.dns.QClass;
import dh.net.dns.QType;


import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
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
      String result = domainName + "\t" +
                      ttl + "\t" +
                      recordClass + "\t" +
                      type + "\t";

      if(type == QType.A)
      {
        String ipAddress = String.valueOf(0xFF & data[0]) + "." +
                           String.valueOf(0xFF & data[1]) + "." +
                           String.valueOf(0xFF & data[2]) + "." +
                           String.valueOf(0xFF & data[3]);
        result += ipAddress;
      }
      else if(type == QType.NS)
      {
        String nameserverAddr = new String(data);
        result += nameserverAddr;
      }
      return result;
    }
  }

  /**
   * Returns true if the following conditions are met:
   *
   * The response does not contain an answer section but contains
   * one or more authoritative name servers in the domain authority.
   *
   * Hopefully the DNS server will also have been a dear and filled out
   * the additional answer section with glue records (corresponding IP
   * addresses) for the name servers, however if it doesn't you may find
   * yourself having to start a new query for those too!
   *
   * @return true if no authoritative answers are present but the
   *         authoritative name server section contains records.
   */
  public boolean isReferralResponse()
  {
    return (this.authorativeAnswers.isEmpty() &&
        !this.authorityNameservers.isEmpty());
  }

  public List<ResourceRecord> getAuthorityAnswers()
  {
    return authorativeAnswers;
  }

  /**
   * Return the list of name servers that we can use in referrals.
   */
  public Map<String, ResourceRecord> referralNameservers()
  {
    Map<String, ResourceRecord> result = new HashMap<>();

    // TODO: re-write using aggregate operations.
    // TODO: tidy up data structures used in parsing to use map
    // instead of these lists...
    for(ResourceRecord rr : authorityNameservers)
    {
      if(rr.type == QType.NS && rr.recordClass == QClass.IN)
      {
        String nameServer = new String(rr.data);
        for(ResourceRecord aRR : additionalRecords)
        {
          if(aRR.domainName.compareTo(nameServer) == 0 &&
             aRR.type == QType.A && aRR.recordClass == QClass.IN)
          {
            result.put(nameServer, aRR);
          }
        }
      }
    }
    return result;
  }

  /**
   * Static factory method to produce an Answer instance from a byte stream,
   * most commonly returned from a DNS Server.
   */
  public static Answer answerFromByteStream(byte[] answerStream)
  {
    Answer result = new Answer();

    if(answerStream.length < 12)
      throw new IllegalArgumentException("Byte stream less than minimum header size.");

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
        b = fullPacketBuffer.get();
      }
      System.out.println();
      fullPacketBuffer.getShort();
      fullPacketBuffer.getShort();

      --numQuestionsToSkip;
    }

    // Parse each answer from the 'answer section'
    int numAnswers = result.header.getAnswerCount();
    while(numAnswers > 0)
    {
      ResourceRecord rr = Answer.nextRecord(fullPacketBuffer);
      System.out.println(rr);
      result.authorativeAnswers.add(rr);
      --numAnswers;
    }

    // Parse each authority nameserver record.
    int numAuthorityAnswers = result.header.getAuthorityRecordCount();
    while(numAuthorityAnswers > 0)
    {
      ResourceRecord rr = Answer.nextRecord(fullPacketBuffer);
      System.out.println(rr);
      result.authorityNameservers.add(rr);
      --numAuthorityAnswers;
    }

    // Parse the addition record section
    int additionalRecords = result.header.getAdditionalRecordCount();
    while(additionalRecords > 0)
    {
      ResourceRecord rr = Answer.nextRecord(fullPacketBuffer);
      result.additionalRecords.add(rr);
      --additionalRecords;
    }
    return result;
  }

  // Utility class to store the results of the expandDNS() function.
  private static class DnsExpansionResult
  {
    // Name expansion should set this to the DNS name.
    public String dnsName;
    // The number of bytes from the initial offset provided
    // that were consumed by the string before pointers were
    // found.
    public int offsetFromInitial;
  }

  /**
   * Expands a domain name in a DNS packet for QNAME, NAME and RDATA fields
   * following the compression scheme outlined in RFC 1035.
   *
   * Extended labels (RFC 2673 currently aren't supported).
   *
   * Note: in the RDATA case it must be applied to RDATA that represents
   * chained fields.
   */
  private static DnsExpansionResult expandDNS(byte[] packet, int initialOffset)
  {
    // A domain name can be defined as
    // 1). A regular sequence of labels.
    // 2). A pointer.
    // 3). A sequence of labels ending in a pointer.
    DnsExpansionResult result = new DnsExpansionResult();
    result.dnsName = "";

    boolean shouldCountOffset = true;

    int offset = initialOffset;

    while(offset < packet.length)
    {
      byte val = packet[offset++];
      if(shouldCountOffset)
        ++result.offsetFromInitial;

      if(val == 0x0)
        break;

      // Pointer
      switch( (val & 0xC0) )
      {
        // Label
        case 0x00:
        {
          byte[] label = new byte[val];
          int index = 0;
          while(index < val)
          {
            label[index++] = packet[offset++];
            if(shouldCountOffset)
              ++result.offsetFromInitial;
          }

          // Append label
          result.dnsName += "." + new String(label);
        }
        break;

        // Pointer
        case 0xC0:
        {
          // Move the pointer to the offset described and continue
          // reading from there.
          int newOffset = val & 0x3F;
          newOffset = newOffset << 8;

          int lowByte = packet[offset++] & 0xFF;
          if(shouldCountOffset)
            ++result.offsetFromInitial;
          newOffset |= lowByte;
          shouldCountOffset = false;

          offset = newOffset;
        }
        break;

        // TODO: Could be extended label (RFC 2673) or other
        // error.
        default:
          throw new IllegalArgumentException("Unsupported DNS label type, aborting.");
      }
    }

    // Trim the leading '.'
    result.dnsName = result.dnsName.substring(1);
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
    int currentOffset = packet.position();
    DnsExpansionResult expansionResult =
      Answer.expandDNS(packet.array(), currentOffset);
    result.domainName = expansionResult.dnsName;
    packet.position(currentOffset + expansionResult.offsetFromInitial);

    // Next is type.
    int type = packet.getShort();
    result.type = QType.valueOf(type);

    int inClass = packet.getShort();
    result.recordClass = QClass.valueOf(inClass);

    long ttl = packet.getInt();
    result.ttl = ttl;

    // Certain types of records contain domain names too.
    if(result.type == QType.NS)
    {
      int rdlength = packet.getShort();

      currentOffset = packet.position();
      expansionResult =
        Answer.expandDNS(packet.array(), currentOffset);

      packet.position(currentOffset + expansionResult.offsetFromInitial);
      result.dataLength = expansionResult.dnsName.length();
      result.data = expansionResult.dnsName.getBytes();
    }
    else
    {
      int rdlength = packet.getShort();
      result.dataLength = rdlength;

      byte[] rdData = new byte[rdlength];
      packet.get(rdData, 0, rdlength);
      result.data = rdData;
    }
    return result;
  }

  private Answer() {}
  private Header header;

  private List<ResourceRecord> authorativeAnswers =
    new ArrayList<>();
  private List<ResourceRecord> authorityNameservers =
    new ArrayList<>();
  private List<ResourceRecord> additionalRecords =
    new ArrayList<>();

}

