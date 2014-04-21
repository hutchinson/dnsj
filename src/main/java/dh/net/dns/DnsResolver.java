package dh.net.dns;

import java.io.*;
import java.net.*;
import java.util.*;
import java.nio.*;
import java.nio.channels.*;
import java.util.concurrent.*;

/**
 * DnsResolver represents provides the ability to invoke queries into the
 * global DNS system and return various asynchronous events.
 *
 * The rough design at present is:
 *
 * // call a competition callback when the question is fully resolved.
 * public void query(Question, CompletionCallback)
 *
 * // Synchronous response
 * public Answer query(Question)
 *
 * Example registration methods that can eventually be used in the 
 * UI front end for this system.
 * public void registerOnNewZoneCallback(callback)
 * public void registerOnNewNSAddedToZone(callback)
 * public void registerOnNewARecordAddedToZoneCallback(callback)
 *
 */
public class DnsResolver
{

  public DnsResolver()
  {
    this.nextQueryID = 0;
    this.inProgressQueries = new LinkedList<>();
    this.rrCache = new ConcurrentHashMap<>();
  }

  /**
   * Used to represent a name server.
   */
  private class Nameserver
  {
    public Nameserver(String hostName, int b1, int b2, int b3, int b4)
    {
      this.hostName = hostName;

      ipv4 = new byte[4];
      ipv4[0] = (byte)b1;
      ipv4[1] = (byte)b2;
      ipv4[2] = (byte)b3;
      ipv4[3] = (byte)b4;
    }

    public byte[] address()
    { 
      return Arrays.copyOf(ipv4, ipv4.length);
    }
    private final byte[] ipv4;
    private final String hostName;

    @Override
    public boolean equals(Object o)
    {
      if(o == this)
        return true;

      if(!(o instanceof Nameserver))
        return false;

      Nameserver ns = (Nameserver)o;
      if(hostName.equals(ns.hostName) &&
         Arrays.equals(ipv4, ns.ipv4))
      {
        return true;
      }

      return false;
    }

    @Override
    public int hashCode()
    {
      int result = 17;
      result = 31 * result + Arrays.hashCode(ipv4);
      result = 31 * result + hostName.hashCode();
      return result;
    }

    @Override
    public String toString()
    {
      return hostName + "(" + String.valueOf(0xFF & ipv4[0]) + "." +
                              String.valueOf(0xFF & ipv4[1]) + "." +
                              String.valueOf(0xFF & ipv4[2]) + "." +
                              String.valueOf(0xFF & ipv4[3]) + ")";
    }
  }

  ////////////////////////////////////////////////////////////////////////////

  /**
   * Class used to lookup resource records in our internal cache
   */
  private class ResourceRecordKey
  {
    public ResourceRecordKey(String n, QType t, QClass q)
    {
      name = n;
      type = t;
      qClass = q;
    }

    public String name;
    public QType type;
    public QClass qClass;

    @Override
    public boolean equals(Object o)
    {
      if(o == this)
        return true;

      if(!(o instanceof ResourceRecordKey))
        return false;

      ResourceRecordKey rhs = (ResourceRecordKey)o;
      if(name.equals(rhs.name) &&
         type == rhs.type &&
         qClass == rhs.qClass)
      {
        return true;
      }
      return false;
    }

    @Override
    public int hashCode()
    {
      int result = 17;
      result = 31 * result + name.hashCode();
      result = 31 * result + type.getValue();
      result = 31 * result + qClass.getValue();
      return result;
    }

    @Override
    public String toString()
    {
      String result = "( " + name + ", " + type + ", " + qClass + ")";
      return result;
    }
  }

  ////////////////////////////////////////////////////////////////////////////
  private class Zone
  {
    public Set<Nameserver> knownNameServers =
      Collections.synchronizedSet(new HashSet<Nameserver>());
  }

  ////////////////////////////////////////////////////////////////////////////

  /**
   * This represents a query that the resolver is currently working
   * on resolving the answer to.
   */
  private class Query
  {
    // TODO: This needs to be a stack, or some structure that allows
    // us to store things we're awaiting on from questions we launched
    // as a result of our query.
    public Question question;
    public Zone currentZone;

    // Used to store nameservers we're currently awaiting responses
    // from.
    public Selector pendingResponses;
    public int pendingResponseNumRetry;
  }

