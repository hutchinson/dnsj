package dh.net.dns;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import dh.net.dns.OpCode;
import dh.net.dns.QType;
import dh.net.dns.QClass;

/**
 * Encapsulates the logic required for created and parsing
 * DNS header packets.
 *
 * Header is 12-bytes in total.
 * 
 *                                   1  1  1  1  1  1
 *     0  1  2  3  4  5  6  7  8  9  0  1  2  3  4  5
 *   +--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+
 *   |                      ID                       |
 *   +--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+
 *   |QR|   Opcode  |AA|TC|RD|RA|   Z    |   RCODE   |
 *   +--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+
 *   |                    QDCOUNT                    |
 *   +--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+
 *   |                    ANCOUNT                    |
 *   +--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+
 *   |                    NSCOUNT                    |
 *   +--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+
 *   |                    ARCOUNT                    |
 *   +--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+
 */
public class Header
{
  /**
   * Header Builder allows creation of DNS Header packets
   */
  public static class Builder
  {
    public Builder() {}

    public Header build()
    {
      return new Header(this);
    }

    public Builder setID(int val) { this.id = val; return this; }

    public Builder setQueryFlag(boolean val)
    {
      this.isQuery = val;
      return this;
    }

    public Builder setQueryType(OpCode val)
    {
      this.opCode = val;
      return this;
    }

    public Builder setAAFlag(boolean val)
    {
      this.authorativeAnswer = val;
      return this;
    }

    public Builder setTruncatedFlag(boolean val)
    { 
      this.truncated = val;
      return this;
    }

    public Builder setRDFlag(boolean val)
    {
      this.recursionDesired = val;
      return this;
    }

    public Builder setRCode(byte val)
    { 
      this.rcode = val;
      return this;
    }

    public Builder setQCount(int val)
    {
      this.questionCount = val;
      return this;
    }

    public Builder setACount(int val)
    { 
      this.answerCount = val;
      return this;
    }

    public Builder setNSCount(int val)
    { 
      this.nameServerCount = val;
      return this;
    }

    public Builder setARCount(int val)
    {
      this.additionalRecordCount = val;
      return this;
    }

    private int id = 1;
    private boolean isQuery = true;
    private OpCode opCode = OpCode.QUERY;
    private boolean authorativeAnswer = false;
    private boolean truncated = false;
    private boolean recursionDesired = false;
    private boolean recursionAvailable = false;
    // Z field is reserved and must be zero so don't provide user with option.
    private final byte Z = 0;
    private byte rcode = 0;

    private int questionCount = 0;
    private int answerCount = 0;
    private int nameServerCount = 0;
    private int additionalRecordCount = 0;
  }

  /**
   * Returns this header encoded as a byte array capable of being sent to a
   * DNS Server.
   *
   * @return 12 byte array to be sent to DNS server.
   */
  public byte[] headerAsByteArray()
  {
    // DNS Header is always 12 bytes (See comments above for formatting).
    ByteBuffer buffer = ByteBuffer.allocate(12);
    buffer.order(ByteOrder.BIG_ENDIAN);

    // First store the ID number we've been given.
    buffer.putShort((short)this.id);

    // Next store the various flags as per the user's settings
    //  MSB ---> LSB
    //    0  1  2  3  4  5  6  7  8  9  0  1  2  3  4  5
    //  +--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+
    //  |                      ID                       |
    //  +--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+
    //  |QR|   Opcode  |AA|TC|RD|RA|   Z    |   RCODE   |
    //
    int options = 0x000000;

    // QR
    if(!this.isQuery)
    {
      int mask = 0x8000;
      options |= mask;
    }

    // OpCode
    // TODO: OpCode only valid for QUERY
   // int opCode = this.opCode.getValue();
   // options |= opCode;

    // Authoritative Answer
    if(this.authorativeAnswer)
    {
      int mask = 0x400;
      options |= mask;
    }

    // Recursion Desired
    if(this.recursionDesired)
    {
      int mask = 0x100;
      options |= mask;
    }

    // Take the first word and push into array.
    buffer.putShort((short)options);

    // Set number of questions, remainder of header is zeros.
    buffer.putShort((short)questionCount);
    // Answer Count == 0
    buffer.putShort((short)answerCount);
    // Name Server count == 0
    buffer.putShort((short)nameServerCount);
    // Additional Record Count == 0
    buffer.putShort((short)additionalRecordCount);
  
    return buffer.array();
  }

  private Header(Builder builder)
  {
    this.id = builder.id;
    this.isQuery = builder.isQuery;
    this.opCode = builder.opCode;
    this.authorativeAnswer = builder.authorativeAnswer;
    this.truncated = builder.truncated;
    this.recursionDesired = builder.recursionDesired;
    this.recursionAvailable = builder.recursionAvailable;
    this.Z = builder.Z;
    this.rcode = builder.rcode;

    this.questionCount = builder.questionCount;
    this.answerCount = builder.answerCount;
    this.nameServerCount = builder.nameServerCount;
    this.additionalRecordCount = builder.additionalRecordCount;
  }

  // Header fields
  private final int id;
  private final boolean isQuery;
  private final OpCode opCode;
  private final boolean authorativeAnswer;
  private final boolean truncated;
  private final boolean recursionDesired;
  private final boolean recursionAvailable;
  private final byte Z;
  private final byte rcode;

  private final int questionCount;
  private final int answerCount;
  private final int nameServerCount;
  private final int additionalRecordCount;
}

