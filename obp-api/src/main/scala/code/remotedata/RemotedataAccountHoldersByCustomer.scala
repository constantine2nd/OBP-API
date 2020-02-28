package code.remotedata

import akka.pattern.ask
import code.accountholders.customer.{AccountHoldersByCustomer, AccountHoldersByCustomerProvider, RemotedataAccountHoldersByCustomerCaseClasses}
import code.actorsystem.ObpActorInit
import com.openbankproject.commons.model._
import net.liftweb.common.Box


object RemotedataAccountHoldersByCustomer extends ObpActorInit with AccountHoldersByCustomerProvider {

  val cc = RemotedataAccountHoldersByCustomerCaseClasses

  override def getOrCreateAccountHolder(customer: Customer, bankAccountUID :BankIdAccountId): Box[AccountHoldersByCustomer] = getValueFromFuture(
    (actor ? cc.getOrCreateAccountHolder(customer: Customer, bankAccountUID :BankIdAccountId)).mapTo[Box[AccountHoldersByCustomer]]
  )

  override def getAccountHolders(bankId: BankId, accountId: AccountId): Set[Customer] = getValueFromFuture(
    (actor ? cc.getAccountHolders(bankId, accountId)).mapTo[Set[Customer]]
  )
  
  override def getAccountsHeld(bankId: BankId, customer: Customer): Set[BankIdAccountId] = getValueFromFuture(
    (actor ? cc.getAccountsHeld(bankId: BankId, customer: Customer)).mapTo[Set[BankIdAccountId]]
  )

  override def getAccountsHeldByUser(customer: Customer): Set[BankIdAccountId] = getValueFromFuture(
    (actor ? cc.getAccountsHeldByUser(customer: Customer)).mapTo[Set[BankIdAccountId]]
  )

  def bulkDeleteAllAccountHolders(): Box[Boolean] = getValueFromFuture(
    (actor ? cc.bulkDeleteAllAccountHolders()).mapTo[Box[Boolean]]
  )

}
