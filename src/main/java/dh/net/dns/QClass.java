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

  public static QClass valueOf(int val)
  {
    switch(val)
    {
      case 1:
        return QClass.IN;
      case 2:
        return QClass.CS;
      case 3:
        return QClass.CH;
      case 4:
        return QClass.HS;
      default:
        return QClass.ANY;
    }
  }
}
