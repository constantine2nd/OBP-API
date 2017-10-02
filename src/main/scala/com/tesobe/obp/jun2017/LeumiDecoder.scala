package com.tesobe.obp.june2017

import java.text.SimpleDateFormat
import java.util.UUID

import com.tesobe.obp._
import com.tesobe.obp.GetBankAccounts.getBasicBankAccountsForUser
import com.tesobe.obp.JoniMf.getJoni
import com.tesobe.obp.Nt1cBMf.getBalance
import com.tesobe.obp.Nt1cTMf.getCompletedTransactions
import com.tesobe.obp.Ntbd1v135Mf.getNtbd1v135MfHttpApache
import com.tesobe.obp.Ntbd2v135Mf.getNtbd2v135MfHttpApache
import com.tesobe.obp.Ntlv1Mf.getNtlv1MfHttpApache
import com.tesobe.obp.Ntlv7Mf.getNtlv7MfHttpApache
import com.tesobe.obp.GetBankAccounts.base64EncodedSha256
import com.tesobe.obp.JoniMf.getMFToken
import com.tesobe.obp.Util.TransactionRequestTypes
import com.typesafe.scalalogging.StrictLogging
import net.liftweb.json.JValue

import scala.collection.mutable.{ListBuffer, Map}


/**
  * Responsible for processing requests based on local example json files.
  *
  * Open Bank Project - Leumi Adapter
  * Copyright (C) 2016-2017, TESOBE Ltd.This program is free software: you can redistribute it and/or modify
  * it under the terms of the GNU Affero General Public License as published by
  * the Free Software Foundation, either version 3 of the License, or
  * (at your option) any later version.This program is distributed in the hope that it will be useful,
  * but WITHOUT ANY WARRANTY; without even the implied warranty of
  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  * GNU Affero General Public License for more details.You should have received a copy of the GNU Affero General Public License
  * along with this program.  If not, see <http://www.gnu.org/licenses/>.Email: contact@tesobe.com
  * TESOBE Ltd
  * Osloerstrasse 16/17
  * Berlin 13359, GermanyThis product includes software developed at TESOBE (http://www.tesobe.com/)
  * This software may also be distributed under a commercial license from TESOBE Ltd subject to separate terms.
  */
object LeumiDecoder extends Decoder with StrictLogging {
  
  val defaultCurrency = "ILS"
  val defaultFilterFormat: SimpleDateFormat = new SimpleDateFormat("E MMM dd HH:mm:ss Z yyyy")
  val simpleTransactionDateFormat = new SimpleDateFormat("yyyymmdd")
  val simpleDateFormat: SimpleDateFormat = new SimpleDateFormat("dd/mm/yyyy")
  val simpleDayFormat: SimpleDateFormat = new SimpleDateFormat("dd")
  val simpleMonthFormat: SimpleDateFormat = new SimpleDateFormat("mm")
  val simpleYearFormat: SimpleDateFormat = new SimpleDateFormat("yyyy")

  //TODO: Replace with caching solution for production
  case class AccountValues (branchId: String,accountType: String, accountNumber:String)
  var mapAccountIdToAccountValues = Map[String, AccountValues]()
  var mapAccountNumberToAccountId= Map[String, String]()
  case class TransactionIdValues(amount: String, completedDate: String, newBalanceAmount: String)
  var mapTransactionIdToTransactionValues = Map[String, TransactionIdValues]()
  var mapTransactionValuesToTransactionId = Map[TransactionIdValues, String]()
  
  //Helper functions start here:---------------------------------------------------------------------------------------

  def getOrCreateAccountId(branchId: String, accountType: String, accountNumber: String): String = {
    logger.debug(s"getOrCreateAccountId-accountNr($accountNumber)")
    if (mapAccountNumberToAccountId.contains(accountNumber)) { mapAccountNumberToAccountId(accountNumber) }
    else {
      //TODO: Do random salting for production? Will lead to expired accountIds becoming invalid.
      val accountId = base64EncodedSha256(accountNumber + "fjdsaFDSAefwfsalfid")
      mapAccountIdToAccountValues += (accountId -> AccountValues(branchId, accountType, accountNumber))
      mapAccountNumberToAccountId += (accountNumber -> accountId)
      accountId
    }
  }
  
