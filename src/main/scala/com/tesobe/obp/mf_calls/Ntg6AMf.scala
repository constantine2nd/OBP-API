package com.tesobe.obp

import com.tesobe.obp.HttpClient.makePostRequest
import com.tesobe.obp.JoniMf.replaceEmptyObjects
import com.tesobe.obp.Ntg6A
import com.typesafe.scalalogging.StrictLogging
import net.liftweb.json.JValue
import net.liftweb.json.JsonParser.parse

object Ntg6AMf extends StrictLogging{
  
    def getNtg6A(
                 branch: String,
                 accountType: String,
                 accountNumber: String,
                 cbsToken: String,
                 counterpartyBranchNumber: String,
                 counterpartyAccountNumber: String,
                 counterpartyName: String,
                 counterpartyDescription: String,
                 counterpartyIBAN: String,
                 counterpartyNameInEnglish: String,
                 counterpartyDescriptionInEnglish: String
               ): Either[PAPIErrorResponse, Ntg6A] = {

      val path = "/ESBLeumiDigitalBank/PAPI/v1.0/NTG6/A/000/01.02"
      logger.debug("parsing json for getNtg6A")
      val json: JValue = parse(s"""
      {
        "NTG6_A_000": {
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
          "KMUT_IDKUNIN": {
          "KMUT_OLD": {
          "KMUT_ERETZ_MUTAV": "2121",
          "KMUT_BANK_MUTAV": "10",
          "KMUT_SNIF_MUTAV": "$counterpartyBranchNumber",
          "KMUT_SUG_CHEN_MUTAV": "0",
          "KMUT_CHEN_MUTAV": "$counterpartyAccountNumber",
          "KMUT_SHEM_MUTAV": "$counterpartyName",
          "KMUT_SUG_MUTAV": "0",
          "KMUT_TEUR_MUTAV": "$counterpartyDescription",
          "KMUT_IBAN": "$counterpartyIBAN",
          "KMUT_SHEM_MUTAV_ANGLIT": "$counterpartyNameInEnglish",
          "KMUT_TEUR_MUTAV_ANGLIT": "$counterpartyDescriptionInEnglish"
        },
          "KMUT_TOSEFET": {
          "KMUT_ZIHUI_MUTAV1": "0",
          "KMUT_ZIHUI_MUTAV2": "0",
          "KMUT_SHEM_MUTAV2": " ",
          "KMUT_SHEM_MUTAV2_E": " "
        }
        }
        }
      }""")

      val result = makePostRequest(json, path)

      logger.debug("Ntg6A ---extracting case class")

      implicit val formats = net.liftweb.json.DefaultFormats
      try {
        Right(parse(replaceEmptyObjects(result)).extract[Ntg6A])
      } catch {
        case _  => Left(parse(replaceEmptyObjects(result)).extract[PAPIErrorResponse])
      }  
    }
}
