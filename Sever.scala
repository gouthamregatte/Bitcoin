import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import java.net.InetAddress
import akka.actor._
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

object server {
  def main(args: Array[String]) {

    val hostname = InetAddress.getLocalHost.getHostName
    val config = ConfigFactory.parseString(
      """ 
     akka{ 
    		actor{ 
    			provider = "akka.remote.RemoteActorRefProvider" 
    		} 
    		remote{ 
                enabled-transports = ["akka.remote.netty.tcp"] 
            netty.tcp{ 
    			hostname = "192.168.0.27" 
    			port = 5150 
    		} 
      }      
    }""")
    
    val kValue = Integer.parseInt(args(0))
    
    val system = ActorSystem("HelloRemoteSystem", ConfigFactory.load(config))
    val remoteActor = system.actorOf(Props(new RemoteActor(kValue)), name = "RemoteActor")
    remoteActor ! "The Server is Up & Running"
    
     val listener = system.actorOf(Props[Listener], name = "listener")

    // create the master
    val master = system.actorOf(Props(new Master(2, 25, listener)), name = "master")

    // start the calculation
    master ! StartMaster(kValue)
    
  }
}

class Listener extends Actor {
  def receive = {
    case SHAHashVal(input) =>
      {
        println("Bitcoins found on Server :")
        for(i <- 0 until input.length){
           if(i%2==0) print(input(i) +"    :  ")
           else println(input(i))
        }  
      }
   // context.system.shutdown()
  }
}

class RemoteActor(kValue : Int) extends Actor {
  def receive = {
    case "GETWORK" => {
      sender ! AllocateWork(kValue)
    }
    case RemoteMessage(ab) => {
      println("Bitcoins Recevied from client")
      println("Length   :  " + (ab.length/2))
      for (i <- 0 until ab.length) {
        if (i % 2 == 0) print(ab(i) + "   :  ")
        else println(ab(i))
      }
    }
    case _ => {
      println("Unknown Mesasge Recevied at Server")
    }

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
      println("Unknown Message Received at Master")
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

      val password: String = input

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
      // println("input :" + input + "  output : " + temp)
      }
    }
    result
  }
}