  public Answer.ResourceRecord query(String name, QType type, QClass qClass)
  {
    Question.Builder qb = new Question.Builder();
    qb.setID(nextID());
    qb.setOpCode(OpCode.QUERY);
    qb.addQuestion(name, type, qClass);
    Question initialQuestion = qb.build();

    // Generate the query object and store the root zone as the
    // current query.
    Query query = new Query();
    query.pendingResponseNumRetry = 5;
    query.question = initialQuestion;
    query.currentZone = rootZone();

    // Push the query to the inpr query queue
    inProgressQueries.addFirst(query);

    // The algorithm is roughly thus.
    // 1). Check if the query is available from the cache of
    // existing RRs we've got.
    // 2). If not, deliver to all name servers in the zone nearest
    // to us.
    // 3). Process the response, if it gives us the answer return it
    // (after caching) otherwise follow any links.
    //
    // When we allow for multiple queries we'll basically move this
    // loop into a 'job queue' and each job will return a future that
    // contains the appropriate rr.

    // Work the query in progress queue until it's empty
    while(inProgressQueries.peek() != null)
    {
      // Always work from the top of the stack.
      Query aq = inProgressQueries.peekFirst();

      QuestionRecord qr = aq.question.getQuestions().get(0);

      Answer.ResourceRecord rrFromCache =
        recordInCache(qr.qname, qr.qtype, qr.qclass);

      if(inProgressQueries.size() == 1 && rrFromCache != null)
      {
        return rrFromCache;
      }
      else if(inProgressQueries.size() > 1 && rrFromCache != null)
      {
        // Pop ourselves of the stack and continue
        System.out.println("Found answer" + rrFromCache);
        inProgressQueries.removeFirst();
        continue;
      }
      else
      {
        // Nothing in the local cache? Send out the question to
        // all name servers in the current zone.
        deliverQueryToCurrentZone(aq.question, aq);

        // Next, wait for a response from any of the name servers.
        while(aq.pendingResponseNumRetry > 0)
        {
          if(procesDNSResponse(aq) == 0) break;
        }
      }
    }

    return null;
  }

  /**
   * Return -1 if we timed out, else return 0.
   */
  private int procesDNSResponse(Query aq)
  {
    try
    {
      int numReadyChannels =
        aq.pendingResponses.select(DnsResolver.PER_SELECT_TIMEOUT);

      if(numReadyChannels > 0)
      {
        // Get the channels that say they're ready.
        Set<SelectionKey> selectedKeys =
          aq.pendingResponses.selectedKeys();

        // Get any responses from the server.
        Iterator<SelectionKey> keyIterator = selectedKeys.iterator();
        while(keyIterator.hasNext())
        {
          SelectionKey key = keyIterator.next();

          if(key.isReadable())
          {
            DatagramChannel channel = (DatagramChannel)key.channel();
            ByteBuffer recvBuff = ByteBuffer.allocate(65536);
            int bytesReady = channel.read(recvBuff);

            Answer response = Answer.answerFromByteStream(recvBuff.array());
            cacheAnswer(response);

            // See if we have a referral, this means we need to generate
            // a new request.
            if(response.isFullReferralResponse())
            {
              System.out.println("Got referral.");
              // Create a new zone which contains the name servers we're
              // being suggested could help.
              Zone newZone = zoneFromResponse(response);

              // Reset the active query.
              aq.pendingResponseNumRetry = 5;
              aq.currentZone = newZone;

              // Break out the loop and let the next round check if we got
              // lucky in the cache, or, transmit this new query.
              return 0;
            }
//            else if(response.hasNameserverHints())
//            {
//              // We might hit this if we reached a name server that new of
//              // another name server that could provide us with a response
//              // but wasn't kind enough to provide us with a glue record!
//              //
//              // In which case, we build a new DNS Question packet.
//              // Generate a new Query stick it on the top of the stack
//              // and start the processing loop again!
//              Question.Builder qb = new Question.Builder();
//              qb.setID(nextID());
//              qb.setOpCode(OpCode.QUERY);
//
//              List<Answer.ResourceRecord> nsList =
//                response.getAuthorityNameservers();
//              Answer.ResourceRecord first = nsList.get(0);
//              qb.addQuestion(new String(first.data), first.type, first.recordClass);
//            //  for(Answer.ResourceRecord rr : nsList)
//            //  {
//            //    System.out.println("Adding " + rr + " to new question");
//            //    qb.addQuestion(new String(rr.data), rr.type, rr.recordClass);
//            //  }
//
//              Question newQuestion = qb.build();
//
//              // Generate the query object and store the root zone as the
//              // current query.
//              Query query = new Query();
//              query.pendingResponseNumRetry = 5;
//              query.question = newQuestion;
//              query.currentZone = rootZone();
//
//              // Push the query to the inpr query queue
//              inProgressQueries.addFirst(query);
//              return 0;
//            }
          }

          keyIterator.remove();
        }
      }
      else
      {
        System.out.println("Timed out!");
        --aq.pendingResponseNumRetry;
        return -1;
      }
    }
    catch(IOException e)
    {
      e.printStackTrace();
    }

    return 0;
  }

  private Answer.ResourceRecord recordInCache(String name, QType type, QClass qClass)
  {
    ResourceRecordKey key = new ResourceRecordKey(name, type, qClass);

    Deque<Answer.ResourceRecord> records = rrCache.get(key);
    if(records == null)
      return null;

    // Always take the most recent (which is the first element)
    return records.peekFirst();
  }


