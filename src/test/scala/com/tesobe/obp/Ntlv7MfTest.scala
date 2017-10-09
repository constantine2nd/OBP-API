package com.tesobe.obp

import com.tesobe.obp.Ntlv7Mf.getNtlv7Mf

class Ntlv7MfTest extends ServerSetup{

  test("Ntlv7 gives proper OTP"){
    val result = getNtlv7Mf(
      branch = "616",
      accountType = "330",
      accountNumber = "50180963",
      username = "N7jut8d",
      cbsToken = ">M8          81433020102612",
      ntlv1TargetMobileNumberPrefix = "054",
      ntlv1TargetMobileNumber = "2501665"
    )

    result.DFHPLT_1.DFH_OPT should be ("183823")
 
  }
  
}
