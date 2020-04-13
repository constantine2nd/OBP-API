package code.remotedata

import akka.actor.Actor
import akka.pattern.pipe
import code.actorsystem.ObpActorHelper
import code.api.pemusage.{MappedPemUsageProvider, RemotedatPemUsageCaseClasses}
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.ExecutionContext.Implicits.global

class RemotedataPemUsageActor extends Actor with ObpActorHelper with MdcLoggable {

  val mapper = MappedPemUsageProvider
  val cc = RemotedatPemUsageCaseClasses

  def receive: PartialFunction[Any, Unit] = {
        
    case cc.checkPem(pem: Option[String], consumerId: String, userId:  String) =>
      logger.debug(s"checkPem(${pem}, ${consumerId}, ${userId})")
      mapper.checkPem(pem, consumerId, userId) pipeTo sender

    case cc.checkPemSync(pem: Option[String], consumerId: String, userId:  String) =>
      logger.debug(s"checkPem(${pem}, ${consumerId}, ${userId})")
      sender ! (mapper.checkPemSync(pem, consumerId, userId))
      
    case message => logger.warn("[AKKA ACTOR ERROR - REQUEST NOT RECOGNIZED] " + message)

  }

}


