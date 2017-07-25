package com.tesobe.obp

import net.liftweb.json.JValue
import net.liftweb.json.JsonAST.compactRender
import net.liftweb.json.JsonParser._
import net.liftweb.json.JsonDSL._
import net.liftweb.json.JsonParser._  
import org.apache.http.client.methods.HttpPost
import org.apache.http.impl.client.DefaultHttpClient

/**
  * Created by work on 6/12/17.
  */
object Ntib2Mf extends Config{
  
  def getNtib2Mf(mainframe: String): String = {
    val source = scala.io.Source.fromFile(mainframe)
    val lines = try source.mkString finally source.close()
    lines
  }

  def getNtib2MfHttpApache(branch: String, accountType: String, accountNumber: String, username: String, cbsToken: String): String = {

    val url = config.getString("bankserver.url")


    val post = new HttpPost(url + "/ESBLeumiDigitalBank/PAPI/v1.0/NTIB/2/000/01.01")
    println(post)
    post.addHeader("application/json;charset=utf-8","application/json;charset=utf-8")

    val client = new DefaultHttpClient()

    val json: JValue = "NTIB_2_000" -> ("NtdriveCommonHeader" -> ("KeyArguments" -> ("Branch" -> branch) ~ ("AccountType" ->
      accountType) ~ ("AccountNumber" -> accountNumber)) ~ ("AuthArguments" ->( ("User" -> username) ~ ("MFToken" -> cbsToken))))
    println(compactRender(json))

    // send the post request
    val response = client.execute(post)
    val inputStream = response.getEntity.getContent
    val result = scala.io.Source.fromInputStream(inputStream).mkString
    response.close()
    result
  }
  
  
  
  def getIban(branch: String, accountType: String, accountNumber: String, username: String, cbsToken: String) = {
    val parser = (p: Parser) => {
      def parse: String = p.nextToken match {
        case FieldStart("TS00_IBAN") => p.nextToken match {
          case StringVal(token) => token
          case _ => p.fail("expected string")
        }
        case End => p.fail("no field named 'TS00_IBAN'")
        case _ => parse
      }

      parse
    }
    parse(getNtib2MfHttpApache(branch,  accountType,  accountNumber, username, cbsToken), parser)
  }
}
