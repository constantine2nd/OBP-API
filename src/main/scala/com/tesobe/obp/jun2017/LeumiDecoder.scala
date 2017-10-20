package com.tesobe.obp.june2017

import java.text.SimpleDateFormat
import java.util.Date

import com.tesobe.obp.ErrorMessages.NoCreditCard
import com.tesobe.obp.GetBankAccounts.{base64EncodedSha256, getBasicBankAccountsForUser}
import com.tesobe.obp.JoniMf.{getJoni, getMFToken}
import com.tesobe.obp.Nt1cBMf.getBalance
import com.tesobe.obp.Nt1cBMf.getNt1cBMfHttpApache
import com.tesobe.obp.Nt1cTMf.getCompletedTransactions
import com.tesobe.obp.Nt1c3Mf.getNt1c3
import com.tesobe.obp.Nt1c4Mf.getNt1c4
import com.tesobe.obp.Ntbd1v105Mf.getNtbd1v105Mf
import com.tesobe.obp.Ntbd1v135Mf.getNtbd1v135Mf
import com.tesobe.obp.Ntbd2v050Mf.getNtbd2v050
import com.tesobe.obp.Ntbd2v105Mf.getNtbd2v105Mf
import com.tesobe.obp.Ntbd2v135Mf.getNtbd2v135Mf
import com.tesobe.obp.NtbdAv050Mf.getNtbdAv050
import com.tesobe.obp.NtbdBv050Mf.getNtbdBv050
import com.tesobe.obp.NtbdGv050Mf.getNtbdGv050
import com.tesobe.obp.NtbdIv050Mf.getNtbdIv050
import com.tesobe.obp.Ntg6AMf.getNtg6A
import com.tesobe.obp.Ntg6BMf.getNtg6B
import com.tesobe.obp.Ntlv1Mf.getNtlv1Mf
import com.tesobe.obp.Ntlv7Mf.getNtlv7Mf
import com.tesobe.obp.NttfWMf.getNttfWMf
import com.tesobe.obp.Util.TransactionRequestTypes
import com.tesobe.obp._
import com.tesobe.obp.june2017.AccountRoutingJsonV121
import com.typesafe.scalalogging.StrictLogging
import net.liftweb.json.JValue

