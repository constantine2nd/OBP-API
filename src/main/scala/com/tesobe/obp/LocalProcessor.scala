package com.tesobe.obp

import akka.kafka.ConsumerMessage.CommittableMessage
import akka.stream.Materializer
import com.tesobe.obp.SouthKafkaStreamsActor.Business
import com.tesobe.obp.june2017.LeumiDecoder.simpleTransactionDateFormat
import com.tesobe.obp.june2017._
import com.typesafe.scalalogging.StrictLogging
import io.circe.generic.auto._
import io.circe.parser.decode
import io.circe.syntax._
import net.liftweb.json
import net.liftweb.json.Extraction
import net.liftweb.json.JsonAST.prettyRender

import scala.concurrent.{ExecutionContext, Future}

/**
  * Responsible for processing requests from North Side using local json files as data sources.
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
class LocalProcessor(implicit executionContext: ExecutionContext, materializer: Materializer) extends StrictLogging with Config {
  
  implicit val formats = net.liftweb.json.DefaultFormats
  
  /**
    * Processes message that comes from generic 'Request'/'Response' topics.
    * It has to resolve version from request first and based on that employ corresponding Decoder to extract response.
    * For convenience it is done in private method.
    *
    * @return Future of tuple2 containing message given from client and response given from corresponding Decoder.
    *         The form is defined in SouthKafkaStreamsActor
    */
  def generic: Business = { msg =>
    logger.info(s"Processing ${msg.record.value}")
    Future(msg, getResponse(msg))
  }

  /**
    * Processes message that comes from 'GetBanks' topic
    *
    * @return
    */
  def banksFn: Business = { msg =>
    /* call Decoder for extracting data from source file */
    logger.debug(s"Processing banksFn ${msg.record.value}")
    
    try {
      //This may throw exception:
      val response: (OutboundGetBanks => InboundGetBanks) = {q => com.tesobe.obp.june2017.LeumiDecoder.getBanks(q)}
      //This also maybe throw exception, map the error to Exception 
      val r = decode[OutboundGetBanks](msg.record.value()) match {
        case Left(e) => throw new RuntimeException(s"Please check `$OutboundGetBanks` case class for OBP-API and Adapter sides : ", e)
        case Right(x) => response(x).asJson.noSpaces
      }
      Future(msg, r)
    } catch {
      case m: Throwable =>
        logger.error("banksFn-unknown error", m)
        val errorBody = InboundGetBanks(
          AuthInfo("","",""),
          List(
            InboundBank(
              m.getMessage,
              List(
                InboundStatusMessage("ESB","Success", "0", "OK"), //TODO, need to fill the coreBanking error
                InboundStatusMessage("MF","Success", "0", "OK")  //TODO, need to fill the coreBanking error
            ),
             "", "","","")
          )
        )
        
        Future(msg, errorBody.asJson.noSpaces)
    }
   
  }

  def bankFn: Business = { msg =>
    logger.debug(s"Processing bankFn ${msg.record.value}")
    try {
      /* call Decoder for extracting data from source file */
      val response: (OutboundGetBank => InboundGetBank) = { q => com.tesobe.obp.june2017.LeumiDecoder.getBank(q) }
      val r = decode[OutboundGetBank](msg.record.value()) match {
        case Left(e) => throw new RuntimeException(s"Please check `$OutboundGetBank` case class for OBP-API and Adapter sides : ", e);
        case Right(x) => response(x).asJson.noSpaces
      }
      Future(msg, r)
    } catch {
      case m: Throwable =>
        logger.error("banksFn-unknown error", m)
        val errorBody = InboundGetBank(
          AuthInfo("","",""),
            InboundBank(
              m.getMessage,
              List(
                InboundStatusMessage("ESB","Success", "0", "OK"), //TODO, need to fill the coreBanking error
                InboundStatusMessage("MF","Success", "0", "OK")  //TODO, need to fill the coreBanking error
              ),
              "", "","","")
        )
      
        Future(msg, errorBody.asJson.noSpaces)
    }
  }
  
  def userFn: Business = { msg =>
    logger.debug(s"Processing userFn ${msg.record.value}")
    try {
      /* call Decoder for extracting data from source file */
      val response: (OutboundGetUserByUsernamePassword => InboundGetUserByUsernamePassword) = { q => com.tesobe.obp.june2017.LeumiDecoder.getUser(q) }
      val r = decode[OutboundGetUserByUsernamePassword](msg.record.value()) match {
        case Left(e) => throw new RuntimeException(s"Please check `$OutboundGetUserByUsernamePassword` case class for OBP-API and Adapter sides : ", e);
        case Right(x) => response(x).asJson.noSpaces
      }
      Future(msg, r)
    } catch {
      case m: Throwable =>
        logger.error("banksFn-unknown error", m)
        val errorBody = InboundGetUserByUsernamePassword(
          AuthInfo("","",""),
          InboundValidatedUser(
            m.getMessage,
            List(
              InboundStatusMessage("ESB","Success", "0", "OK"), //TODO, need to fill the coreBanking error
              InboundStatusMessage("MF","Success", "0", "OK")  //TODO, need to fill the coreBanking error
            ),
            "", "")
        )
        Future(msg, errorBody.asJson.noSpaces)
    }
  }
  
  def adapterFn: Business = { msg =>
    logger.debug(s"Processing adapterFn ${msg.record.value}")
    try {
    /* call Decoder for extracting data from source file */
      val response: (OutboundGetAdapterInfo => InboundAdapterInfo) = { q => com.tesobe.obp.june2017.LeumiDecoder.getAdapter(q) }
      val r = decode[OutboundGetAdapterInfo](msg.record.value()) match {
        case Left(e) => throw new RuntimeException(s"Please check `$OutboundGetAdapterInfo` case class for OBP-API and Adapter sides : ", e);
        case Right(x) => response(x).asJson.noSpaces
      }
      Future(msg, r)
    } catch {
      case m: Throwable =>
        logger.error("banksFn-unknown error", m)
        val errorBody = InboundAdapterInfo(
          InboundAdapterInfoInternal(
            m.getMessage,
            List(
              InboundStatusMessage("ESB","Success", "0", "OK"), //TODO, need to fill the coreBanking error
              InboundStatusMessage("MF","Success", "0", "OK")  //TODO, need to fill the coreBanking error
            ),
            "", "","", "")
        )
        Future(msg, errorBody.asJson.noSpaces)
    }
  }
  
  def bankAccountIdFn: Business = { msg =>
    try {
      logger.debug(s"Processing bankAccountIdFn ${msg.record.value}")
      /* call Decoder for extracting data from source file */
      val response: (OutboundGetAccountbyAccountID => InboundGetAccountbyAccountID) = { q => com.tesobe.obp.june2017.LeumiDecoder.getBankAccountbyAccountId(q) }
      val r = decode[OutboundGetAccountbyAccountID](msg.record.value()) match {
        case Left(e) => throw new RuntimeException(s"Please check `$OutboundGetAccountbyAccountID` case class for OBP-API and Adapter sides : ", e);
        case Right(x) => response(x).asJson.noSpaces
      }
      Future(msg, r)
    } catch {
      case m: Throwable =>
        logger.error("banksFn-unknown error", m)
        val errorBody = InboundGetAccountbyAccountID(
            AuthInfo("","",""),
            InboundAccountJune2017(
              m.getMessage,
              List(
                InboundStatusMessage("ESB","Success", "0", "OK"), //TODO, need to fill the coreBanking error
                InboundStatusMessage("MF","Success", "0", "OK")   //TODO, need to fill the coreBanking error
              ),
              "", "","", "","", "","","",List(""),List(""),"", "","", "","","")
        )
        Future(msg, errorBody.asJson.noSpaces)
    }
  }
  
  def checkBankAccountExistsFn: Business = { msg =>
    try {
      logger.debug(s"Processing bankAccountIdFn ${msg.record.value}")
      /* call Decoder for extracting data from source file */
      val kafkaRecordValue = msg.record.value()
      val outboundCheckBankAccountExists  = Extraction.extract[OutboundCheckBankAccountExists](json.parse(kafkaRecordValue))
      val inboundGetAccountbyAccountID = com.tesobe.obp.june2017.LeumiDecoder.checkBankAccountExists(outboundCheckBankAccountExists)
      val r = prettyRender(Extraction.decompose(inboundGetAccountbyAccountID))
      
      Future(msg, r)
    } catch {
      case m: Throwable =>
        logger.error("banksFn-unknown error", m)
        val errorBody = InboundGetAccountbyAccountID(
          AuthInfo("","",""),
          InboundAccountJune2017(
            m.getMessage,
            List(
              InboundStatusMessage("ESB","Success", "0", "OK"), //TODO, need to fill the coreBanking error
              InboundStatusMessage("MF","Success", "0", "OK")   //TODO, need to fill the coreBanking error
            ),
            "", "","", "","", "","","",List(""),List(""),"", "","", "","","")
        )
        Future(msg, errorBody.asJson.noSpaces)
    }
  }

  def bankAccountNumberFn: Business = { msg =>
    logger.debug(s"Processing bankAccountNumberFn ${msg.record.value}")
    try {
      /* call Decoder for extracting data from source file */
      val response: (OutboundGetAccountbyAccountNumber => InboundGetAccountbyAccountID) = { q => com.tesobe.obp.june2017.LeumiDecoder.getBankAccountByAccountNumber(q) }
      val r = decode[OutboundGetAccountbyAccountNumber](msg.record.value()) match {
        case Left(e) => throw new RuntimeException(s"Please check `$OutboundGetAccountbyAccountNumber` case class for OBP-API and Adapter sides : ", e);
        case Right(x) => response(x).asJson.noSpaces
      }
      Future(msg, r)
    } catch {
      case m: Throwable =>
        logger.error("banksFn-unknown error", m)
        val errorBody = InboundGetAccountbyAccountID(
          AuthInfo("","",""),
          InboundAccountJune2017(
            m.getMessage,
            List(
              InboundStatusMessage("ESB","Success", "0", "OK"), //TODO, need to fill the coreBanking error
              InboundStatusMessage("MF","Success", "0", "OK")   //TODO, need to fill the coreBanking error
            ),
            "", "","", "","", "","","",List(""),List(""),"", "","", "","","")
        )
        Future(msg, errorBody.asJson.noSpaces)
    }
  }
  
  def bankAccountsFn: Business = {msg =>
    logger.debug(s"Processing bankAccountsFn ${msg.record.value}")
    try {
//    /* call Decoder for extracting data from source file */
      val response: (OutboundGetAccounts => InboundGetAccounts) = { q => com.tesobe.obp.june2017.LeumiDecoder.getBankAccounts(q) }
      val r = decode[OutboundGetAccounts](msg.record.value()) match {
        case Left(e) => throw new RuntimeException(s"Please check `$OutboundGetAccounts` case class for OBP-API and Adapter sides : ", e);
        case Right(x) => response(x).asJson.noSpaces
      }
      Future(msg, r)
    } catch {
      case m: Throwable =>
        logger.error("banksFn-unknown error", m)
        val errorBody = InboundGetAccounts(
          AuthInfo("","",""),
          List(InboundAccountJune2017(
            m.getMessage,
            List(
              InboundStatusMessage("ESB","Success", "0", "OK"), //TODO, need to fill the coreBanking error
              InboundStatusMessage("MF","Success", "0", "OK")   //TODO, need to fill the coreBanking error
            ),
            "", "","", "","", "","","",List(""),List(""),"", "","", "","","")
        ))
        Future(msg, errorBody.asJson.noSpaces)
    }
  }

  def getCoreAccountsFn: Business = {msg =>
    logger.debug(s"Processing getCoreAccountsFn ${msg.record.value}")
    try {
      //    /* call Decoder for extracting data from source file */
      val response: (OutboundGetCoreAccounts => InboundGetCoreAccounts) = { q => com.tesobe.obp.june2017.LeumiDecoder.getCoreAccounts(q) }
      val r = decode[OutboundGetCoreAccounts](msg.record.value()) match {
        case Left(e) => throw new RuntimeException(s"Please check `$OutboundGetCoreAccounts` case class for OBP-API and Adapter sides : ", e);
        case Right(x) => response(x).asJson.noSpaces
      }
      Future(msg, r)
    } catch {
      case m: Throwable =>
        logger.error("banksFn-unknown error", m)
          val errorBody = InboundGetCoreAccounts(
          AuthInfo("","",""),
            List(
              InboundStatusMessage("ESB","Success", "0", "OK"), //TODO, need to fill the coreBanking error
              InboundStatusMessage("MF","Success", "0", "OK")   //TODO, need to fill the coreBanking error
            ),
            List(CoreAccountJsonV300("","", "", AccountRoutingJsonV121("","")))
          )
        Future(msg, errorBody.asJson.noSpaces)
    }
  }

  def getCoreBankAccountsFn: Business = {msg =>
    logger.debug(s"Processing getCoreBankAccountsFn ${msg.record.value}")
    try {
      //    /* call Decoder for extracting data from source file */
      val response: (OutboundGetCoreBankAccounts => InboundGetCoreBankAccounts) = { q => com.tesobe.obp.june2017.LeumiDecoder.getCoreBankAccounts(q) }
      val r = decode[OutboundGetCoreBankAccounts](msg.record.value()) match {
        case Left(e) => throw new RuntimeException(s"Please check `$OutboundGetCoreAccounts` case class for OBP-API and Adapter sides : ", e);
        case Right(x) => response(x).asJson.noSpaces
      }
      Future(msg, r)
    } catch {
      case m: Throwable =>
        logger.error("banksFn-unknown error", m)
        val errorBody = InboundGetCoreBankAccounts(
          AuthInfo("","",""),List(InternalInboundCoreAccount(
            "",
          List(
            InboundStatusMessage("ESB","Success", "0", "OK"), //TODO, need to fill the coreBanking error
            InboundStatusMessage("MF","Success", "0", "OK")   //TODO, need to fill the coreBanking error
          ),
          "","", "", AccountRoutingJsonV121("",""))))
        
        Future(msg, errorBody.asJson.noSpaces)
    }
  }

  def getCustomerFn: Business = {msg =>
    logger.debug(s"Processing getCustomerFn ${msg.record.value}")
    try {
      /* call Decoder for extracting data from source file */
      val kafkaRecordValue = msg.record.value()
      val outboundGetCustomerByUserId  = Extraction.extract[OutboundGetCustomersByUserId](json.parse(kafkaRecordValue))
      val inboundGetCustomersByUserId  = com.tesobe.obp.june2017.LeumiDecoder.getCustomer(outboundGetCustomerByUserId)
      val r = prettyRender(Extraction.decompose(inboundGetCustomersByUserId))
      Future(msg, r)
    } catch {
      case m: Throwable =>
        logger.error("getCustomerFn-unknown error", m)
        val errorBody = InboundGetCustomersByUserId(AuthInfo("","",""),
          List(InternalFullCustomer("",
            m.getMessage,
            List(
            InboundStatusMessage("ESB","Success", "0", "OK"), //TODO, need to fill the coreBanking error
            InboundStatusMessage("MF","Success", "0", "OK")   //TODO, need to fill the coreBanking error
          ),"","","","","","",CustomerFaceImage(None,""),
            simpleTransactionDateFormat.parse("19481231"),"",0,List(None),
          "", "", CreditRating("",""),AmountOfMoney("","0"),false,None)))
        Future(msg, prettyRender(Extraction.decompose(errorBody)))
    }
  }


  def transactionsFn: Business = {msg =>
    logger.debug(s"Processing transactionsFn ${msg.record.value}")
    try {
      /* call Decoder for extracting data from source file */
      val response: (OutboundGetTransactions => InboundGetTransactions) = { q => com.tesobe.obp.june2017.LeumiDecoder.getTransactions(q) }
      val r = decode[OutboundGetTransactions](msg.record.value()) match {
        case Left(e) => throw new RuntimeException(s"Please check `$OutboundGetTransactions` case class for OBP-API and Adapter sides : ", e);
        case Right(x) => response(x).asJson.noSpaces
      }
      Future(msg, r)
    } catch {
      case m: Throwable =>
        logger.error("banksFn-unknown error", m)
        val errorBody = InboundGetTransactions(
          AuthInfo("","",""),
          List(InternalTransaction(
            m.getMessage,
            List(
              InboundStatusMessage("ESB","Success", "0", "OK"), //TODO, need to fill the coreBanking error
              InboundStatusMessage("MF","Success", "0", "OK")   //TODO, need to fill the coreBanking error
            ),
            "", "","", "","", "","","","","","", "","", "")
          ))
        Future(msg, errorBody.asJson.noSpaces)
    }
  } 
  
  def transactionFn: Business = {msg =>
    logger.debug(s"Processing transactionFn ${msg.record.value}")
    try {
      /* call Decoder for extracting data from source file */
      val response: (OutboundGetTransaction => InboundGetTransaction) = { q => com.tesobe.obp.june2017.LeumiDecoder.getTransaction(q) }
      val r = decode[OutboundGetTransaction](msg.record.value()) match {
        case Left(e) => throw new RuntimeException(s"Please check `$OutboundGetTransaction` case class for OBP-API and Adapter sides : ", e);
        case Right(x) => response(x).asJson.noSpaces
      }
      Future(msg, r)
    } catch {
      case m: Throwable =>
        logger.error("banksFn-unknown error", m)
        
        val errorBody = InboundGetTransaction(
          AuthInfo("","",""),
          InternalTransaction(
            m.getMessage,
            List(
              InboundStatusMessage("ESB","Success", "0", "OK"), //TODO, need to fill the coreBanking error
              InboundStatusMessage("MF","Success", "0", "OK")   //TODO, need to fill the coreBanking error
            ),
            "", "","", "","", "","","","","","", "","", ""))
      
        Future(msg, errorBody.asJson.noSpaces)
    }
  }
  
  def createTransactionFn: Business = {msg =>
    logger.debug(s"Processing createTransactionFn ${msg.record.value}")
    try {
      /* call Decoder for extracting data from source file */
      val response: (OutboundCreateTransaction => InboundCreateTransactionId) = { q => com.tesobe.obp.june2017.LeumiDecoder.createTransaction(q) }
      val valueFromKafka: String = msg.record.value()
      //Because the CreateTransaction case class, contain the "sealed trait TransactionRequestCommonBodyJSON"
      //So, we need map the trait explicitly.
      def mapTraitFieldExplicitly(transactionRequestType: String) = valueFromKafka.replace(""""transactionRequestCommonBody":{""",s""""transactionRequestCommonBody":{"${transactionRequestType }": {""").replace("""}},""","""}}},""")
      
      val changeValue = 
        if(valueFromKafka.contains(Util.TransactionRequestTypes.TRANSFER_TO_PHONE.toString)) 
          mapTraitFieldExplicitly(TransactionRequestBodyTransferToPhoneJson.toString())
        else if(valueFromKafka.contains(Util.TransactionRequestTypes.COUNTERPARTY.toString))
          mapTraitFieldExplicitly(TransactionRequestBodyCounterpartyJSON.toString())
        else if(valueFromKafka.contains(Util.TransactionRequestTypes.SEPA.toString))
          mapTraitFieldExplicitly(TransactionRequestBodySEPAJSON.toString())
        else if(valueFromKafka.contains(Util.TransactionRequestTypes.TRANSFER_TO_ATM.toString))
          mapTraitFieldExplicitly(TransactionRequestBodyTransferToAtmJson.toString())
        else if(valueFromKafka.contains(Util.TransactionRequestTypes.TRANSFER_TO_ACCOUNT.toString))
          mapTraitFieldExplicitly(TransactionRequestBodyTransferToAccount.toString())
        else
          throw new RuntimeException("Do not support this transaction type, please check it in OBP-API side")
      
      val r = decode[OutboundCreateTransaction](changeValue) match {
        case Left(e) => throw new RuntimeException(s"Please check `$OutboundCreateTransaction` case class for OBP-API and Adapter sides : ",e);
        case Right(x) => response(x).asJson.noSpaces
      }
      Future(msg, r)
    } catch {
      case m: Throwable =>
        logger.error("banksFn-unknown error", m)
    
        val errorBody = InboundCreateTransactionId(
          AuthInfo("","",""),
          InternalTransactionId(
            m.getMessage,
            List(
              InboundStatusMessage("ESB","Success", "0", "OK"), //TODO, need to fill the coreBanking error
              InboundStatusMessage("MF","Success", "0", "OK")   //TODO, need to fill the coreBanking error
            ),
            ""))
    
        Future(msg, errorBody.asJson.noSpaces)
    }
  }
  
  def tokenFn: Business = {msg =>
    logger.debug(s"Processing tokenFn ${msg.record.value}")
    /* call Decoder for extracting data from source file */
    val response: (OutboundGetToken => InboundToken) = { q => com.tesobe.obp.june2017.LeumiDecoder.getToken(q) }
    val r = decode[OutboundGetToken](msg.record.value()) match {
      case Left(e) => throw new RuntimeException(s"Please check `$OutboundGetToken` case class for OBP-API and Adapter sides : ", e);
      case Right(x) => response(x).asJson.noSpaces
    }
    Future(msg, r)
  }
  
  def createChallengeFn: Business = {msg =>
    logger.debug(s"Processing createChallengeFn ${msg.record.value}")
    try {
      /* call Decoder for extracting data from source file */
      val response: (OutboundCreateChallengeJune2017 => InboundCreateChallengeJune2017) = { q => com.tesobe.obp.june2017.LeumiDecoder.createChallenge(q)}
      val r = decode[OutboundCreateChallengeJune2017](msg.record.value()) match {
        case Left(e) => throw new RuntimeException(s"Please check `$OutboundCreateChallengeJune2017` case class for OBP-API and Adapter sides : ", e);
        case Right(x) => response(x).asJson.noSpaces
      }
      Future(msg, r)
  } catch {
    case m: Throwable =>
      logger.error("banksFn-unknown error", m)
    
      val errorBody = InboundCreateChallengeJune2017(
        AuthInfo("","",""),
        InternalCreateChallengeJune2017(
          m.getMessage,
          List(
            InboundStatusMessage("ESB","Success", "0", "OK"), //TODO, need to fill the coreBanking error
            InboundStatusMessage("MF","Success", "0", "OK")   //TODO, need to fill the coreBanking error
          ),
          ""))
    
      Future(msg, errorBody.asJson.noSpaces)
  }
  }

  def getTransactionRequestsFn: Business = {msg =>
    logger.debug(s"Processing getTransactionRequestsFn ${msg.record.value}")
    try {
      /* call Decoder for extracting data from source file */
      val kafkaRecordValue = msg.record.value()
      val outboundGetTransactionRequests210  = Extraction.extract[OutboundGetTransactionRequests210](json.parse(kafkaRecordValue))
      val inboundGetTransactionRequests210 = com.tesobe.obp.june2017.LeumiDecoder.getTransactionRequests(outboundGetTransactionRequests210)
      val r = prettyRender(Extraction.decompose(InboundGetTransactionRequests210))
      Future(msg, r)
    } catch {
      case m: Throwable =>
        logger.error("getTransactionRequestsFn-unknown error", m)

        val errorBody = InboundGetTransactionRequests210(
          AuthInfo("","",""),
          InternalGetTransactionRequests(
            m.getMessage,
            List(
              InboundStatusMessage("ESB","Success", "0", "OK"), //TODO, need to fill the coreBanking error
              InboundStatusMessage("MF","Success", "0", "OK")   //TODO, need to fill the coreBanking error
            ),
            Nil
          )
        )
        Future(msg, prettyRender(Extraction.decompose(errorBody)))
    }
  }
  
  
  def createCounterpartyFn: Business = {msg =>
    logger.debug(s"Processing createCounterpartyFn ${msg.record.value}")
    try {
      /* call Decoder for extracting data from source file */
      val response: (OutboundCreateCounterparty => InboundCreateCounterparty) = { q => com.tesobe.obp.june2017.LeumiDecoder.createCounterparty(q)}
      val r = decode[OutboundCreateCounterparty](msg.record.value()) match {
        case Left(e) => throw new RuntimeException(s"Please check `$OutboundCreateCounterparty` case class for OBP-API and Adapter sides : ", e);
        case Right(x) => response(x).asJson.noSpaces
      }
      Future(msg, r)
    } catch {
      case m: Throwable =>
        logger.error("getTransactionRequestsFn-unknown error", m)
        
        val errorBody = InboundCreateChallengeJune2017(
          AuthInfo("","",""),
          InternalCreateChallengeJune2017(
            m.getMessage,
            List(
              InboundStatusMessage("ESB","Success", "0", "OK"), //TODO, need to fill the coreBanking error
              InboundStatusMessage("MF","Success", "0", "OK")   //TODO, need to fill the coreBanking error
            ),
            false.toString
          )
        )
        
        Future(msg, errorBody.asJson.noSpaces)
    }
  }
  
  private def getResponse(msg: CommittableMessage[String, String]): String = {
    decode[Request](msg.record.value()) match {
      case Left(e) => e.getLocalizedMessage
      case Right(r) =>
        val rr = r.version.isEmpty match {
          case true => r.copy(version = r.messageFormat)
          case false => r.copy(messageFormat = r.version)
        }
        rr.version match {
          case Some("Nov2016") => com.tesobe.obp.nov2016.Decoder.response(rr)
          case Some("Mar2017") => com.tesobe.obp.mar2017.Decoder.response(rr)
          case Some("June2017") => com.tesobe.obp.june2017.LeumiDecoder.response(rr)
          case _ => com.tesobe.obp.nov2016.Decoder.response(rr)
        }
    }
  }
}

object LocalProcessor {
  def apply()(implicit executionContext: ExecutionContext, materializer: Materializer): LocalProcessor =
    new LocalProcessor()
}

case class FileProcessingException(message: String) extends RuntimeException(message)
