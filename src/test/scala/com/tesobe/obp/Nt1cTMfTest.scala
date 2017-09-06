package com.tesobe.obp

import com.tesobe.obp.Nt1cTMf._
import com.tesobe.obp.RunMockServer.startMockServer
import com.typesafe.scalalogging.StrictLogging
import org.scalatest.{BeforeAndAfterAll, FunSuite, Matchers}

class Nt1cTMfTest extends FunSuite with Matchers with BeforeAndAfterAll with StrictLogging{

  override def beforeAll() {
    startMockServer
  }

  implicit val formats = net.liftweb.json.DefaultFormats
  
/*  test("Nt1cTMf gets a result"){
    val result = getNt1cTMfHttpApache("","","","",List("","",""),List("","",""),"")
    logger.debug(result)
  }*/
  
  test("getCompletedTransactions works") {
    val result = getCompletedTransactions(
      "814",
      "330",
      "20102612",
      "<~9          81433020102612",
      List("2016",
        "01",
        "01"),
      List("2017", "06", "01"),
      "15"
    )
    logger.debug(result.toString)
  }
    
  override def afterAll() {
    com.tesobe.obp.RunMockServer.mockServer.stop()
  }

}