  def getOrCreateTransactionId(amount: String, completedDate: String,newBalanceAmount: String): String = {
    logger.debug(s"getOrCreateTransactionId for ($amount)($completedDate)($newBalanceAmount)")
    val transactionIdValues = TransactionIdValues(amount,completedDate, newBalanceAmount)
    if (mapTransactionValuesToTransactionId.contains(transactionIdValues)) {
      mapTransactionValuesToTransactionId(transactionIdValues)
    } else {
      val transactionId = base64EncodedSha256(amount + completedDate + newBalanceAmount)
      mapTransactionValuesToTransactionId += (transactionIdValues -> transactionId)
      mapTransactionIdToTransactionValues += (transactionId -> transactionIdValues)
      transactionId
    }
  } 

  def mapAdapterAccountToInboundAccountJune2017(username: String, x: BasicBankAccount): InboundAccountJune2017 = {

    //TODO: This is by choice and needs verification
    //Create OwnerRights and accountViewer for result InboundAccount2017 creation
    val hasOwnerRights: Boolean = x.accountPermissions.canMakeExternalPayments || x.accountPermissions.canMakeInternalPayments
    val hasViewerRights: Boolean = x.accountPermissions.canSee
    val  viewsToGenerate  = {
      if (hasOwnerRights) {List("Owner")}
      else if (hasViewerRights) {List("Auditor")}
      else {List("")}
    }
    //Create Owner for result InboundAccount2017 creation
    val accountOwner = if (hasOwnerRights) {List(username)} else {List("")}
    InboundAccountJune2017(
      errorCode = "",
      List(InboundStatusMessage("ESB","Success", "0", "OK")), ////TODO, need to fill the coreBanking error
      x.cbsToken,
      bankId = "10",
      branchId = x.branchNr,
      accountId = getOrCreateAccountId(x.branchNr, x.accountType, x.accountNr),
      accountNumber = x.accountNr,
      accountType = x.accountType,
      balanceAmount = getBalance(username, x.branchNr, x.accountType, x.accountNr, x.cbsToken),
      balanceCurrency = defaultCurrency,
      owners = accountOwner,
      viewsToGenerate = viewsToGenerate,
      bankRoutingScheme = "", 
      bankRoutingAddress = "",
      branchRoutingScheme = "",
      branchRoutingAddress = "",
      accountRoutingScheme = "",
      accountRoutingAddress = "")
  }
  def mapAdapterTransactionToInternalTransaction(userId: String, 
                                                 bankId: String,
                                                 accountId: String,
                                                 adapterTransaction: Tn2TnuaBodedet): InternalTransaction = {
    val amount = adapterTransaction.TN2_TNUA_BODEDET.TN2_SCHUM
    val completedDate = adapterTransaction.TN2_TNUA_BODEDET.TN2_TA_ERECH
    val newBalanceAmount = adapterTransaction.TN2_TNUA_BODEDET.TN2_ITRA
    InternalTransaction(
      //Base : "TN2_TSHUVA_TAVLAIT":"TN2_SHETACH_LE_SEND_NOSAF":"TN2_TNUOT":"TN2_PIRTEY_TNUA":["TN2_TNUA_BODEDET"                              
      errorCode = "",
      List(
        InboundStatusMessage("ESB","Success", "0", "OK"), //TODO, need to fill the coreBanking error
        InboundStatusMessage("MF","Success", "0", "OK")   //TODO, need to fill the coreBanking error
      ),
      transactionId = getOrCreateTransactionId(amount,completedDate,newBalanceAmount), // Find some
      accountId = accountId, //accountId
      amount = amount, //:"TN2_SCHUM"
      bankId = "10", // 10 for now (Joni)
      completedDate = completedDate, //"TN2_TA_ERECH": // Date of value for
      counterpartyId = "counterpartyId",
      counterpartyName = "counterpartyName",
      currency = defaultCurrency, //ILS 
      description = adapterTransaction.TN2_TNUA_BODEDET.TN2_TEUR_PEULA, //"TN2_TEUR_PEULA":
      newBalanceAmount = newBalanceAmount,  //"TN2_ITRA":
      newBalanceCurrency = defaultCurrency, //ILS
      postedDate = adapterTransaction.TN2_TNUA_BODEDET.TN2_TA_IBUD, //"TN2_TA_IBUD": // Date of transaction
      `type` = adapterTransaction.TN2_TNUA_BODEDET.TN2_SUG_PEULA, //"TN2_SUG_PEULA"
      userId = userId //userId
    )
  }
  //Helper functions end here--------------------------------------------------------------------------------------------
  
  //Processor functions start here---------------------------------------------------------------------------------------

  override def getBanks(getBanks: GetBanks) = {
      Banks(getBanks.authInfo, List(InboundBank(
        "",
        List(InboundStatusMessage("ESB","Success", "0", "OK")),
         "10", "leumi","leumilogo","leumiurl")))
    }