import scala.collection.immutable.List
import scala.collection.mutable
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

  implicit val formats = net.liftweb.json.DefaultFormats
  

  val defaultCurrency = "ILS"
  val defaultFilterFormat: SimpleDateFormat = new SimpleDateFormat("E MMM dd HH:mm:ss Z yyyy")
  val simpleTransactionDateFormat = new SimpleDateFormat("yyyyMMdd")
  val simpleDateFormat: SimpleDateFormat = new SimpleDateFormat("dd/MM/yyyy")
  val simpleDayFormat: SimpleDateFormat = new SimpleDateFormat("dd")
  val simpleMonthFormat: SimpleDateFormat = new SimpleDateFormat("MM")
  val simpleYearFormat: SimpleDateFormat = new SimpleDateFormat("yyyy")
  val simpleLastLoginFormat: SimpleDateFormat = new SimpleDateFormat("yyyyMMddHHmmss")

  //TODO: Replace with caching solution for production
  case class AccountIdValues(branchId: String, accountType: String, accountNumber: String)

  var mapAccountIdToAccountValues = Map[String, AccountIdValues]()
  var mapAccountValuesToAccountId = Map[AccountIdValues, String]()

  case class TransactionIdValues(amount: String, completedDate: String, newBalanceAmount: String)

  var mapTransactionIdToTransactionValues = Map[String, TransactionIdValues]()
  var mapTransactionValuesToTransactionId = Map[TransactionIdValues, String]()
  
  var mapCustomerIdToBankUserId = Map[String,String]()
  var mapBankUserIdToCustomerId = Map[String,String]()
  

  //Helper functions start here:---------------------------------------------------------------------------------------

  def getOrCreateAccountId(branchId: String, accountType: String, accountNumber: String): String = {
    logger.debug(s"getOrCreateAccountId-accountNr($accountNumber)")
    val accountIdValues = AccountIdValues(branchId, accountType, accountNumber)
    if (mapAccountValuesToAccountId.contains(accountIdValues)) {
      mapAccountValuesToAccountId(accountIdValues)
    }
    else {
      //TODO: Do random salting for production? Will lead to expired accountIds becoming invalid.
      val accountId = base64EncodedSha256(branchId + accountType + accountNumber + config.getString("salt.global"))
      mapAccountIdToAccountValues += (accountId -> accountIdValues)
      mapAccountValuesToAccountId += (accountIdValues -> accountId)
      accountId
    }
  }

  def getOrCreateTransactionId(amount: String, completedDate: String, newBalanceAmount: String): String = {
    logger.debug(s"getOrCreateTransactionId for ($amount)($completedDate)($newBalanceAmount)")
    val transactionIdValues = TransactionIdValues(amount, completedDate, newBalanceAmount)
    if (mapTransactionValuesToTransactionId.contains(transactionIdValues)) {
      mapTransactionValuesToTransactionId(transactionIdValues)
    } else {
      val transactionId = base64EncodedSha256(amount + completedDate + newBalanceAmount)
      mapTransactionValuesToTransactionId += (transactionIdValues -> transactionId)
      mapTransactionIdToTransactionValues += (transactionId -> transactionIdValues)
      transactionId
    }
  }
  
  def getOrCreateCustomerId(username: String): String = {
    logger.debug(s"getOrCreateTransactionId for ($username)")
    if (mapBankUserIdToCustomerId.contains(username)) mapBankUserIdToCustomerId(username) else {
      val customerId = base64EncodedSha256(username + config.getString("salt.global"))
      mapBankUserIdToCustomerId += (username -> customerId)
      mapCustomerIdToBankUserId += (customerId -> username)
      customerId
    }
  }

  def mapBasicBankAccountToInboundAccountJune2017WithoutBalance(username: String, x: BasicBankAccount): InboundAccountJune2017 = {

    //TODO: This is by choice and needs verification
    //Create OwnerRights and accountViewer for result InboundAccount2017 creation
    val hasOwnerRights: Boolean = x.accountPermissions.canMakeExternalPayments || x.accountPermissions.canMakeInternalPayments
    val hasViewerRights: Boolean = x.accountPermissions.canSee
    val viewsToGenerate = {
      if (hasOwnerRights) {
        List("Owner")
      }
      else if (hasViewerRights) {
        List("Auditor")
      }
      else {
        List("")
      }
    }
    //Create Owner for result InboundAccount2017 creation
    val accountOwner = if (hasOwnerRights) {
      List(username)
    } else {
      List("")
    }
    InboundAccountJune2017(
      errorCode = "",
      List(InboundStatusMessage("ESB", "Success", "0", "OK")), ////TODO, need to fill the coreBanking error
      x.cbsToken,
      bankId = "10",
      branchId = x.branchNr,
      accountId = getOrCreateAccountId(x.branchNr, x.accountType, x.accountNr),
      accountNumber = x.accountNr,
      accountType = x.accountType,
      //balanceAmount = getBalance(username, x.branchNr, x.accountType, x.accountNr, x.cbsToken),
      balanceAmount = "",
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

  def mapBasicBankAccountToInboundAccountJune2017WithBalance(username: String, x: BasicBankAccount): InboundAccountJune2017 = {

    //TODO: This is by choice and needs verification
    //Create OwnerRights and accountViewer for result InboundAccount2017 creation
    val hasOwnerRights: Boolean = x.accountPermissions.canMakeExternalPayments || x.accountPermissions.canMakeInternalPayments
    val hasViewerRights: Boolean = x.accountPermissions.canSee
    val viewsToGenerate = {
      if (hasOwnerRights) {
        List("Owner")
      }
      else if (hasViewerRights) {
        List("Auditor")
      }
      else {
        List("")
      }
    }
    //Create Owner for result InboundAccount2017 creation
    val accountOwner = if (hasOwnerRights) {
      List(username)
    } else {
      List("")
    }
    InboundAccountJune2017(
      errorCode = "",
      List(InboundStatusMessage("ESB", "Success", "0", "OK")), ////TODO, need to fill the coreBanking error
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

    // We can only get these six parameters from CBS. 
    val amount = adapterTransaction.TN2_TNUA_BODEDET.TN2_SCHUM //:"TN2_SCHUM"
    val completedDate = adapterTransaction.TN2_TNUA_BODEDET.TN2_TA_ERECH //"TN2_TA_ERECH": // Date of value for
    val newBalanceAmount = adapterTransaction.TN2_TNUA_BODEDET.TN2_ITRA //"TN2_ITRA":
    val description = adapterTransaction.TN2_TNUA_BODEDET.TN2_TEUR_PEULA //"TN2_TEUR_PEULA":
    val transactionType = adapterTransaction.TN2_TNUA_BODEDET.TN2_SUG_PEULA //"TN2_SUG_PEULA"
    val transactionProcessingDate = adapterTransaction.TN2_TNUA_BODEDET.TN2_TA_IBUD //"TN2_TA_IBUD": // Date of transaction

    InternalTransaction(
      errorCode = "",
      List(
        InboundStatusMessage("ESB", "Success", "0", "OK"), //TODO, need to fill the coreBanking error
        InboundStatusMessage("MF", "Success", "0", "OK") //TODO, need to fill the coreBanking error
      ),
      transactionId = getOrCreateTransactionId(amount, completedDate, newBalanceAmount), // Find some
      accountId = accountId, //accountId
      amount = amount,
      bankId = "10", // 10 for now (Joni)
      completedDate = completedDate,
      counterpartyId = "counterpartyId", //TODO, can not get this field from CBS
      counterpartyName = "counterpartyName", //TODO, can not get this field from CBS
      currency = defaultCurrency, //ILS 
      description = description,
      newBalanceAmount = newBalanceAmount,
      newBalanceCurrency = defaultCurrency, //ILS
      postedDate = transactionProcessingDate,
      `type` = transactionType,
      userId = userId //userId
    )
  }
  
  
  def mapBasicBankAccountToCoreAccountJsonV300(account: BasicBankAccount): CoreAccountJsonV300 = {
    CoreAccountJsonV300(
      id = getOrCreateAccountId(account.branchNr,account.accountType,account.accountNr),
      label = "",
      bank_id = "10",
      account_routing = AccountRoutingJsonV121(scheme = "account_number", address = account.accountNr))
  } 

  //Helper functions end here--------------------------------------------------------------------------------------------

  //Processor functions start here---------------------------------------------------------------------------------------

  override def getBanks(getBanks: OutboundGetBanks) = {
    InboundGetBanks(getBanks.authInfo, List(InboundBank(
      "",
      List(InboundStatusMessage("ESB", "Success", "0", "OK")),
      "10", "leumi", "leumilogo", "leumiurl")))
  }

  override def getBank(getBank: OutboundGetBank) = {
    InboundGetBank(getBank.authInfo, InboundBank(
      "",
      List(InboundStatusMessage("ESB", "Success", "0", "OK")),
      "10", "leumi", "leumilogo", "leumiurl"))
  }


  def getBankAccountbyAccountId(getAccount: OutboundGetAccountbyAccountID): InboundGetAccountbyAccountID = {
    //Not cached or invalid AccountId
    if (!mapAccountIdToAccountValues.contains(getAccount.accountId)) {
      logger.debug("AccountId not mapped. This should not happen in normal business flow")
    }
    val accountNr = mapAccountIdToAccountValues(getAccount.accountId).accountNumber
    val mfAccounts = getBasicBankAccountsForUser(getAccount.authInfo.username)
    InboundGetAccountbyAccountID(AuthInfo(getAccount.authInfo.userId,
      getAccount.authInfo.username,
      mfAccounts.head.cbsToken),
      mapBasicBankAccountToInboundAccountJune2017WithBalance(getAccount.authInfo.username, mfAccounts.filter(x => x.accountNr == accountNr).head)
    )
  }

  def getBankAccountByAccountNumber(getAccount: OutboundGetAccountbyAccountNumber): InboundGetAccountbyAccountID = {
    val mfAccounts = getBasicBankAccountsForUser(getAccount.authInfo.username)
    InboundGetAccountbyAccountID(AuthInfo(getAccount.authInfo.userId,
      getAccount.authInfo.username,
      mfAccounts.head.cbsToken),
      //TODO: Error handling
      mapBasicBankAccountToInboundAccountJune2017WithBalance(getAccount.authInfo.username, mfAccounts.filter(x =>
        x.accountNr == getAccount.accountNumber).head))
  }

  def getBankAccounts(getAccountsInput: OutboundGetAccounts): InboundGetAccounts = {
    val mfAccounts = getBasicBankAccountsForUser(getAccountsInput.authInfo.username)
    var result = new ListBuffer[InboundAccountJune2017]()
    for (i <- mfAccounts) {

      result += mapBasicBankAccountToInboundAccountJune2017WithoutBalance(getAccountsInput.authInfo.username, i)
    }
    InboundGetAccounts(AuthInfo(getAccountsInput.authInfo.userId,
      //TODO: Error handling
      getAccountsInput.authInfo.username,
      mfAccounts.head.cbsToken), result.toList)
  }
  
  def getCoreAccounts(getCoreBankAccounts: OutboundGetCoreAccounts): InboundGetCoreAccounts = {
    val mfAccounts = getBasicBankAccountsForUser(getCoreBankAccounts.authInfo.username)
    var result = new ListBuffer[CoreAccountJsonV300]
    for (i <- mfAccounts)  {
      result += mapBasicBankAccountToCoreAccountJsonV300(i)
    }
    InboundGetCoreAccounts(
      getCoreBankAccounts.authInfo,
      List(InboundStatusMessage("","","","")),
      result.toList)
  }
  
  def getCoreBankAccounts(getCoreBankAccounts: OutboundGetCoreBankAccounts): InboundGetCoreBankAccounts = {
    val accountIdList = mapAccountIdToAccountValues.keySet.toList
    val result = new ListBuffer[InternalInboundCoreAccount]
    for (i <- accountIdList) {
      result += InternalInboundCoreAccount(
        errorCode = "",
        backendMessages = List(
          InboundStatusMessage("ESB", "Success", "0", "OK"),
          InboundStatusMessage("MF", "Success", "0", "OK")), 
        id = i,
        label = "",
        bank_id = "10",
        account_routing = AccountRoutingJsonV121(scheme = "account_number", address = mapAccountIdToAccountValues(i).accountNumber)
      )
    }
    InboundGetCoreBankAccounts(getCoreBankAccounts.authInfo, result.toList)
  }

  def getTransactions(getTransactionsRequest: OutboundGetTransactions): InboundGetTransactions = {
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
      getTransactionsRequest.authInfo.cbsToken, List(fromYear, fromMonth, fromDay), List(toYear, toMonth, toDay), getTransactionsRequest.limit.toString)
    var result = new ListBuffer[InternalTransaction]
    for (i <- mfTransactions.TN2_TSHUVA_TAVLAIT.TN2_SHETACH_LE_SEND_NOSAF.TN2_TNUOT.TN2_PIRTEY_TNUA) {
      result += mapAdapterTransactionToInternalTransaction(
        getTransactionsRequest.authInfo.userId,
        "10",
        getTransactionsRequest.accountId,
        i
      )
    }
    InboundGetTransactions(getTransactionsRequest.authInfo, result.toList)
  }

  def getTransaction(getTransactionRequest: OutboundGetTransaction): InboundGetTransaction = {
    logger.debug(s"get Transaction for ($getTransactionRequest)")
    val allTransactions: List[InternalTransaction] = {
      if (mapTransactionIdToTransactionValues.contains(getTransactionRequest.transactionId) &&
        mapAccountIdToAccountValues.contains(getTransactionRequest.accountId)) {
        val transactionDate: String = mapTransactionIdToTransactionValues(getTransactionRequest.transactionId).completedDate
        val simpleTransactionDate = defaultFilterFormat.format(simpleTransactionDateFormat.parse(transactionDate))
        getTransactions(OutboundGetTransactions(getTransactionRequest.authInfo,
          getTransactionRequest.bankId,
          getTransactionRequest.accountId,
          50,
          simpleTransactionDate, simpleTransactionDate
        )).data
      } else if (mapTransactionIdToTransactionValues.contains(getTransactionRequest.transactionId) &&
        !mapAccountIdToAccountValues.contains(getTransactionRequest.accountId)) {
        getBankAccounts(OutboundGetAccounts(getTransactionRequest.authInfo, null)) //TODO , need fix
        val transactionDate: String = mapTransactionIdToTransactionValues(getTransactionRequest.transactionId).completedDate
        val simpleTransactionDate = defaultFilterFormat.format(simpleTransactionDateFormat.parse(transactionDate))
        getTransactions(OutboundGetTransactions(getTransactionRequest.authInfo,
          getTransactionRequest.bankId,
          getTransactionRequest.accountId,
          50,
          simpleTransactionDate, simpleTransactionDate
        )).data
      } else if (!mapTransactionIdToTransactionValues.contains(getTransactionRequest.transactionId) &&
        mapAccountIdToAccountValues.contains(getTransactionRequest.accountId)) {
        getTransactions(OutboundGetTransactions(getTransactionRequest.authInfo,
          getTransactionRequest.bankId,
          getTransactionRequest.accountId,
          50,
          "Sat Jul 01 00:00:00 CEST 2000", "Sat Jul 01 00:00:00 CEST 2000"
        )).data
      } else {
        getBankAccounts(OutboundGetAccounts(getTransactionRequest.authInfo, null))
        getTransactions(OutboundGetTransactions(getTransactionRequest.authInfo,
          getTransactionRequest.bankId,
          getTransactionRequest.accountId,
          50,
          "Sat Jul 01 00:00:00 CEST 2000", "Sat Jul 01 00:00:00 CEST 2000"
        )).data
      }
    }

    //TODO: Error handling
    val resultTransaction = allTransactions.filter(x => x.transactionId == getTransactionRequest.transactionId).head
    InboundGetTransaction(getTransactionRequest.authInfo, resultTransaction)

  }

  def createTransaction(createTransactionRequest: OutboundCreateTransaction): InboundCreateTransactionId = {
    logger.debug(s"LeumiDecoder-createTransaction input: ($createTransactionRequest)")
    // As to this page: https://github.com/OpenBankProject/OBP-Adapter_Leumi/wiki/NTBD_1_135#-these-parameters-have-to-come-from-the-api
    // OBP-API will provide: four values:
    val accountValues = mapAccountIdToAccountValues(createTransactionRequest.fromAccountId)
    val branchId = accountValues.branchId
    val accountNumber = accountValues.accountNumber
    val accountType = accountValues.accountType
    val username = createTransactionRequest.authInfo.username
    val cbsToken = createTransactionRequest.authInfo.cbsToken
    val transactionNewId = "" //as we cannot determine the transactionid at creation, this will always be empty
    
    if (createTransactionRequest.transactionRequestType == (TransactionRequestTypes.TRANSFER_TO_PHONE.toString)) {
      val transactionRequestBodyPhoneToPhoneJson = createTransactionRequest.transactionRequestCommonBody.asInstanceOf[TransactionRequestBodyTransferToPhoneJson]
      val senderPhoneNumber = transactionRequestBodyPhoneToPhoneJson.from.mobile_phone_number
      val receiverPhoneNumber = transactionRequestBodyPhoneToPhoneJson.to.mobile_phone_number
      val transactionDescription = transactionRequestBodyPhoneToPhoneJson.description
      val transactionMessage = transactionRequestBodyPhoneToPhoneJson.message
      val transactionAmount = transactionRequestBodyPhoneToPhoneJson.value.amount

      val callNtbd1_135 = getNtbd1v135Mf(branch = branchId,
        accountType,
        accountNumber,
        username,
        cbsToken,
        mobileNumberOfMoneySender = senderPhoneNumber,
        mobileNumberOfMoneyReceiver = receiverPhoneNumber,
        description = transactionDescription,
        transferAmount = transactionAmount) 
      match {
        case Right(x) =>
          val callNtbd2_135 = getNtbd2v135Mf(branchId,
            accountType,
            accountNumber,
            username,
            cbsToken,
            ntbd1v135_Token = x.P135_BDIKAOUT.P135_TOKEN,
            nicknameOfMoneySender = transactionRequestBodyPhoneToPhoneJson.from.nickname,
            messageToMoneyReceiver = transactionMessage)
          match {
            case Right(y) =>
              InboundCreateTransactionId(createTransactionRequest.authInfo,
                InternalTransactionId("", List(InboundStatusMessage("ESB", "Success", "0", "OK")),
                  transactionNewId))
            case Left(y) =>
              InboundCreateTransactionId(createTransactionRequest.authInfo,
                InternalTransactionId("", List(InboundStatusMessage(
                  "ESB",
                  "Failure",
                  y.PAPIErrorResponse.esbHeaderResponse.responseStatus.callStatus,
                  y.PAPIErrorResponse.esbHeaderResponse.responseStatus.errorDesc.getOrElse("")
                )),
                  transactionNewId))
          }
        case Left(x) =>
          InboundCreateTransactionId(createTransactionRequest.authInfo,
            InternalTransactionId("", List(InboundStatusMessage(
              "ESB",
              "Failure",
              x.PAPIErrorResponse.esbHeaderResponse.responseStatus.callStatus,
              x.PAPIErrorResponse.esbHeaderResponse.responseStatus.errorDesc.getOrElse("")
            )),
              transactionNewId))
          
      }

 


    } else if (createTransactionRequest.transactionRequestType == (TransactionRequestTypes.TRANSFER_TO_ATM.toString)) {
      val transactionRequestBodyTransferToAtmJson = createTransactionRequest.transactionRequestCommonBody.asInstanceOf[TransactionRequestBodyTransferToAtmJson]
      val transactionAmount = transactionRequestBodyTransferToAtmJson.value.amount
      val callNttfW = getNttfWMf(branchId, accountType, accountNumber, cbsToken)
      val cardData = callNttfW.PELET_NTTF_W.P_PRATIM.P_PIRTEY_KARTIS.find(x => x.P_TIKRAT_KARTIS >= transactionAmount).getOrElse(
        throw new RuntimeException(NoCreditCard)
      )
      val callNtbd1v105 = getNtbd1v105Mf(
        branch = branchId,
        accountType = accountType,
        accountNumber = accountNumber,
        cbsToken = cbsToken,
        cardNumber = cardData.P_MISPAR_KARTIS,
        cardExpirationDate = cardData.P_TOKEF_KARTIS,
        cardWithdrawalLimit = cardData.P_TIKRAT_KARTIS,
        mobileNumberOfMoneySender = transactionRequestBodyTransferToAtmJson.from.mobile_phone_number,
        amount = transactionAmount,
        description = transactionRequestBodyTransferToAtmJson.description,
        idNumber = transactionRequestBodyTransferToAtmJson.to.kyc_document.number,
        idType = transactionRequestBodyTransferToAtmJson.to.kyc_document.`type`,
        nameOfMoneyReceiver = transactionRequestBodyTransferToAtmJson.to.legal_name,
        birthDateOfMoneyReceiver = transactionRequestBodyTransferToAtmJson.to.date_of_birth,
        mobileNumberOfMoneyReceiver = transactionRequestBodyTransferToAtmJson.to.mobile_phone_number)
        match {
        case Right(x) =>
          val callNtbd2v105 = getNtbd2v105Mf(
            branchId,
            accountType,
            accountNumber,
            cbsToken,
            ntbd1v105Token = x.P135_BDIKAOUT.P135_TOKEN,
            nicknameOfSender = transactionRequestBodyTransferToAtmJson.from.nickname,
            messageToReceiver = transactionRequestBodyTransferToAtmJson.message)
          match {
            case Right(y) =>
              InboundCreateTransactionId(createTransactionRequest.authInfo,
                InternalTransactionId("", List(InboundStatusMessage(
                  "ESB",
                  "Success",
                  y.PELET_1352.esbHeaderResponse.responseStatus.callStatus,
                  y.PELET_1352.esbHeaderResponse.responseStatus.errorDesc.getOrElse(""))),
                  transactionNewId))
            case Left(y) =>
              InboundCreateTransactionId(createTransactionRequest.authInfo,
                InternalTransactionId("", List(InboundStatusMessage(
                  "ESB",
                  "Failure",
                  y.PAPIErrorResponse.esbHeaderResponse.responseStatus.callStatus,
                  y.PAPIErrorResponse.esbHeaderResponse.responseStatus.errorDesc.getOrElse(""))),
                  transactionNewId))
          }
        case Left(x) =>
          InboundCreateTransactionId(createTransactionRequest.authInfo,
            InternalTransactionId("", List(InboundStatusMessage(
              "ESB",
              "Failure",
              x.PAPIErrorResponse.esbHeaderResponse.responseStatus.callStatus,
              x.PAPIErrorResponse.esbHeaderResponse.responseStatus.errorDesc.getOrElse(""))),
              transactionNewId))

      }


    } else if (createTransactionRequest.transactionRequestType == (TransactionRequestTypes.TRANSFER_TO_ACCOUNT.toString)) {
      val transactionRequestBodyTransferToAccountJson = createTransactionRequest.transactionRequestCommonBody.asInstanceOf[TransactionRequestBodyTransferToAccount]

      val callNtbdAv050 = getNtbdAv050(branchId,
        accountType,
        accountNumber,
        cbsToken,
        transactionRequestBodyTransferToAccountJson.transfer_type,
        transferDateInFuture = transactionRequestBodyTransferToAccountJson.future_date
      )
      val transferToAccountToken = callNtbdAv050.P050_BDIKACHOVAOUT.P050_TOKEN_OUT

      val callNtdBv050 = getNtbdBv050(branchId,
        accountType,
        accountNumber,
        cbsToken,
        ntbdAv050Token = transferToAccountToken,
        toAccountBankId = transactionRequestBodyTransferToAccountJson.to.bank_code,
        toAccountBranchId = transactionRequestBodyTransferToAccountJson.to.branch_number,
        toAccountAccountNumber = transactionRequestBodyTransferToAccountJson.to.account.number,
        toAccountIban = transactionRequestBodyTransferToAccountJson.to.account.iban,
        transactionAmount = transactionRequestBodyTransferToAccountJson.value.amount,
        description = transactionRequestBodyTransferToAccountJson.description,
        referenceNameOfTo = transactionRequestBodyTransferToAccountJson.to.name
      )

      val callNtbdIv050 = getNtbdIv050(
        branchId,
        accountType,
        accountNumber,
        cbsToken,
        ntbdAv050Token = transferToAccountToken,
        transactionAmount = transactionRequestBodyTransferToAccountJson.value.amount
      )

      val callNtbdGv050 = getNtbdGv050(
        branchId,
        accountType,
        accountNumber,
        cbsToken,
        ntbdAv050Token = transferToAccountToken,
        //TODO: check with leumi if bankID 10 implies leumi code 1 here
        bankTypeOfTo = if (transactionRequestBodyTransferToAccountJson.to.bank_code == "10") "0" else "1"
      )

      val callNtbd2v050 = getNtbd2v050(
        branchId,
        accountType,
        accountNumber,
        cbsToken,
        username,
        ntbdAv050Token = transferToAccountToken,
        ntbdAv050fromAccountOwnerName = callNtbdAv050.P050_BDIKACHOVAOUT.P050_SHEM_HOVA_ANGLIT
      )
      InboundCreateTransactionId(createTransactionRequest.authInfo,
        InternalTransactionId("", List(InboundStatusMessage("ESB", "Success", "0", "OK")),
          transactionNewId))

    } else if (createTransactionRequest.transactionRequestType == (TransactionRequestTypes.COUNTERPARTY.toString)) {
      val transactionRequestBodyPhoneToPhoneJson = createTransactionRequest.transactionRequestCommonBody.asInstanceOf[TransactionRequestBodyCounterpartyJSON]

    } else if (createTransactionRequest.transactionRequestType == (TransactionRequestTypes.SEPA.toString)) {
      val transactionRequestBodyPhoneToPhoneJson = createTransactionRequest.transactionRequestCommonBody.asInstanceOf[TransactionRequestBodySEPAJSON]

    } else
      throw new RuntimeException("Do not support this transaction type, please check it in OBP-API side")

    InboundCreateTransactionId(createTransactionRequest.authInfo,
      InternalTransactionId("", List(InboundStatusMessage("ESB", "Success", "0", "OK")),
        transactionNewId))

  }

  def getToken(getTokenRequest: OutboundGetToken): InboundToken = {
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
    val callNtlv1 = getNtlv1Mf(username,
      jsonExtract.SDR_JONI.SDR_MANUI.SDRM_ZEHUT,
      jsonExtract.SDR_JONI.SDR_MANUI.SDRM_SUG_ZIHUY,
      cbsToken
    )
    //TODO: will use the first mobile phone contact available. Check.
    val mobilePhoneData = callNtlv1.O1OUT1AREA_1.O1_CONTACT_REC.find(x => x.O1_TEL_USE_TYPE_CODE == "10").getOrElse(
      O1contactRec(O1recId("", ""), "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", ""))


    val callNtlv7 = getNtlv7Mf(branchId,
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
        InboundStatusMessage("ESB", "Success", "0", "OK"), //TODO, need to fill the coreBanking error
        InboundStatusMessage("MF", "Success", "0", "OK") //TODO, need to fill the coreBanking error
      ),
      answer))
  }

  def getTransactionRequests(outboundGetTransactionRequests210: OutboundGetTransactionRequests210): InboundGetTransactionRequests210 = {

    val accountValues = mapAccountIdToAccountValues(outboundGetTransactionRequests210.counterparty.accountId)
    val branchId = accountValues.branchId
    val accountNumber = accountValues.accountNumber
    val accountType = accountValues.accountType
    val username = outboundGetTransactionRequests210.authInfo.username
    val cbsToken = outboundGetTransactionRequests210.authInfo.cbsToken
    
    val nt1c3result = getNt1c3(
      branchId,
      accountType,
      accountNumber,
      username,
      cbsToken
    )
    
    val nt1c4result = getNt1c4(
      branchId,
      accountType,
      accountNumber,
      username,
      cbsToken
    )
    
    var result = new ListBuffer[TransactionRequest]

    InboundGetTransactionRequests210(
      outboundGetTransactionRequests210.authInfo,
      InternalGetTransactionRequests(
        "",
        List(
          //Todo: We did 3 MfCalls so far. Shall they all go in?
          InboundStatusMessage("ESB", "Success", "0", "OK"), //TODO, need to fill the coreBanking error
          InboundStatusMessage("MF", "Success", "0", "OK") //TODO, need to fill the coreBanking error
        ), 
      Nil))

  }

  def createCounterparty(outboundCreateCounterparty: OutboundCreateCounterparty): InboundCreateCounterparty = {
    val accountValues = mapAccountIdToAccountValues(outboundCreateCounterparty.counterparty.thisAccountId)
    val branchId = accountValues.branchId
    val accountNumber = accountValues.accountNumber
    val accountType = accountValues.accountType

    if (outboundCreateCounterparty.counterparty.thisBankId == "10") {
      val ntg6ACall = getNtg6A(
        branch = branchId,
        accountType = accountType,
        accountNumber = accountNumber,
        cbsToken = outboundCreateCounterparty.authInfo.cbsToken,
        counterpartyBranchNumber = outboundCreateCounterparty.counterparty.otherBranchRoutingAddress,
        counterpartyAccountNumber = outboundCreateCounterparty.counterparty.otherAccountSecondaryRoutingAddress,
        counterpartyName = outboundCreateCounterparty.counterparty.name,
        counterpartyDescription = outboundCreateCounterparty.counterparty.description,
        counterpartyIBAN = outboundCreateCounterparty.counterparty.otherAccountRoutingAddress,
        counterpartyNameInEnglish = outboundCreateCounterparty.counterparty.bespoke(0).value,
        counterpartyDescriptionInEnglish = outboundCreateCounterparty.counterparty.bespoke(1).value
      )
      ntg6ACall match {
        case Right(x) =>

          InboundCreateCounterparty(
            outboundCreateCounterparty.authInfo,
            InternalCreateCounterparty(
              "",
              List(
                InboundStatusMessage(
                  "ESB",
                  "Success",
                  x.NTDriveNoResp.esbHeaderResponse.responseStatus.callStatus,
                  x.NTDriveNoResp.esbHeaderResponse.responseStatus.errorDesc.getOrElse("")),
                InboundStatusMessage(
                  "MF",
                  "Success",
                  x.NTDriveNoResp.MFAdminResponse.returnCode,
                  x.NTDriveNoResp.MFAdminResponse.messageText.getOrElse(""))
              ),
              true.toString
            )
          )
        case Left(x) =>
          InboundCreateCounterparty(
            outboundCreateCounterparty.authInfo,
            InternalCreateCounterparty(
              "",
              List(
                InboundStatusMessage(
                  "ESB",
                  "Success",
                  x.PAPIErrorResponse.esbHeaderResponse.responseStatus.callStatus,
                  x.PAPIErrorResponse.esbHeaderResponse.responseStatus.errorDesc.getOrElse("")),
                InboundStatusMessage(
                  "MF",
                  "Success",
                  x.PAPIErrorResponse.MFAdminResponse.returnCode,
                  x.PAPIErrorResponse.MFAdminResponse.messageText.getOrElse(""))
              ),
              true.toString
            )
          )
      }
    } else {
      val ntg6BCall = getNtg6B(
        branch = branchId,
        accountType = accountType,
        accountNumber = accountNumber,
        cbsToken = outboundCreateCounterparty.authInfo.cbsToken,
        counterpartyBankId = outboundCreateCounterparty.counterparty.otherBankRoutingAddress,
        counterpartyBranchNumber = outboundCreateCounterparty.counterparty.otherBranchRoutingAddress,
        counterpartyAccountNumber = outboundCreateCounterparty.counterparty.otherAccountSecondaryRoutingAddress,
        counterpartyName = outboundCreateCounterparty.counterparty.name,
        counterpartyDescription = outboundCreateCounterparty.counterparty.description,
        counterpartyIBAN = outboundCreateCounterparty.counterparty.otherAccountRoutingAddress,
        counterpartyNameInEnglish = outboundCreateCounterparty.counterparty.bespoke(0).value,
        counterpartyDescriptionInEnglish = outboundCreateCounterparty.counterparty.bespoke(1).value
      )
      ntg6BCall match {
        case Right(x) =>

          InboundCreateCounterparty(
            outboundCreateCounterparty.authInfo,
            InternalCreateCounterparty(
              "",
              List(
                InboundStatusMessage(
                  "ESB",
                  "Success",
                  x.NTDriveNoResp.esbHeaderResponse.responseStatus.callStatus,
                  x.NTDriveNoResp.esbHeaderResponse.responseStatus.errorDesc.getOrElse("")),
                InboundStatusMessage(
                  "MF",
                  "Success",
                  x.NTDriveNoResp.MFAdminResponse.returnCode,
                  x.NTDriveNoResp.MFAdminResponse.messageText.getOrElse(""))
              ),
              true.toString
            )
          )

        case Left(x) =>
          InboundCreateCounterparty(
            outboundCreateCounterparty.authInfo,
            InternalCreateCounterparty(
              "",
              List(
                InboundStatusMessage(
                  "ESB",
                  "Failure",
                  x.PAPIErrorResponse.esbHeaderResponse.responseStatus.callStatus,
                  x.PAPIErrorResponse.esbHeaderResponse.responseStatus.errorDesc.getOrElse("")),
                InboundStatusMessage(
                  "MF",
                  "Failure",
                  x.PAPIErrorResponse.MFAdminResponse.returnCode,
                  x.PAPIErrorResponse.MFAdminResponse.messageText.getOrElse(""))
              ),
              true.toString
            )
          )
      }


    }

  }

  def getCustomer(outboundGetCustomersByUserIdFuture: OutboundGetCustomersByUserIdFuture): InboundGetCustomersByUserIdFuture = {
    val username = outboundGetCustomersByUserIdFuture.authInfo.username
    val joniMfCall = getJoni(username).extract[JoniMfUser]
    //Todo: just gets limit for the leading account instead of limit and balance for all
    val nt1cBCall = getNt1cBMfHttpApache(
      username,
      branch = joniMfCall.SDR_JONI.SDR_MANUI.SDRM_MOVIL_RASHI.SDRM_MOVIL_RASHI_CHN,
      accountType = joniMfCall.SDR_JONI.SDR_MANUI.SDRM_MOVIL_RASHI.SDRM_MOVIL_RASHI_SNIF,
      accountNumber = joniMfCall.SDR_JONI.SDR_MANUI.SDRM_MOVIL_RASHI.SDRM_MOVIL_RASHI_SUG,
      cbsToken = outboundGetCustomersByUserIdFuture.authInfo.cbsToken)
    
    val result = InternalFullCustomer(
      status = "",
      errorCode = "",
      backendMessages = List(InboundStatusMessage("","","","")),
      customerId = getOrCreateCustomerId(username),
      bankId = "10",
      number = username,
      legalName = joniMfCall.SDR_JONI.SDR_MANUI.SDRM_SHEM_PRATI + " " + joniMfCall.SDR_JONI.SDR_MANUI.SDRM_SHEM_MISHPACHA,
      mobileNumber = "notinthiscall",
      email = "notinthiscall",
      faceImage = CustomerFaceImage(joniMfCall.SDR_JONI.SDR_MANUI.SDRM_TAR_LEIDA, "notinthiscall"),
      dateOfBirth= joniMfCall.SDR_JONI.SDR_MANUI.SDRM_TAR_LEIDA,
      relationshipStatus = "notfromthiscall",
      dependents = 0,
      dobOfDependents = List(joniMfCall.SDR_JONI.SDR_MANUI.SDRM_TAR_LEIDA),
      highestEducationAttained = "",
      employmentStatus = "notfromthiscall",
      creditRating = CreditRating("notfromthiscall","notfromthiscall"),
      creditLimit =  AmountOfMoney(defaultCurrency, nt1cBCall.TSHUVATAVLAIT.HH_MISGAROT_ASHRAI.HH_PIRTEY_CHESHBON.HH_MATI.HH_MISGERET_ASHRAI),
      kycStatus = true,
      lastOkDate = joniMfCall.SDR_JONI.SDR_MANUI.SDRM_DATE_LAST + joniMfCall.SDR_JONI.SDR_MANUI.SDRM_TIME_LAST
    )
    InboundGetCustomersByUserIdFuture(outboundGetCustomersByUserIdFuture.authInfo, List(result))
  }
}



