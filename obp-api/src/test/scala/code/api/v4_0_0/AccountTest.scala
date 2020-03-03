package code.api.v4_0_0

import com.openbankproject.commons.model.ErrorMessage
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON
import code.api.util.APIUtil.DateWithMsExampleObject
import code.api.util.APIUtil.OAuth._
import code.api.util.{ApiRole, ErrorMessages}
import code.api.util.ErrorMessages.{CustomerAlreadyLinkedToUser, CustomerNotFoundByCustomerId, InitialBalanceMustBeZero, UserHasMissingRoles, UserNotLoggedIn}
import code.api.v3_1_0.CreateAccountResponseJsonV310
import code.api.v4_0_0.OBPAPI4_0_0.Implementations4_0_0
import code.entitlement.Entitlement
import code.model.dataAccess.ResourceUser
import code.usercustomerlinks.UserCustomerLink
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.AmountOfMoneyJsonV121
import com.openbankproject.commons.util.ApiVersion
import net.liftweb.json.Serialization.write
import org.scalatest.Tag

class AccountTest extends V400ServerSetup {
  /**
    * Test tags
    * Example: To run tests with tag "getPermissions":
    * 	mvn test -D tagsToInclude
    *
    *  This is made possible by the scalatest maven plugin
    */
  object VersionOfApi extends Tag(ApiVersion.v4_0_0.toString)
  object ApiEndpoint1 extends Tag(nameOf(Implementations4_0_0.getCoreAccountById))
  object ApiEndpoint2 extends Tag(nameOf(Implementations4_0_0.getPrivateAccountByIdFull))
  object ApiEndpoint3 extends Tag(nameOf(Implementations4_0_0.addAccount))

  lazy val testBankId = testBankId1
  lazy val addAccountJsonUser1 = SwaggerDefinitionsJSON.createAccountRequestJsonV310.copy(user_id = resourceUser1.userId, balance = AmountOfMoneyJsonV121("EUR","0"))
  lazy val addAccountJsonUser2 = SwaggerDefinitionsJSON.createAccountRequestJsonV310.copy(user_id = resourceUser2.userId, balance = AmountOfMoneyJsonV121("EUR","0"))
  
  
  feature(s"test $ApiEndpoint1") {
    scenario("prepare all the need parameters", VersionOfApi, ApiEndpoint1) {
      Given("We prepare the accounts in V300ServerSetup, just check the response")

      When("We send the request")
      val request = (v4_0_0_Request /"my" / "banks" / testBankId1.value/ "accounts" / testAccountId1.value / "account").GET <@ (user1)
      val response = makeGetRequest(request)

      Then("We should get a 200 and check the response body")
      response.code should equal(200)
      val moderatedCoreAccountJsonV400 = response.body.extract[ModeratedCoreAccountJsonV400]
      moderatedCoreAccountJsonV400.account_attributes.length == 0 should be (true)
      moderatedCoreAccountJsonV400.views_basic.length >= 1 should be (true)

    }
  }
  feature(s"test $ApiEndpoint2") {
    scenario("prepare all the need parameters", VersionOfApi, ApiEndpoint2) {
      Given("We prepare the accounts in V300ServerSetup, just check the response")

      lazy val bankId = randomBankId
      lazy val bankAccount = randomPrivateAccount(bankId)
      lazy val view = randomOwnerViewPermalink(bankId, bankAccount)

      When("We send the request")
      val request = (v4_0_0_Request / "banks" / bankId / "accounts" / bankAccount.id / view / "account").GET <@ (user1)
      val response = makeGetRequest(request)

      Then("We should get a 200 and check the response body")
      response.code should equal(200)
      val moderatedAccountJSON400 = response.body.extract[ModeratedAccountJSON400]
      moderatedAccountJSON400.account_attributes.length == 0 should be (true)
      moderatedAccountJSON400.views_available.length >= 1 should be (true)
    }
  }

