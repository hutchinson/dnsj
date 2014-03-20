package dh.net.dns;

public enum OpCode
{
  QUERY(0),            // Standard Query
  IQUERY(0x800),       // IQUERY
  STATUS(0x1000);       // Sever Status Request

  OpCode(int val) { this.val = val; }
  public int getValue() { return this.val; }
  private final int val;
}
