package dh.net.dns;

import dh.net.dns.QType;
import dh.net.dns.OpCode;
import dh.net.dns.QClass;

import java.util.Vector;

/**
 *  Question represents a DNS question that can be asked to
 *  a DNS Server.
 *
 *  DNS Questions are made up of a header and question
 *  section.
 * 
 *  Header is 12-bytes in total.
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
public class Question
{
  /** Builder class to aid readability when creating
   * a new question
   */
  public static class Builder
  {
    public Builder()
    {
      this.questions = new Vector<QuestionRecord>();
    }

    public Builder setID(int val) { this.id = val; return this; }
    public Builder setOpCode(OpCode val) { this.opCode = val; return this; }
    public Builder setRecursionFlag(boolean val)
    {
      this.recursionDesired = val;
      return this;
    }

    public Builder addQuestion(String name, QType recordType, QClass qClass)
    {
      this.questions.add(new QuestionRecord(name, recordType, qClass));
      ++this.questionCount;
      return this;
    }

    // Header fields
    private int id = 1;
    // Questions are always queries
    private final boolean isQuery = true;
    private OpCode opCode = OpCode.QUERY;
    // Clear bit
    private final boolean authorativeAnswer = false;
    // TODO: Determine if we can set this.
    private boolean truncated = false;
    private boolean recursionDesired = false;
    // Clear bit
    private final boolean recursionAvailable = false;
    // Always zero
    private final byte Z = 0;
    // Set by server.
    private final byte rcode = 0;

    // Determined by calls to addQuestion
    private int questionCount = 0;
    private final int answerCount = 0;
    private final int nameServerCount = 0;
    private final int additionalRecordCount = 0;

    private Vector<QuestionRecord> questions;

    public Question build()
    {
      return new Question(this);
    }
  }

  private Question(Builder builder)
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

    this.questions = builder.questions;
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

  // Determined by calls to addQuestion
  private final int questionCount;
  private final int answerCount;
  private final int nameServerCount;
  private final int additionalRecordCount;

  private Vector<QuestionRecord> questions;
}


class QuestionRecord
{
  QuestionRecord(String name, QType type, QClass qclass)
  {
    this.qname = name;
    this.qtype = type;
    this.qclass = qclass;
  }

  private final String qname;
  private final QType qtype;
  private final QClass qclass;
}
