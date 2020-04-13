package code.api.pemusage


import code.util.Helper.MdcLoggable
import com.openbankproject.commons.ExecutionContext.Implicits.global
import net.liftweb.common.Full
import net.liftweb.mapper._
import net.liftweb.util.SecurityHelpers

import scala.collection.immutable.List
import scala.concurrent.Future

object MappedPemUsageProvider extends PemUsageProviderTrait with MdcLoggable {
  def checkPemSync(pem: Option[String], consumerId: String, userId:  String): Boolean = {
    pem match {
      case Some(pem) => // There is the PEM in a request header
        val hash = SecurityHelpers.hash256(pem)
        PemUsage.findAll(By(PemUsage.PemHash, hash)) match {
          case List() => // First usage of the PEM
            Full(PemUsage.create.PemHash(hash).ConsumerId(consumerId).LastUserId(userId).saveMe())
            true
          case List(row) if SecurityHelpers.secureEquals(row.consumerId, consumerId) == true => // Correct Consumer
            true
          case List(row) if SecurityHelpers.secureEquals(row.consumerId, consumerId)  == false => // Incorrect Consumer
            false
          case otherwise => // Unhandled case. Log it and return false
            logger.debug(otherwise)
            false
        }
      case None => // There is NO the PEM in a request header at all
        true
    }
    
  }
  def checkPem(pem: Option[String], consumerId: String, userId:  String): Future[Boolean] = {
    Future(checkPemSync(pem, consumerId, userId))
  }
}

class PemUsage extends PemUsageTrait with LongKeyedMapper[PemUsage] with IdPK with CreatedUpdated {
  override def getSingleton = PemUsage
  object PemHash extends MappedString(this, 64)
  object ConsumerId extends MappedString(this, 50)
  object LastUserId extends MappedString(this, 50)

  def pemHash: String = PemHash.get
  def consumerId: String = ConsumerId.get
  def lastUserId: String = LastUserId.get

}

object PemUsage extends PemUsage with LongKeyedMetaMapper[PemUsage] {
  override def dbIndexes: List[BaseIndex[PemUsage]] = UniqueIndex(PemHash, ConsumerId) :: super.dbIndexes
}
