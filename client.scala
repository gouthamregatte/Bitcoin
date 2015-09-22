import akka.actor._
import com.typesafe.config.ConfigFactory
import java.net.InetAddress
import scala.collection.mutable.ArrayBuffer
import akka.routing.RoundRobinRouter
import java.security.MessageDigest
import scala.util.control.Breaks
import scala.util.Random


sealed trait BitCoinMessage
case class LocalMessage(ab:ArrayBuffer[String]) extends BitCoinMessage
case class RemoteMessage(ab:ArrayBuffer[String]) extends BitCoinMessage
case class AllocateWork(kValue:Int) extends BitCoinMessage
case class StartMaster(kValue: Int) extends BitCoinMessage
case class StartWorker(stringlen: Int,kValue :Int) extends BitCoinMessage
case class ReplyToMaster(output: ArrayBuffer[String] ) extends BitCoinMessage
case class SHAHashVal(input: ArrayBuffer[String]) extends BitCoinMessage
object C {

  def main(args: Array[String]) {
    val hostname = InetAddress.getLocalHost.getHostName
    val config = ConfigFactory.parseString(
      """akka{
		  		actor{
		  			provider = "akka.remote.RemoteActorRefProvider"
		  		}
		  		remote{
                   enabled-transports = ["akka.remote.netty.tcp"]
		  			netty.tcp{
						hostname = "127.0.0.1"
						port = 0
					}
				}     
    	}""")

    implicit val system = ActorSystem("LocalSystem", ConfigFactory.load(config))
  
    val proxyActor2 = system.actorOf(Props(new ProxyActor2(args(0))), name = "proxyActor2") // the local actor
       
    val listener = system.actorOf(Props(new Listener(proxyActor2)), name = "listener")
    
    val master =  system.actorOf(Props(new Master(2,25, listener)),name = "master")

    val proxyActor = system.actorOf(Props(new ProxyActor(args(0),master)), name = "proxyActor") // the local actor
    proxyActor ! "GETWORKFROMSERVER" // start the action
    
  }
}

class ProxyActor(ip: String , master:ActorRef) extends Actor {
  println("akka.tcp://HelloRemoteSystem@" + ip + ":5150/user/RemoteActor")
  // create the remote actor
  val remote = context.actorFor("akka.tcp://HelloRemoteSystem@" + ip + ":5150/user/RemoteActor")

  def receive = {
    case "GETWORKFROMSERVER" =>
      remote ! "GETWORK"
    case AllocateWork(kValue) =>
       println("recd from server :" + kValue)
       master ! StartMaster(kValue)
    case SHAHashVal(input) =>
       remote ! RemoteMessage(input)
    case msg: String =>
      println(s"LocalActor received message and the k Value is: '$msg'")
  }
}

class ProxyActor2(ip: String) extends Actor {
  println("akka.tcp://HelloRemoteSystem@" + ip + ":5150/user/RemoteActor")
  // create the remote actor
  val remote = context.actorFor("akka.tcp://HelloRemoteSystem@" + ip + ":5150/user/RemoteActor")

  def receive = {
    case LocalMessage(input) =>
       println("Sending to Remote")
       remote ! RemoteMessage(input)
       println("Sent to Remote") 
    case _ =>
      println("Unknown message in Proxyactor")
  }
}


class Master(WorkersCount: Int, MessageCount: Int, listener: ActorRef) extends Actor {

  var result: ArrayBuffer[String] = new ArrayBuffer[String]()
  var nrOfResults: Int = _
  val start: Long = System.currentTimeMillis

  val workerRouter = context.actorOf(
    Props[Worker].withRouter(RoundRobinRouter(WorkersCount)), name = "workerRouter")

  def receive = {

    case StartMaster(kValue) =>
      for (i <- 0 until MessageCount) workerRouter ! StartWorker(10 + i % 10,kValue)
    case ReplyToMaster(output) =>
      result ++= output
      nrOfResults += 1
      println("Reached Master")
      if (nrOfResults == MessageCount) {
        listener ! SHAHashVal(result)
        println("Done!! Sent to Listener")
        // Stops this actor and all its supervised children
        context.stop(self)
      } else {
        println("  ")
        println("nrOfResults : " + nrOfResults)
      }
    case _ =>
      println("Unknownn")
  }

}

class Worker extends Actor {

  def receive = {

    case StartWorker(stringlen,kValue) => 
      println("Done!! Sent to Master after Mining") 
      sender ! ReplyToMaster(mineBitCoins(stringlen,kValue))
  

  }
  def mineBitCoins(stringlen: Int, kValue : Int): ArrayBuffer[String] = {
    
    val main: String = "!\"#$%&'()*+,-./0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWXYZ[\\]^_`abcdefghijklmnopqrstuvwxyz{|}~"
    val main2: String = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"

    var result = new ArrayBuffer[String]()

    for (l <- 0 until 100000) {
      var input: String = "pmsaisandeep"
      for (i <- 0 until stringlen) {
        var k: Int = Random.nextInt(main2.length())
        input += main2.charAt(k) //Random.alphanumeric(255) 
      }

      val md: MessageDigest = MessageDigest.getInstance("SHA-256");

      val password: String = input //"pmsaisandeepdummy"

      md.update(password.getBytes())

      val ans: Array[Byte] = md.digest()

      var ab = new ArrayBuffer[String]()

      var found: Boolean = false;
      var temp:String=null

      val loop = Breaks
      loop.breakable {
        for (i <- 0 to ans.length - 1) {

          var s: String = Integer.toHexString((ans(i) & 0xFF))

          s = if (s.length() == 1) "0" + s else s
          
          if (s.charAt(0) == 0 && i == 1 && found) {
            result += input;
          }
          ab.+=(s)

        }
        
        temp= ab.toArray.mkString("")
        
        var strlenString : String =""
          
          for(l <-1 to kValue){
            strlenString +="0"
          }
        
        if(temp.startsWith(strlenString)){
          found =true;
        }
        
      }
      
      //println("found :  " + found)
      
      if(found){
       result += input
       result += temp
       println("input :" + input + "  output : " + temp)
      }
    }
    result
  }
}

class Listener(proxyActor2: ActorRef) extends Actor {
  def receive = {
    case SHAHashVal(input) =>
      {
        println("Done!! Before Sending to proxyActor2")
        proxyActor2 ! LocalMessage(input)
        println("Done!! Sent to proxyActor2")
      }
   // context.system.shutdown()
  }
}
