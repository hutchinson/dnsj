package dh.net.dns;

public enum QClass
{
  IN(1),
  CS(2),
  CH(3),
  HS(4),
  ANY(255);
  
  QClass(int val) { this.val = val; }
  public int getValue() { return this.val; }
  private final int val;
}