  feature(s"test $ApiEndpoint3 - Unauthorized access") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint3, VersionOfApi) {
      When("We make a request v4.0.0")
      val request400 = (v4_0_0_Request / "banks" / testBankId.value / "accounts"  ).POST
      val response400 = makePostRequest(request400, write(addAccountJsonUser1))
      Then("We should get a 400")
      response400.code should equal(400)
      And("error should be " + UserNotLoggedIn)
      response400.body.extract[ErrorMessage].message should equal (UserNotLoggedIn)
    }
  }
  feature(s"test $ApiEndpoint3 - Authorized access") {
    scenario("We will call the endpoint with user credentials", ApiEndpoint3, VersionOfApi) {
      When("We make a request v4.0.0")
      val request400 = (v4_0_0_Request / "banks" / testBankId.value / "accounts" ).POST <@(user1)
      val response400 = makePostRequest(request400, write(addAccountJsonUser1))
      Then("We should get a 201")
      response400.code should equal(201)
      val account = response400.body.extract[CreateAccountResponseJsonV310]
      account.account_id should not be empty
      account.product_code should be (addAccountJsonUser1.product_code)
      account.`label` should be (addAccountJsonUser1.`label`)
      account.balance.amount.toDouble should be (addAccountJsonUser1.balance.amount.toDouble)
      account.balance.currency should be (addAccountJsonUser1.balance.currency)
      account.branch_id should be (addAccountJsonUser1.branch_id)
      account.user_id should be (addAccountJsonUser1.user_id)
      account.label should be (addAccountJsonUser1.label)
      account.account_routings should be (List(addAccountJsonUser1.account_routing))

      
      Then(s"We call $ApiEndpoint1 to get the account back")
      val request = (v4_0_0_Request /"my" / "banks" / testBankId.value/ "accounts" / account.account_id / "account").GET <@ (user1)
      val response = makeGetRequest(request)

      Then("We should get a 200 and check the response body")
      response.code should equal(200)
      val moderatedCoreAccountJsonV400 = response.body.extract[ModeratedCoreAccountJsonV400]
      moderatedCoreAccountJsonV400.account_attributes.length == 0 should be (true)
      moderatedCoreAccountJsonV400.views_basic.length >= 1 should be (true)
      
      
      
      
      Then("We make a request v4.0.0 but with other user")
      val requestWithNewAccountId = (v4_0_0_Request / "banks" / testBankId.value / "accounts" ).POST <@(user1)
      val responseWithNoRole = makePostRequest(requestWithNewAccountId, write(addAccountJsonUser2))
      Then("We should get a 403 and some error message")
      responseWithNoRole.code should equal(403)
      responseWithNoRole.body.toString contains(s"$UserHasMissingRoles") should be (true)


      Then("We grant the roles and test it again")
      Entitlement.entitlement.vend.addEntitlement(testBankId.value, resourceUser1.userId, ApiRole.canCreateAccount.toString)

      Then("We set non zero balance")
      val nonZeroBalance = addAccountJsonUser2.balance.copy(amount = "1")
      val responseWithWrongBalance = makePostRequest(requestWithNewAccountId, write(addAccountJsonUser2.copy(balance = nonZeroBalance)))
      Then("We should get a 400")
      responseWithWrongBalance.code should equal(400)
      And("error should be " + InitialBalanceMustBeZero)
      responseWithWrongBalance.body.extract[ErrorMessage].message should equal (InitialBalanceMustBeZero)
      
      Then("We set a non existing customer")
      val addAccountJsonNonExistingCustomer = addAccountJsonUser2.copy(customer_id = Some("Non existing value"))
      val responseWithNonExistingCustomer = makePostRequest(requestWithNewAccountId, write(addAccountJsonNonExistingCustomer))
      Then("We should get a 400")
      responseWithNonExistingCustomer.code should equal(400)
      And("error should be " + CustomerNotFoundByCustomerId)
      responseWithNonExistingCustomer.body.extract[ErrorMessage].message should startWith (CustomerNotFoundByCustomerId) 
      
      Then("We set The Customer which is already linked to an User")
      val customerId1 = createAndGetCustomerId(testBankId.value, user2)
      // User1 <--> customerId1
      UserCustomerLink.userCustomerLink.vend.createUserCustomerLink(resourceUser1.userId, customerId1, DateWithMsExampleObject, true)
      // User2 <--> customerId1
      val addAccountJsonCustomerAlreadyLinkedToUser = addAccountJsonUser2.copy(customer_id = Some(customerId1))
      val responseCustomerAlreadyLinkedToUser = makePostRequest(requestWithNewAccountId, write(addAccountJsonCustomerAlreadyLinkedToUser))
      Then("We should get a 400")
      responseCustomerAlreadyLinkedToUser.code should equal(400)
      And("error should be " + CustomerAlreadyLinkedToUser)
      responseCustomerAlreadyLinkedToUser.body.extract[ErrorMessage].message should startWith (CustomerAlreadyLinkedToUser) 
      
      Then("We set The User which is already linked to an Customer")
      val customerId2 = createAndGetCustomerId(testBankId.value, user3)
      val customerId3 = createAndGetCustomerId(testBankId.value, user3)
      // User1 <--> customerId3
      UserCustomerLink.userCustomerLink.vend.createUserCustomerLink(resourceUser1.userId, customerId3, DateWithMsExampleObject, true)
      // User1 <--> customerId2
      val addAccountJsonUserAlreadyLinkedToCustomer = addAccountJsonUser1.copy(customer_id = Some(customerId2))
      val responseUserAlreadyLinkedToCustomer = makePostRequest(requestWithNewAccountId, write(addAccountJsonUserAlreadyLinkedToCustomer))
      Then("We should get a 400")
      responseUserAlreadyLinkedToCustomer.code should equal(400)
      And("error should be " + ErrorMessages.UserAlreadyLinkedToCustomer)
      responseUserAlreadyLinkedToCustomer.body.extract[ErrorMessage].message should startWith (ErrorMessages.UserAlreadyLinkedToCustomer)
      
      // All good
      val responseWithOtherUesr = makePostRequest(requestWithNewAccountId, write(addAccountJsonUser2))
      val account2 = responseWithOtherUesr.body.extract[CreateAccountResponseJsonV310]
      account2.account_id should not be empty
      account2.product_code should be (addAccountJsonUser1.product_code)
      account2.`label` should be (addAccountJsonUser1.`label`)
      account2.balance.amount.toDouble should be (addAccountJsonUser1.balance.amount.toDouble)
      account2.balance.currency should be (addAccountJsonUser1.balance.currency)
      account2.branch_id should be (addAccountJsonUser1.branch_id)
      account2.user_id should be (addAccountJsonUser2.user_id)
      account2.label should be (addAccountJsonUser1.label)
      account2.account_routings should be (List(addAccountJsonUser1.account_routing))
    }
  }
  
}
