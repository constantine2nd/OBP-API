package code.api.pemusage

import code.api.util.APIUtil
import code.remotedata.RemotedataPemUsage
import net.liftweb.util.SimpleInjector

import scala.concurrent.Future

object PemUsageDI extends SimpleInjector {
  val pemUsage = new Inject(buildOne _) {}
  def buildOne: PemUsageProviderTrait = APIUtil.getPropsAsBoolValue("use_akka", false) match {
    case false  => MappedPemUsageProvider
    case true => RemotedataPemUsage   // We will use Akka as a middleware
  }
}

trait PemUsageProviderTrait {
  def checkPem(pem: Option[String], consumerId: String, userId:  String): Future[Boolean]
  def checkPemSync(pem: Option[String], consumerId: String, userId:  String): Boolean
}

trait PemUsageTrait {
  def pemHash: String 
  def consumerId: String
  def lastUserId: String
}


class RemotedataPemUsageCaseClasses {
  case class checkPem(pem: Option[String], consumerId: String, userId:  String)
  case class checkPemSync(pem: Option[String], consumerId: String, userId:  String)
}

object RemotedatPemUsageCaseClasses extends RemotedataPemUsageCaseClasses