  override def getBank(getBank: GetBank) = {
    BankWrapper(getBank.authInfo, InboundBank(
      "",
      List(InboundStatusMessage("ESB","Success", "0", "OK")), 
       "10", "leumi","leumilogo","leumiurl"))
  }

  
  def getBankAccountbyAccountId(getAccount: GetAccountbyAccountID): InboundBankAccount = {
    //Not cached or invalid AccountId
    if (!mapAccountIdToAccountValues.contains(getAccount.accountId)) {
      logger.debug("not mapped")
      getBankAccounts(OutboundGetAccounts(getAccount.authInfo,null)) //TODO need add the data here.
    }
    val accountNr = mapAccountIdToAccountValues(getAccount.accountId).accountNumber
    val mfAccounts = getBasicBankAccountsForUser(getAccount.authInfo.username)
    InboundBankAccount(AuthInfo(getAccount.authInfo.userId,
      getAccount.authInfo.username,
      mfAccounts.head.cbsToken),
      mapAdapterAccountToInboundAccountJune2017(getAccount.authInfo.username,mfAccounts.filter(x => x.accountNr == accountNr ).head)
    )
  }
  
  def getBankAccountByAccountNumber(getAccount: GetAccountbyAccountNumber): InboundBankAccount = {
    val mfAccounts = getBasicBankAccountsForUser(getAccount.authInfo.username)
    InboundBankAccount(AuthInfo(getAccount.authInfo.userId,
      getAccount.authInfo.username,
      mfAccounts.head.cbsToken),
      //TODO: Error handling
      mapAdapterAccountToInboundAccountJune2017(getAccount.authInfo.username,mfAccounts.filter(x => 
      x.accountNr == getAccount.accountNumber).head))
  }

   def getBankAccounts(getAccountsInput: OutboundGetAccounts): InboundBankAccounts = {
    val mfAccounts = getBasicBankAccountsForUser(getAccountsInput.authInfo.username)
    var result = new ListBuffer[InboundAccountJune2017]()
    for (i <- mfAccounts) {
      
      result += mapAdapterAccountToInboundAccountJune2017(getAccountsInput.authInfo.username, i)
      }
    InboundBankAccounts(AuthInfo(getAccountsInput.authInfo.userId,
      //TODO: Error handling
      getAccountsInput.authInfo.username,
      mfAccounts.head.cbsToken), result.toList)
  }
  
  def getTransactions(getTransactionsRequest: GetTransactions): InboundTransactions = {
    //TODO: Error handling
    val accountValues = mapAccountIdToAccountValues(getTransactionsRequest.accountId)
    val fromDay = simpleDayFormat.format(defaultFilterFormat.parse(getTransactionsRequest.fromDate))
    val fromMonth = simpleMonthFormat.format(defaultFilterFormat.parse(getTransactionsRequest.fromDate))
    val fromYear = simpleYearFormat.format(defaultFilterFormat.parse(getTransactionsRequest.fromDate))
    val toDay = simpleDayFormat.format(defaultFilterFormat.parse(getTransactionsRequest.toDate))
    val toMonth = simpleMonthFormat.format(defaultFilterFormat.parse(getTransactionsRequest.toDate))
    val toYear = simpleYearFormat.format(defaultFilterFormat.parse(getTransactionsRequest.toDate))

    val mfTransactions = getCompletedTransactions(
      getTransactionsRequest.authInfo.username,
      accountValues.branchId,
      accountValues.accountType,
      accountValues.accountNumber,
      getTransactionsRequest.authInfo.cbsToken, List(fromYear,fromMonth,fromDay), List(toYear,toMonth,toDay), getTransactionsRequest.limit.toString)
    var result = new ListBuffer[InternalTransaction]
    for (i <- mfTransactions.TN2_TSHUVA_TAVLAIT.TN2_SHETACH_LE_SEND_NOSAF.TN2_TNUOT.TN2_PIRTEY_TNUA) {
      result += mapAdapterTransactionToInternalTransaction(
        getTransactionsRequest.authInfo.userId,
        "10",
        getTransactionsRequest.accountId,
        i
      )
    }
      InboundTransactions(getTransactionsRequest.authInfo, result.toList)
  }
  
