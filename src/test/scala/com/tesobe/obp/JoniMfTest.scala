package com.tesobe.obp

import com.tesobe.obp.JoniMf._
import com.tesobe.obp.GetBankAccounts._
import com.tesobe.obp.RunMockServer.startMockServer
import com.tesobe.obp.RunMockServer._
import com.typesafe.scalalogging.StrictLogging
import net.liftweb.json.JsonAST.JValue
import org.scalatest.{BeforeAndAfterAll, FunSuite, Matchers}

class JoniMfTest extends FunSuite with Matchers with BeforeAndAfterAll with StrictLogging{

  override def beforeAll() {
    startMockServer
  }
  val username = "N7jut8d"
  val mfToken = "?+1         81433020102612"
  val mfresult: String = jsonToString("joni_result.json")
  val sane_result: String = replaceEmptyObjects(mfresult)
  implicit val formats = net.liftweb.json.DefaultFormats
  
  test("getJoniMf gets response from the mainframe"){
    assert(mfresult.contains("SDR"))
  }
  
  test("replaceEmptyObjects replaces all {} with \"\" "){
    assert(!sane_result.contains("{}"))
  }
  
  test("getJoni creates Json AST"){
    val jsonAst: JValue = getJoni("joni_result.json")
  }

  test("extact case class from Json AST"){
    val jsonAst: JValue = getJoni("joni_result.json")
    logger.debug(jsonAst.toString)
    val JoniCall: JoniMfUser = jsonAst.extract[JoniMfUser]
    assert(JoniCall.SDR_JONI.esbHeaderResponse.responseStatus.callStatus == "Success")
    assert(JoniCall.SDR_JONI.MFAdminResponse.returnCode == "0")
    assert(JoniCall.SDR_JONI.esbHeaderResponse.responseStatus.errorDesc
      == Some(""))
    JoniCall.SDR_JONI.SDR_LAK_SHEDER.SDRL_LINE(2).SDR_CHN(0).SDRC_LINE.SDRC_CHN.SDRC_CHN_CHN should be ("20102642")
    JoniCall.SDR_JONI.MFTOKEN should be ("""?+1         81433020102612""")


  }
   test("getBasicBankAccountsForUser"){
    val accounts = getBasicBankAccountsForUser("joni_result.json")
   accounts should be (List(
     BasicBankAccount("3565953", "616", "330", mfToken, AccountPermissions(true,false,false)), 
     BasicBankAccount("50180983", "616", "430", mfToken, AccountPermissions(true,false,true)),
     BasicBankAccount("50180963", "616", "330", mfToken, AccountPermissions(true,false,false)),
     //BasicBankAccount("20102642","814","0", AccountPermissions(true,false,false)),
     BasicBankAccount("20102612", "814", "330", mfToken, AccountPermissions(true,false,false)),
     //BasicBankAccount("20102632", "814", "999", AccountPermissions(true,false,false)),
     BasicBankAccount("20105505", "814", "330", mfToken, AccountPermissions(true,false,false))
   ))
   
  }
/*  test("getBankAccountsForUser without leading account"){
    val accounts = getBasicBankAccountsForUser("joni_result_no_lead.json")
    accounts should be (List(
      BasicBankAccount("3565953", "616", "330", "<M/          81433020102612", AccountPermissions(true,false,false)),
      BasicBankAccount("50180983", "616", "430", "<M/          81433020102612", AccountPermissions(true,false,true)),
      BasicBankAccount("50180963", "616", "330", "<M/          81433020102612", AccountPermissions(true,false,false)),
      //BasicBankAccount("20102642","814","0", AccountPermissions(true,false,false)),
      //BasicBankAccount("20102632", "814", "999", AccountPermissions(true,false,false)),
      BasicBankAccount("20105505", "814", "330", "<M/          81433020102612", AccountPermissions(true,false,false)),
      BasicBankAccount("20102612", "814", "330", "<M/          81433020102612", AccountPermissions(true,false,false))
    ))

  }*/

  test("getMFToken gets the MFTOKEN, assuming / will be  escaped later"){
    val mftoken = getMFToken("joni_result.json")
    mftoken should be (mfToken)
  }
  
/*  test("getJoniMfHttp does useful things"){
    val result = getJoniMfHttp("N7jut8d")
    logger.debug(result)
  }*/
  
  test("getJoniMfHttpApache does something useful"){
    val result = getJoniMfHttpApache("N7jut8d")
    logger.debug(result.toString)
  }

  override def afterAll() {
    com.tesobe.obp.RunMockServer.mockServer.stop()
  }
}