package code.remotedata

import akka.actor.Actor
import code.accountholders.customer.{MappedAccountHoldersByCustomerProvider, RemotedataAccountHoldersByCustomerCaseClasses}
import code.actorsystem.ObpActorHelper
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.model.{AccountId, BankId, BankIdAccountId, Customer}


class RemotedataAccountHoldersByCustomerActor extends Actor with ObpActorHelper with MdcLoggable {

  val mapper = MappedAccountHoldersByCustomerProvider
  val cc = RemotedataAccountHoldersByCustomerCaseClasses

  def receive = {

    case cc.getOrCreateAccountHolder(customer: Customer, account :BankIdAccountId) =>
      logger.debug(s"getOrCreateAccountHolder($customer, $account)")
      sender ! (mapper.getOrCreateAccountHolder(customer, account))
      
    case cc.getAccountHolders(bankId: BankId, accountId: AccountId) =>
      logger.debug(s"getAccountHolders($bankId, $accountId)")
      sender ! (mapper.getAccountHolders(bankId, accountId))

    case cc.getAccountsHeld(bankId: BankId, customer: Customer) =>
      logger.debug(s"getAccountsHeld($bankId, $customer)")
      sender ! (mapper.getAccountsHeld(bankId: BankId, customer))

    case cc.getAccountsHeldByUser(customer: Customer) =>
      logger.debug(s"getAccountsHeldByUser($customer)")
      sender ! (mapper.getAccountsHeldByUser(customer))
      
    case cc.bulkDeleteAllAccountHolders() =>
      logger.debug(s"bulkDeleteAllAccountHolders()")
      sender ! (mapper.bulkDeleteAllAccountHolders())

    case message => logger.warn("[AKKA ACTOR ERROR - REQUEST NOT RECOGNIZED] " + message)
  }
}

