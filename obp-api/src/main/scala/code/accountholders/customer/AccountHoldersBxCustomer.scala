package code.accountholders.customer

import code.api.util.APIUtil
import code.remotedata.RemotedataAccountHoldersByCustomer
import com.openbankproject.commons.model._
import net.liftweb.common.Box
import net.liftweb.util.SimpleInjector


object AccountHoldersByCustomerDI extends SimpleInjector {

  val accountHolders = new Inject(buildOne _) {}

  def buildOne: AccountHoldersByCustomerProvider =
    APIUtil.getPropsAsBoolValue("use_akka", false) match {
      case false  => MappedAccountHoldersByCustomerProvider
      case true => RemotedataAccountHoldersByCustomer     // We will use Akka as a middleware
    }
}

trait AccountHoldersByCustomerProvider {

  def getAccountHolders(bankId: BankId, accountId: AccountId): Set[Customer]
  def getAccountsHeld(bankId: BankId, customer: Customer): Set[BankIdAccountId]
  def getAccountsHeldByUser(customer: Customer): Set[BankIdAccountId]
  def getOrCreateAccountHolder(customer: Customer, bankAccountUID :BankIdAccountId): Box[AccountHoldersByCustomer] //There is no AccountHolder trait, database structure different with view
  def bulkDeleteAllAccountHolders(): Box[Boolean]
}

class RemotedataAccountHoldersByCustomerCaseClasses {
  case class getAccountHolders(bankId: BankId, accountId: AccountId)
  case class getAccountsHeld(bankId: BankId, customer: Customer)
  case class getAccountsHeldByUser(customer: Customer)
  case class getOrCreateAccountHolder(customer: Customer, bankAccountUID: BankIdAccountId)
  case class bulkDeleteAllAccountHolders()
}

object RemotedataAccountHoldersByCustomerCaseClasses extends RemotedataAccountHoldersByCustomerCaseClasses




