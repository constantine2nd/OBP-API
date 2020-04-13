package code.remotedata

import akka.pattern.ask
import code.actorsystem.ObpActorInit
import code.api.pemusage.PemUsageProviderTrait

import scala.concurrent.Future


object RemotedataPemUsage extends ObpActorInit with PemUsageProviderTrait {

  val cc = RemotedataPemUsage

  override def checkPem(pem: Option[String], consumerId: String, userId: String): Future[Boolean] = {
    (actor ? cc.checkPem(pem, consumerId, userId)).mapTo[Boolean]
  }

  def checkPemSync(pem: Option[String], consumerId: String, userId: String): Boolean = getValueFromFuture(
    (actor ? cc.checkPemSync(pem, consumerId, userId)).mapTo[Boolean]
  )

}