  def getTransaction(getTransactionRequest: GetTransaction): InboundTransaction = {
    logger.debug(s"get Transaction for ($getTransactionRequest)")
    val allTransactions: List[InternalTransaction] = {
      if (mapTransactionIdToTransactionValues.contains(getTransactionRequest.transactionId) && 
          mapAccountIdToAccountValues.contains(getTransactionRequest.accountId)) {
        val transactionDate: String = mapTransactionIdToTransactionValues(getTransactionRequest.transactionId).completedDate
        val simpleTransactionDate = defaultFilterFormat.format(simpleTransactionDateFormat.parse(transactionDate))
        getTransactions(GetTransactions(getTransactionRequest.authInfo,
          getTransactionRequest.bankId,
          getTransactionRequest.accountId,
          50,
          simpleTransactionDate, simpleTransactionDate
        )).data
      } else if (mapTransactionIdToTransactionValues.contains(getTransactionRequest.transactionId) &&
        !mapAccountIdToAccountValues.contains(getTransactionRequest.accountId)){
        getBankAccounts(OutboundGetAccounts(getTransactionRequest.authInfo, null))  //TODO , need fix
        val transactionDate: String = mapTransactionIdToTransactionValues(getTransactionRequest.transactionId).completedDate
        val simpleTransactionDate = defaultFilterFormat.format(simpleTransactionDateFormat.parse(transactionDate))
        getTransactions(GetTransactions(getTransactionRequest.authInfo,
          getTransactionRequest.bankId,
          getTransactionRequest.accountId,
          50,
          simpleTransactionDate, simpleTransactionDate
        )).data
      } else if (!mapTransactionIdToTransactionValues.contains(getTransactionRequest.transactionId) &&
        mapAccountIdToAccountValues.contains(getTransactionRequest.accountId)){
        getTransactions(GetTransactions(getTransactionRequest.authInfo,
          getTransactionRequest.bankId,
          getTransactionRequest.accountId,
          50,
          "Sat Jul 01 00:00:00 CEST 2000", "Sat Jul 01 00:00:00 CEST 2000"
        )).data
      } else {
        getBankAccounts(OutboundGetAccounts(getTransactionRequest.authInfo, null))
        getTransactions(GetTransactions(getTransactionRequest.authInfo,
          getTransactionRequest.bankId,
          getTransactionRequest.accountId,
          50,
          "Sat Jul 01 00:00:00 CEST 2000", "Sat Jul 01 00:00:00 CEST 2000" 
        )).data
      }
    }
    
    //TODO: Error handling
    val resultTransaction = allTransactions.filter(x => x.transactionId == getTransactionRequest.transactionId).head
    InboundTransaction(getTransactionRequest.authInfo, resultTransaction)
    
  }
  
