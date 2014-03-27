package dh.net.dns;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class Answer
{
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
    return result;
  }

  private Answer() {}
  private Header header;

}

