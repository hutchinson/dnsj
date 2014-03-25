package dh.net.dns;

public class Answer
{
  /**
   * Static factory method to produce an Answer instance from a byte stream,
   * most commonly returned from a DNS Server.
   */
  public static Answer answerFromByteStream(byte[] answerStream)
  {
    Answer result = new Answer();


    return result;
  }

  private Answer() {}

}

