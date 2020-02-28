package code.accountholders.customer

import code.accountholders.MapperAccountHolders
import code.customer.MappedCustomer
import code.util.Helper.MdcLoggable
import code.util.UUIDString
import com.openbankproject.commons.model.{AccountId, BankId, BankIdAccountId, Customer}
import net.liftweb.common._
import net.liftweb.mapper._


/**
  * the link List(customerId) <--> bankId + accountId 
  */
class AccountHoldersByCustomer extends LongKeyedMapper[AccountHoldersByCustomer] with IdPK {

  def getSingleton: AccountHoldersByCustomer.type = AccountHoldersByCustomer

  object customerId extends UUIDString(this)
  object bankId extends UUIDString(this)
  object accountId extends UUIDString(this)

}

object AccountHoldersByCustomer extends AccountHoldersByCustomer with LongKeyedMetaMapper[AccountHoldersByCustomer] {
  override def dbIndexes: List[BaseIndex[AccountHoldersByCustomer]] =  UniqueIndex(bankId, accountId, customerId) :: super.dbIndexes
}

object MappedAccountHoldersByCustomerProvider extends AccountHoldersByCustomerProvider with MdcLoggable {
  //Note, this method, will not check the existing of bankAccount, any value of BankIdAccountId
  //Can create the MapperAccountHolders.
  def getOrCreateAccountHolder(customer: Customer, bankIdAccountId :BankIdAccountId): Box[AccountHoldersByCustomer] ={

    val mapperAccountHolder = AccountHoldersByCustomer.find(
      By(AccountHoldersByCustomer.customerId, customer.customerId),
      By(AccountHoldersByCustomer.bankId, bankIdAccountId.bankId.value),
      By(AccountHoldersByCustomer.accountId, bankIdAccountId.accountId.value)
    )

    mapperAccountHolder match {
      case Full(_) => {
        logger.debug(
          s"getOrCreateAccountHolder --> the accountHolder has been existing in server !"
        )
        mapperAccountHolder
      }
      case Empty => {
        val holder: AccountHoldersByCustomer = AccountHoldersByCustomer.create
          .bankId(bankIdAccountId.bankId.value)
          .accountId(bankIdAccountId.accountId.value)
          .customerId(customer.customerId)
          .saveMe
        logger.debug(
          s"getOrCreateAccountHolder--> create account holder: $holder"
        )
        Full(holder)
      }
      case Failure(msg, t, c) => Failure(msg, t, c)
      case ParamFailure(x,y,z,q) => ParamFailure(x,y,z,q)
    }

  }


  def getAccountHolders(bankId: BankId, accountId: AccountId): Set[Customer] = {
    val accountHolders = AccountHoldersByCustomer.findAll(
      By(AccountHoldersByCustomer.bankId, bankId.value),
      By(AccountHoldersByCustomer.accountId, accountId.value)
    )

    //accountHolders --> customer
    accountHolders.flatMap { accHolder =>
      MappedCustomer.find(By(MappedCustomer.mCustomerId, accHolder.customerId.get))
    }.toSet
  }

  def getAccountsHeld(bankId: BankId, customer: Customer): Set[BankIdAccountId] = {
    val accountHolders = AccountHoldersByCustomer.findAll(
      By(AccountHoldersByCustomer.bankId, bankId.value),
      By(AccountHoldersByCustomer.customerId, customer.customerId)
    )
    transformHolderToAccount(accountHolders)
  }

  def getAccountsHeldByUser(customer: Customer): Set[BankIdAccountId] = {
    val accountHolders = AccountHoldersByCustomer.findAll(
      By(AccountHoldersByCustomer.customerId, customer.customerId)
    )
    transformHolderToAccount(accountHolders)
  }

  private def transformHolderToAccount(accountHolders: List[AccountHoldersByCustomer]): Set[BankIdAccountId] = {
    //accountHolders --> BankIdAccountIds
    accountHolders.map { accHolder =>
      BankIdAccountId(BankId(accHolder.bankId.get), AccountId(accHolder.accountId.get))
    }.toSet
  }

  def bulkDeleteAllAccountHolders(): Box[Boolean] = {
    Full( MapperAccountHolders.bulkDelete_!!() )
  }
}



