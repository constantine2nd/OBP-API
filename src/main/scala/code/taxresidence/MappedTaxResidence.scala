package code.taxresidence

import code.api.util.ErrorMessages
import code.customer.MappedCustomer
import code.util.MappedUUID
import net.liftweb.common.{Box, Empty, Failure, Full}
import net.liftweb.mapper._
import net.liftweb.util.Helpers.tryo

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object MappedTaxResidenceProvider extends TaxResidenceProvider {

  def getTaxResidenceRemote(customerId: String): Box[List[TaxResidence]] = {
    val id: Box[MappedCustomer] = MappedCustomer.find(By(MappedCustomer.mCustomerId, customerId))
    id.map(customer => MappedTaxResidence.findAll(By(MappedTaxResidence.mCustomerId, customer.id.get)))
  }
  override def getTaxResidence(customerId: String): Future[Box[List[TaxResidence]]] = {
    Future(getTaxResidenceRemote(customerId))
  }
  def addTaxResidenceRemote(customerId: String, domain: String, taxNumber: String): Box[TaxResidence] = {
    val id: Box[MappedCustomer] = MappedCustomer.find(By(MappedCustomer.mCustomerId, customerId))
    id match {
      case Full(customer) =>
        tryo(MappedTaxResidence.create.mCustomerId(customer.id.get).mDomain(domain).mTaxNumber(taxNumber).saveMe())
      case Empty =>
        Empty ?~! ErrorMessages.CustomerNotFoundByCustomerId
      case Failure(msg, _, _) =>
        Failure(msg)
      case _ =>
        Failure(ErrorMessages.UnknownError)
    }
  }
  override def addTaxResidence(customerId: String, domain: String, taxNumber: String): Future[Box[TaxResidence]] = {
    Future(addTaxResidenceRemote(customerId, domain, taxNumber))
  }
  def deleteTaxResidenceRemote(taxResidenceId: String): Box[Boolean] = {
    MappedTaxResidence.find(By(MappedTaxResidence.mTaxResidenceId, taxResidenceId)) match {
      case Full(t) => Full(t.delete_!)
      case Empty   => Empty ?~! ErrorMessages.TaxResidenceIdNotFound
      case _       => Full(false)
    }
  }
  override def deleteTaxResidence(taxResidenceId: String): Future[Box[Boolean]] = {
    Future(deleteTaxResidenceRemote(taxResidenceId))
  }
}

class MappedTaxResidence extends TaxResidence with LongKeyedMapper[MappedTaxResidence] with IdPK with CreatedUpdated {

  def getSingleton = MappedTaxResidence

  object mCustomerId extends MappedLongForeignKey(this, MappedCustomer)
  object mTaxResidenceId extends MappedUUID(this)
  object mDomain extends MappedString(this, 50)
  object mTaxNumber extends MappedString(this, 50)

  override def customerId: Long = mCustomerId.get
  override def taxResidenceId: String = mTaxResidenceId.get
  override def domain: String = mDomain.get
  override def taxNumber: String = mTaxNumber.get

}

object MappedTaxResidence extends MappedTaxResidence with LongKeyedMetaMapper[MappedTaxResidence] {
  override def dbIndexes = UniqueIndex(mCustomerId, mDomain, mTaxNumber) :: super.dbIndexes
}
