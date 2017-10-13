package com.tesobe.obp

import com.tesobe.obp.HttpClient.makePostRequest
import com.tesobe.obp.JoniMf.replaceEmptyObjects
import com.tesobe.obp.NtbdIv050
import net.liftweb.json.JValue
import net.liftweb.json.JsonParser.parse

object NtbdIv050Mf {
  
    def getNtbdIv050(branch: String,
                     accountType: String,
                     accountNumber: String,
                     cbsToken: String,
                     ntbdAv050Token: String,
                     transactionAmount: String
                    ) = {

      val path = "/ESBLeumiDigitalBank/PAPI/v1.0/NTBD/I/050/01.03"

      val json: JValue = parse(s"""
      {
        "NTBD_I_050": {
          "NtdriveCommonHeader": {
          "KeyArguments": {
          "Branch": "$branch",
          "AccountType": "$accountType",
          "AccountNumber": "$accountNumber"
        },
          "AuthArguments": {
          "MFToken": "$cbsToken"
        }
        },
          "K050_SIYUMMUTAVIM": {
          "K050_TOKEN_S": "$ntbdAv050Token",
          "K050_SCUM_MIZTABER_S": "$transactionAmount",
          "K050_SHLAV_PEULA_S": "2",
          "K050_MISPAR_MUTAVIM_S": "1"
        }
        }
      }""")

      val result = makePostRequest(json, path)

      implicit val formats = net.liftweb.json.DefaultFormats
      parse(replaceEmptyObjects(result)).extract[NtbdIv050]
    }
}
