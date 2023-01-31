package code.views

import code.model.dataAccess.MappedBankAccount
import code.views.system.AccountAccess
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.model.{CreateViewJson, _}
import net.liftweb.common.Box
import net.liftweb.mapper.By
import net.liftweb.util.SimpleInjector

import scala.collection.immutable.List
import scala.concurrent.Future

object Views  extends SimpleInjector {

  val views = new Inject(buildOne _) {}
 
  def buildOne: Views = MapperViews
  
}

trait Views {
  
  def permissions(account : BankIdAccountId) : List[Permission]
  def permission(account : BankIdAccountId, user: User) : Box[Permission]
  def getPermissionForUser(user: User) : Box[Permission]
  /**
    * This is for @ViewPrivileges. 
    * It will first find the view object by `viewIdBankIdAccountId`
    * And then, call @getOrCreateViewPrivilege(view: View, user: User) for the view and user.
   */
  def grantAccessToCustomView(viewIdBankIdAccountId : ViewIdBankIdAccountId, user : User) : Box[View]
  def grantAccessToSystemView(bankId: BankId, accountId: AccountId, view : View, user : User) : Box[View]
  def grantAccessToMultipleViews(views : List[ViewIdBankIdAccountId], user : User) : Box[List[View]]
  def revokeAccessToMultipleViews(views : List[ViewIdBankIdAccountId], user : User) : Box[List[View]]
  def revokeAccess(viewIdBankIdAccountId : ViewIdBankIdAccountId, user : User) : Box[Boolean]
  def revokeAccessToSystemView(bankId: BankId, accountId: AccountId, view : View, user : User) : Box[Boolean]
  def revokeAllAccountAccess(bankId : BankId, accountId : AccountId, user : User) : Box[Boolean]
  def revokeAccountAccessByUser(bankId : BankId, accountId : AccountId, user : User) : Box[Boolean]

  def revokeAccessToSystemViewForConsumer(bankId: BankId, accountId: AccountId, view : View, consumerId : String) : Box[Boolean]
  def revokeAccessToCustomViewForConsumer(view : View, consumerId : String) : Box[Boolean]

  def customView(viewId : ViewId, bankAccountId: BankIdAccountId) : Box[View]
  def systemView(viewId : ViewId) : Box[View]
  def customViewFuture(viewId : ViewId, bankAccountId: BankIdAccountId) : Future[Box[View]]
  def systemViewFuture(viewId : ViewId) : Future[Box[View]]
  def getSystemViews(): Future[List[View]]

  //always return a view id String, not error here. 
  def getMetadataViewId(bankAccountId: BankIdAccountId, viewId : ViewId) = Views.views.vend.customView(viewId, bankAccountId).map(_.metadataView).openOr(viewId.value)
  
  def createView(bankAccountId: BankIdAccountId, view: CreateViewJson): Box[View]
  def createSystemView(view: CreateViewJson): Future[Box[View]]
  def removeCustomView(viewId: ViewId, bankAccountId: BankIdAccountId): Box[Boolean]
  def removeSystemView(viewId: ViewId): Future[Box[Boolean]]
  def updateCustomView(bankAccountId : BankIdAccountId, viewId : ViewId, viewUpdateJson : UpdateViewJSON) : Box[View]
  def updateSystemView(viewId : ViewId, viewUpdateJson : UpdateViewJSON): Future[Box[View]]
  
  /**
    * This will return all the public views, no requirements for accountId or userId.
    * Because the public views are totally open for everyone. 
    */
  def publicViews: (List[View], List[AccountAccess])
  def publicViewsForBank(bankId: BankId): (List[View], List[AccountAccess])
  /**
    * This will return all the views belong to the bankAccount, its own Public + Private views.
    * Do not contain any other account public views.
    */
  def assignedViewsForAccount(bankAccountId : BankIdAccountId) : List[View]
  def availableViewsForAccount(bankAccountId : BankIdAccountId) : List[View]
  
  def privateViewsUserCanAccess(user: User): (List[View], List[AccountAccess])
  def privateViewsUserCanAccess(user: User, viewIds: List[ViewId]): (List[View], List[AccountAccess])
  def privateViewsUserCanAccessAtBank(user: User, bankId: BankId): (List[View], List[AccountAccess])
  def privateViewsUserCanAccessForAccount(user: User, bankIdAccountId : BankIdAccountId) : List[View]
  
  //the following return list[BankIdAccountId], just use the list[View] method, the View object contains enough data for it.
  final def getAllFirehoseAccounts(bankId: BankId)= {
    MappedBankAccount.findAll(
      By(MappedBankAccount.bank, bankId.value)
    )
  }
  final def getPrivateBankAccounts(user : User) : List[BankIdAccountId] =  privateViewsUserCanAccess(user)._2.map(a => BankIdAccountId(BankId(a.bank_id.get), AccountId(a.account_id.get))).distinct 
  final def getPrivateBankAccounts(user : User, viewIds: List[ViewId]) : List[BankIdAccountId] =  privateViewsUserCanAccess(user, viewIds)._2.map(a => BankIdAccountId(BankId(a.bank_id.get), AccountId(a.account_id.get))).distinct 
  final def getPrivateBankAccountsFuture(user : User) : Future[List[BankIdAccountId]] = Future {getPrivateBankAccounts(user)}
  final def getPrivateBankAccountsFuture(user : User, viewIds: List[ViewId]) : Future[List[BankIdAccountId]] = Future {getPrivateBankAccounts(user, viewIds)}
  final def getPrivateBankAccounts(user : User, bankId : BankId) : List[BankIdAccountId] = getPrivateBankAccounts(user).filter(_.bankId == bankId).distinct
  final def getPrivateBankAccountsFuture(user : User, bankId : BankId) : Future[List[BankIdAccountId]] = Future {getPrivateBankAccounts(user, bankId)}
  
  def getOrCreateAccountView(bankAccountUID: BankIdAccountId, viewId: String): Box[View]
  def getOrCreateFirehoseView(bankId: BankId, accountId: AccountId, description: String) : Box[View]
  
  def getOrCreateSystemView(name: String) : Box[View]
  def getOrCreateCustomPublicView(bankId: BankId, accountId: AccountId, description: String) : Box[View]
  def createCustomRandomView(bankId: BankId, accountId: AccountId) : Box[View]

  @deprecated("There is no custom `Accountant` view, only support system owner view now","2020-01-13")
  def getOrCreateAccountantsView(bankId: BankId, accountId: AccountId, description: String) : Box[View]
  @deprecated("There is no custom `Auditor` view, only support system owner view now","2020-01-13")
  def getOrCreateAuditorsView(bankId: BankId, accountId: AccountId, description: String) : Box[View]
  @deprecated("There is no custom `owner` view, only support system owner view now","2020-01-13")
  def getOrCreateOwnerView(bankId: BankId, accountId: AccountId, description: String) : Box[View]

  def getOwners(view: View): Set[User]
  
  def removeAllPermissions(bankId: BankId, accountId: AccountId) : Boolean
  def removeAllViews(bankId: BankId, accountId: AccountId) : Boolean

  def bulkDeleteAllPermissionsAndViews() : Boolean

}