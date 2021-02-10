package com.openbankproject.commons.util

import net.liftweb.json

import java.util.Date
import net.liftweb.json.Extraction.decompose
import net.liftweb.json.Formats
import org.scalatest.{FlatSpec, Matchers, Tag}

class JsonUtilsTest extends FlatSpec with Matchers {
  object FunctionsTag extends Tag("JsonUtils")
  implicit def formats: Formats = net.liftweb.json.DefaultFormats

  "collectFieldNames" should "return all the field names and path" taggedAs FunctionsTag in {

    case class NestNestClass(nestNestField: String)
    case class NestClass(nestField: String, nestNestClass: NestNestClass)
    case class TestObject(
      stringField: String,
      nestClass: NestClass,
      date: Date,
      boolean: Boolean
    )

    val testObject = TestObject(
      "1",
      NestClass("1", NestNestClass("2")),
      new Date(),
      true
    )

    implicit def formats: Formats = net.liftweb.json.DefaultFormats
    val fields = JsonUtils.collectFieldNames(decompose(testObject))

    val names: List[String] = fields.map(_._1).toList
    names.length should be (7)
    names should contain ("stringField")
    names should contain ("nestClass")
    names should contain ("date")
    names should contain ("boolean")
    names should contain ("nestField")
    names should not contain ("nestField1")
  }

  def toCaseClass(str: String): String = JsonUtils.toCaseClasses(json.parse(str))

  "object json String" should "generate correct case class" taggedAs FunctionsTag in {

    val zson = {
      """
        |{
        |   "name": "Sam",
        |   "age": 12,
        |   "isMarried": true,
        |   "weight": 12.11,
        |   "class": "2",
        |   "def": 12,
        |   "email": ["abc@def.com", "hijk@abc.com"],
        |   "address": [{
        |     "name": "jieji",
        |     "code": 123123,
        |     "street":{"road": "gongbin", "number": 123}
        |   }],
        |   "street": {"name": "hongqi", "width": 12.11}
        |   "_optional_fields_": ["age", "weight", "address"]
        |}
        |""".stripMargin
    }
    val expectedCaseClass =
    """case class AddressStreetJsonClass(road: String, number: Long)
      |case class AddressJsonClass(name: String, code: Long, street: AddressStreetJsonClass)
      |case class StreetJsonClass(name: String, width: BigDecimal)
      |case class RootJsonClass(name: String, age: Option[Long], isMarried: Boolean, weight: Option[BigDecimal], `class`: String, `def`: Long, email: List[String], address: Option[List[AddressJsonClass]], street: StreetJsonClass)""".stripMargin

    val generatedCaseClass = toCaseClass(zson)

    generatedCaseClass should be (expectedCaseClass)
  }

  "List json" should "generate correct case class" taggedAs FunctionsTag in {
    {
      val listIntJson = """[1,2,3]"""
      val expectedCaseClass = """ type RootJsonClass = List[Long]"""

      val generatedCaseClass = toCaseClass(listIntJson)

      generatedCaseClass should be(expectedCaseClass)
    }
    {
      val listObjectJson =
        """[
          | {
          |   "name": "zs"
          |   "weight": 12.34
          | },
          | {
          |   "name": "ls"
          |   "weight": 21.43
          | }
          |]""".stripMargin
      val expectedCaseClass = """case class RootItemJsonClass(name: String, weight: BigDecimal)
                                | type RootJsonClass = List[RootItemJsonClass]""".stripMargin

      val generatedCaseClass = toCaseClass(listObjectJson)

      generatedCaseClass should be(expectedCaseClass)
    }
  }
  "List json have different type items" should "throw exception" taggedAs FunctionsTag in {

    val listJson = """["abc",2,3]"""
    val listJson2 =
      """[
        | {
        |   "name": "zs"
        |   "weight": 12.34
        | },
        | {
        |   "name": "ls"
        |   "weight": 21
        | }
        |]""".stripMargin
    val objectJson =
      """{
        | "emails": [true, "abc@def.com"]
        |}""".stripMargin

    val objectNestedListJson =
      """{
        | "emails": {
        |   "list": [12.34, "abc@def.com"]
        |   }
        |}""".stripMargin

    the [IllegalArgumentException] thrownBy toCaseClass(listJson) should have message "All the items of Json  should be String type."
    the [IllegalArgumentException] thrownBy toCaseClass(listJson2) should have message "All the items of Json  should the same structure."
    the [IllegalArgumentException] thrownBy toCaseClass(objectJson) should have message "All the items of Json emails should be Boolean type."
    the [IllegalArgumentException] thrownBy toCaseClass(objectNestedListJson) should have message "All the items of Json emails.list should be number type."
  }

}
