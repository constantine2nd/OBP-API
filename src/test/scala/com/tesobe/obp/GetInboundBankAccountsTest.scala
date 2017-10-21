package com.tesobe.obp
import com.tesobe.obp.GetBankAccounts._

class GetInboundBankAccountsTest extends ServerSetup {
  
  test("getBasicBankAccounts extracts BasicBankAccounts" ) {
    val accounts = getBasicBankAccountsForUser("./src/test/resources/joni_result.json", false)
    accounts should be(List(
      BasicBankAccount("3565953", "616", "330", "?+1         81433020102612", AccountPermissions(true,false,false)),
      BasicBankAccount("50180983", "616", "430", "?+1         81433020102612", AccountPermissions(true,false,true)),
      BasicBankAccount("50180963", "616", "330", "?+1         81433020102612", AccountPermissions(true,false,false)),
      BasicBankAccount("20102612", "814", "330", "?+1         81433020102612", AccountPermissions(true,false,false)),
      BasicBankAccount("20105505", "814", "330", "?+1         81433020102612", AccountPermissions(true,false,false))
    ))
    }

   test("base64encodedSha256(string) is really sha256 hash of string") {
     val res1 = base64EncodedSha256("fred")
     val res2 = base64EncodedSha256("karl")
     res1 should be ("0M_C5TGbgs3HGjOHPoJsk9fuETY_iskcT6Oiz80ihuU")
     res2 should be ("wxppC1KLgaxxCoDWE3KF28ltJHLOBNMfREcXrHLTgYM")
  }

}