  def createTransaction(createTransactionRequest: CreateTransaction): InboundCreateTransactionId = {
    logger.debug(s"LeumiDecoder-createTransaction input: ($createTransactionRequest)")
    // As to this page: https://github.com/OpenBankProject/OBP-Adapter_Leumi/wiki/NTBD_1_135#-these-parameters-have-to-come-from-the-api
    // OBP-API will provide: four values:
    val accountValues = mapAccountIdToAccountValues(createTransactionRequest.fromAccountId)
    val branchId = accountValues.branchId
    val accountNumber = accountValues.accountNumber
    val accountType = accountValues.accountType
    val username =  createTransactionRequest.authInfo.username
    val cbsToken = createTransactionRequest.authInfo.cbsToken

 
    val transactionNewId = ""  //as we cannot determine the transactionid at creation, this will always be empty
    if (createTransactionRequest.transactionRequestType == (TransactionRequestTypes.TRANSFER_TO_PHONE.toString)) {
      val transactionRequestBodyPhoneToPhoneJson = createTransactionRequest.transactionRequestCommonBody.asInstanceOf[TransactionRequestBodyTransferToPhoneJson]
      val senderPhoneNumber = transactionRequestBodyPhoneToPhoneJson.from_account_phone_number
      val receiverPhoneNumber = transactionRequestBodyPhoneToPhoneJson.couterparty.other_account_phone_number
      val transactionDescription = transactionRequestBodyPhoneToPhoneJson.description
      val transactionAmount = transactionRequestBodyPhoneToPhoneJson.value.amount

      
      val callNtbd1_135 = getNtbd1v135MfHttpApache(branch = branchId,
        accountType,
        accountNumber,
        username,
        cbsToken,
        mobileNumberOfMoneySender = senderPhoneNumber,
        mobileNumberOfMoneyReceiver = receiverPhoneNumber,
        description = transactionDescription,
        transferAmount = transactionAmount)
      
      val callNtbd2_135 = getNtbd2v135MfHttpApache(branchId,
        accountType,
        accountNumber,
        username,
        cbsToken,
        ntbd1v135_Token = callNtbd1_135.P135_BDIKAOUT.P135_TOKEN,
        nicknameOfMoneySender = transactionRequestBodyPhoneToPhoneJson.from_account_owner_nickname,
        //TODO: Check with api if the description is intended to be the message to the money receiver
        messageToMoneyReceiver =  transactionDescription)


      

      }else if (createTransactionRequest.transactionRequestType == (TransactionRequestTypes.TRANSFER_TO_ATM.toString)) {
      val transactionRequestBodyPhoneToPhoneJson = createTransactionRequest.transactionRequestCommonBody.asInstanceOf[TransactionRequestBodyTransferToAtmJson]
  
    } else if (createTransactionRequest.transactionRequestType == (TransactionRequestTypes.TRANSFER_TO_ACCOUNT.toString)) {
      val transactionRequestBodyPhoneToPhoneJson = createTransactionRequest.transactionRequestCommonBody.asInstanceOf[TransactionRequestBodyTransferToAccount]
  
    } else if (createTransactionRequest.transactionRequestType == (TransactionRequestTypes.COUNTERPARTY.toString)) {
      val transactionRequestBodyPhoneToPhoneJson = createTransactionRequest.transactionRequestCommonBody.asInstanceOf[TransactionRequestBodyCounterpartyJSON]
  
    } else if (createTransactionRequest.transactionRequestType == (TransactionRequestTypes.SEPA.toString)) {
      val transactionRequestBodyPhoneToPhoneJson = createTransactionRequest.transactionRequestCommonBody.asInstanceOf[TransactionRequestBodySEPAJSON]
  
    } else
      throw new RuntimeException("Do not support this transaction type, please check it in OBP-API side")
  
    InboundCreateTransactionId(createTransactionRequest.authInfo, 
      InternalTransactionId("",List(InboundStatusMessage("ESB","Success", "0", "OK")),
        transactionNewId))
    
  }
  
  def getToken(getTokenRequest: GetToken): InboundToken = {
    InboundToken(getTokenRequest.username, getMFToken(getTokenRequest.username))
  }
  
  def createChallenge(createChallenge: OutboundCreateChallengeJune2017): InboundCreateChallengeJune2017 = {
    logger.debug(s"LeumiDecoder-createTransaction input: ($createChallenge)")

    implicit val formats = net.liftweb.json.DefaultFormats
    //Creating JSON AST
    val jsonAst: JValue = getJoni(createChallenge.authInfo.username)
    //Create case class object JoniMfUser
    val jsonExtract: JoniMfUser = jsonAst.extract[JoniMfUser]
    val accountValues = mapAccountIdToAccountValues(createChallenge.accountId)
    val branchId = accountValues.branchId
    val accountNumber = accountValues.accountNumber
    val accountType = accountValues.accountType
    val username = createChallenge.authInfo.username 
    val cbsToken = jsonExtract.SDR_JONI.MFTOKEN
    //todo: never used, plz check.
    val phoneNumber = createChallenge.phoneNumber 
    val callNtlv1 = getNtlv1MfHttpApache(username,
      jsonExtract.SDR_JONI.SDR_MANUI.SDRM_ZEHUT,
      jsonExtract.SDR_JONI.SDR_MANUI.SDRM_SUG_ZIHUY,
      cbsToken
    )
    //TODO: will use the first mobile phone contact available. Check.
    val mobilePhoneData  = callNtlv1.O1OUT1AREA_1.O1_CONTACT_REC.find(x => x.O1_TEL_USE_TYPE_CODE == "10" ).getOrElse(
      O1contactRec(O1recId("",""),"","","","","","","","","","","","","","","",""))


    val callNtlv7 = getNtlv7MfHttpApache(branchId,
      accountType,
      accountNumber,
      username,
      cbsToken,
      mobilePhoneData.O1_TEL_AREA,
      mobilePhoneData.O1_TEL_NUM
    )
    
    val answer = callNtlv7.DFHPLT_1.DFH_OPT
    InboundCreateChallengeJune2017(createChallenge.authInfo, InternalCreateChallengeJune2017(
      "",
      List(
        //Todo: We did 3 MfCalls so far. Shall they all go in?
        InboundStatusMessage("ESB","Success", "0", "OK"), //TODO, need to fill the coreBanking error
        InboundStatusMessage("MF","Success", "0", "OK")   //TODO, need to fill the coreBanking error
      ),
        answer))
  }
  
  
}



