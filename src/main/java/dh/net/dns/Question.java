package dh.net.dns;

import dh.net.dns.QType;
import dh.net.dns.OpCode;
import dh.net.dns.QClass;
import dh.net.dns.Header;

import java.util.Vector;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 *  Question represents a DNS question that can be asked to
 *  a DNS Server.
 *
 *  DNS Questions are made up of a header and question
 *  section.
 */
public class Question
{
  /** Builder class to aid readability when creating
   * a new question
   */
  public static class Builder
  {
    public Builder()
    {
      this.headerBuilder = new Header.Builder();
      this.questions = new Vector<QuestionRecord>();
    }

    public Builder setID(int val)
    { 
      this.headerBuilder.setID(val);
      return this;
    }

    public Builder setOpCode(OpCode val)
    { 
      this.headerBuilder.setQueryType(val);
      return this;
    }

    public Builder setRecursionDesired(boolean val)
    {
      this.headerBuilder.setRDFlag(val);
      return this;
    }

    public Builder addQuestion(String name, QType recordType, QClass qClass)
    {
      this.questions.add(new QuestionRecord(name, recordType, qClass));
      this.headerBuilder.setQCount(this.questions.size());
      return this;
    }

    private Header.Builder headerBuilder;
    private Vector<QuestionRecord> questions;

    public Question build()
    {
      return new Question(this);
    }
  }

  /**
   *  Returns a byte array which can be sent directly to a DNS Server
   *  in a UDP packet.
   *
   *  @return valid byte array.
   */
  public byte[] getPacket()
  {
    ByteBuffer buffer = ByteBuffer.allocate(MAX_UDP_PACKET_SIZE);
    buffer.order(ByteOrder.BIG_ENDIAN);

    byte[] header = this.header.headerAsByteArray();

    buffer.put(header);

    // Fill out question section
    for(int x = 0; x < questions.size(); ++x)
    {
      byte[] question = formatQuestion(questions.get(x));
      buffer.put(question);
    }

    return buffer.array();
  }

  private byte[] formatQuestion(QuestionRecord question)
  {
    byte[] formattedQName = formatDomainName(question.qname);
    int totalBytes = 4 + formattedQName.length;
    ByteBuffer qBuffer = ByteBuffer.allocate(totalBytes);

    qBuffer.order(ByteOrder.BIG_ENDIAN);

    // Fill the buffer.
    qBuffer.put(formattedQName);
    qBuffer.putShort((short)question.qtype.getValue());
    qBuffer.putShort((short)question.qclass.getValue());

    return qBuffer.array();
  }

  // TODO: Yuck, rewrite...
  private byte[] formatDomainName(String domainName)
  {
    // www.google.co.uk
    // 3www6google2co2uk0
    byte[] result = new byte[ (domainName.length() + 2) ];
    
    // Starting at the end of str decant each char to the byte
    // array, if '.' replace num previously counted chars.
    int resultPtr = result.length - 2;
    byte currLen = 0;
    while(resultPtr > 0)
    {
      char c = domainName.charAt(resultPtr - 1);
      if(c == '.')
      {
        result[resultPtr] = currLen;
        currLen = 0;
      }
      else if((resultPtr - 1 == 0))
      {
        result[resultPtr] = (byte)c;
        result[resultPtr - 1] = (byte)(currLen + 1);
        break;
      }
      else
      {
        result[resultPtr] = (byte)c;
        ++currLen;
      }
      --resultPtr;
    }

    return result;
  }

  private Question(Builder builder)
  {
    this.header = builder.headerBuilder.build();
    this.questions = builder.questions;
  }

  private Header header;
  private Vector<QuestionRecord> questions;

  // Constants
  // TODO FOR TESTING
  //private final int MAX_UDP_PACKET_SIZE = 65527;
  private final int MAX_UDP_PACKET_SIZE = 512;

}


class QuestionRecord
{
  QuestionRecord(String name, QType type, QClass qclass)
  {
    this.qname = name;
    this.qtype = type;
    this.qclass = qclass;
  }

  public final String qname;
  public final QType qtype;
  public final QClass qclass;
}
