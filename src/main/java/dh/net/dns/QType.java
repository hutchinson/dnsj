package dh.net.dns;

public enum QType
{
  A(1),
  NS(2),
  MD(3),
  MF(4),
  CNAME(5),
  SOA(6),
  MB(7),
  MG(8),
  MR(9),
  NULL(10),
  WKS(11),
  PTR(12),
  HINFO(13),
  MINFO(14),
  MX(15),
  TXT(16),
  AAAA(28);
  
  QType(int val) { this.val = val; }
  public int getValue() { return this.val; }
  private final int val;

  public static QType valueOf(int val)
  {
    switch(val)
    {
      case 1:
        return QType.A;
      case 2:
        return QType.NS;
      case 3:
        return QType.MD;
      case 4:
        return QType.MF;
      case 5:
        return QType.CNAME;
      case 6:
        return QType.SOA;
      case 7:
        return QType.MB;
      case 8:
        return QType.MG;
      case 9:
        return QType.MR;
      case 10:
        return QType.NULL;
      case 11:
        return QType.WKS;
      case 12:
        return QType.PTR;
      case 13:
        return QType.HINFO;
      case 14:
        return QType.MINFO;
      case 15:
        return QType.MX;
      case 16:
        return QType.TXT;
      case 28:
        return QType.AAAA;
      default:
        return QType.NULL;
    }
  }
}