  private Zone zoneFromResponse(Answer response)
  {
    Zone result = new Zone();
    Map<String, Answer.ResourceRecord> newNameServers = response.referralNameservers();

    for(Map.Entry<String, Answer.ResourceRecord> ns : newNameServers.entrySet())
    {
      byte[] ipv4 = ns.getValue().data;

      result.knownNameServers.add(
          new Nameserver(ns.getKey(), ipv4[0], ipv4[1], ipv4[2], ipv4[3]));
    }
    return result;
  }

  // Delivers the specified query to al) name servers in the
  // current zone.
  private void deliverQueryToCurrentZone(Question query, Query aq)
  {
    try
    {
      // Create a new Selector to allow us to multiplex the UDP packets
      if(aq.pendingResponses != null)
      {
        // TODO: Also clean up all channels in the selector calling close
        // on them, otherwise we'll run out of filehandles!!!!
        aq.pendingResponses.close();
      }

      // Open a new selector.
      aq.pendingResponses = Selector.open();

      byte[] packetData = query.getPacket();
      System.out.println(aq.currentZone.knownNameServers.size() + " NS to contact");
      // Send packet to each name server in the current zone.
      for(Nameserver ns : aq.currentZone.knownNameServers)
      {
        System.out.println("Contacting " + ns);
        // Create a DatagramChannel and connect to the specified DNS Server.
        DatagramChannel channel = DatagramChannel.open();
        InetAddress address = InetAddress.getByAddress(ns.address());
        channel.connect(new InetSocketAddress(address, 53));

        // Send and query to each DNS server.
        ByteBuffer buff = ByteBuffer.wrap(packetData);
        int bytesWritten = channel.write(buff);

        // Channel will be used in a selector so set to non-blocking
        channel.configureBlocking(false);
        channel.register(aq.pendingResponses, SelectionKey.OP_READ);
      }
    }
    catch(IOException e)
    {
      e.printStackTrace();
      System.exit(-1);
    }
  }

  private Zone rootZone()
  {
    Zone result = new Zone();
    result.knownNameServers.add(new Nameserver("A.root-servers.net", 198, 41, 0, 4));
    result.knownNameServers.add(new Nameserver("B.root-servers.net", 192, 228, 79, 201));
    result.knownNameServers.add(new Nameserver("C.root-servers.net", 192, 33, 4, 12));
    result.knownNameServers.add(new Nameserver("D.root-servers.net", 199, 7, 91, 13));
    result.knownNameServers.add(new Nameserver("E.root-servers.net", 192, 203, 230, 10));
    result.knownNameServers.add(new Nameserver("F.root-servers.net", 192, 5, 5, 241));
    result.knownNameServers.add(new Nameserver("G.root-servers.net", 192, 112, 36, 4));
    result.knownNameServers.add(new Nameserver("H.root-servers.net", 128, 63, 2, 53));
    result.knownNameServers.add(new Nameserver("I.root-servers.net", 192, 36, 148, 17));
    result.knownNameServers.add(new Nameserver("J.root-servers.net", 192, 58, 128, 30 ));
    result.knownNameServers.add(new Nameserver("K.root-servers.net", 193, 0, 14, 129 ));
    result.knownNameServers.add(new Nameserver("L.root-servers.net", 199, 7, 83, 42 ));
    result.knownNameServers.add(new Nameserver("M.root-servers.net", 202, 12, 27, 33 ));
    
    return result;
  }

  /**
   * Will return a value in the first 16 bits of the result that are
   * to be used in the ID field of the DNS header.
   */
  private int nextID()
  {
    nextQueryID++;
    if(nextQueryID > 65535)
      nextQueryID = 0;
    
    return nextQueryID;
  }

  private void cacheAnswer(Answer answer)
  {
    // Authority answers.
    List<Answer.ResourceRecord> authAnswers =
      answer.getAuthorityAnswers();

    for(Answer.ResourceRecord rr : authAnswers)
    {
      addRRToCache(rr);
    }
  }

  private void addRRToCache(Answer.ResourceRecord rr)
  {
    ResourceRecordKey key =
      new ResourceRecordKey(rr.domainName, rr.type, rr.recordClass);

    Deque<Answer.ResourceRecord> records = rrCache.get(key);
    if(records == null)
    {
      records = new LinkedList<>();
      rrCache.put(key, records);
    }

    records.addFirst(rr);
  }

  private int nextQueryID;
  private static final int PER_SELECT_TIMEOUT = 2000;

  // Used to maintain a stack
  private Deque<Query> inProgressQueries;

  // Cache of resource records that we've received while traversing.
  Map<ResourceRecordKey, Deque<Answer.ResourceRecord>> rrCache;
}

