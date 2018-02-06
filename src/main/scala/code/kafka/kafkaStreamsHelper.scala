package code.kafka

import java.util.UUID

import akka.actor.{Actor, ActorRef}
import akka.kafka.scaladsl.Consumer
import akka.kafka.{ConsumerSettings, ProducerSettings, Subscriptions}
import akka.pattern.pipe
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import code.actorsystem.{ObpActorHelper, ObpActorInit}
import code.api.util.APIUtil.initPasswd
import code.bankconnectors.AvroSerializer
import code.kafka.Topics.TopicTrait
import code.util.Helper.MdcLoggable
import net.liftweb.common.{Failure, Full}
import net.liftweb.json
import net.liftweb.json.{DefaultFormats, Extraction, JsonAST}
import net.liftweb.util.Props
import org.apache.kafka.clients.consumer.{ConsumerConfig, ConsumerRecord}
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.serialization.{StringDeserializer, StringSerializer}
import code.api.util.ErrorMessages._

import scala.concurrent.{ExecutionException, Future, TimeoutException}

/**
  * Actor for accessing kafka from North side.
  */
class KafkaStreamsHelperActor extends Actor with ObpActorInit with ObpActorHelper with MdcLoggable with KafkaConfig with AvroSerializer {

  implicit val formats = DefaultFormats

  implicit val materializer = ActorMaterializer()

  import materializer._
  /**
    *Random select the partitions number from 0 to kafka.partitions value
    *The specified partition number will be inside the Key.
    */
  private def keyAndPartition = scala.util.Random.nextInt(partitions) + "_" + UUID.randomUUID().toString

  private val consumerSettings = if (Props.get("kafka.use.ssl").getOrElse("false") == "true") {
    ConsumerSettings(system, new StringDeserializer, new StringDeserializer)
      .withBootstrapServers(bootstrapServers)
      .withGroupId(groupId)
      .withClientId(clientId)
      .withMaxWakeups(maxWakeups)
      .withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, autoOffsetResetConfig)
      .withProperty("security.protocol","SSL")
      .withProperty("ssl.truststore.location", Props.get("truststore.path").getOrElse(""))
      .withProperty("ssl.truststore.password", initPasswd)
      .withProperty("ssl.keystore.location",Props.get("keystore.path").getOrElse(""))
      .withProperty("ssl.keystore.password", initPasswd)
  } else {
    ConsumerSettings(system, new StringDeserializer, new StringDeserializer)
      .withBootstrapServers(bootstrapServers)
      .withGroupId(groupId)
      .withClientId(clientId)
      .withMaxWakeups(maxWakeups)
      .withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, autoOffsetResetConfig)
  }

  private val consumer: ((String, Int) => Source[ConsumerRecord[String, String], Consumer.Control]) = { (topic, partition) =>
    val assignment = Subscriptions.assignmentWithOffset(new TopicPartition(topic, partition), 0)
    Consumer.plainSource(consumerSettings, assignment)
      .completionTimeout(completionTimeout)
  }

  private val producerSettings = if (Props.get("kafka.use.ssl").getOrElse("false") == "true") {
    ProducerSettings(system, new StringSerializer, new StringSerializer)
      .withBootstrapServers(bootstrapServers)
      .withProperty("batch.size", "0")
      .withParallelism(3)
      .withProperty("security.protocol","SSL")
      .withProperty("ssl.truststore.location", Props.get("truststore.path").getOrElse(""))
      .withProperty("ssl.truststore.password", initPasswd)
      .withProperty("ssl.keystore.location",Props.get("keystore.path").getOrElse(""))
      .withProperty("ssl.keystore.password", initPasswd)
  } else {
    ProducerSettings(system, new StringSerializer, new StringSerializer)
      .withBootstrapServers(bootstrapServers)
      .withProperty("batch.size", "0")
      .withParallelism(3)
  }

  private val producer = producerSettings
    .createKafkaProducer()

  /**
    * communication with Kafka, send and receive message.
    * This method will send message to Kafka, using the specified key and partition for each topic
    * And get the message from the specified partition and filter by key
    */
  private val sendRequestAndGetResponseFromKafka: ((TopicPair, String, String) => Future[String]) = { (topic, key, value) =>
    //When we send RequestTopic message, contain the partition in it, and when we get the ResponseTopic according to the partition.
    val specifiedPartition = key.split("_")(0).toInt 
    val requestTopic = topic.request
    val responseTopic = topic.response
    //producer will publish the message to broker
    val message = new ProducerRecord[String, String](requestTopic, specifiedPartition, key, value)
    producer.send(message)
    
    //consumer will wait for the message from broker
    consumer(responseTopic, specifiedPartition)
      .filter(_.key() == key) // double check the key 
      .map { msg => 
        logger.debug(s"sendRequestAndGetResponseFromKafka ~~$topic with $msg")
        msg.value
      }
      // .throttle(1, FiniteDuration(10, MILLISECONDS), 1, Shaping)
      .runWith(Sink.head)
  }

  private val stringToJValueF: (String => Future[JsonAST.JValue]) = { r =>
    logger.debug("kafka-response-stringToJValueF:" + r)
    Future(json.parse(r))
  }

  val extractJValueToAnyF: (JsonAST.JValue => Future[Any]) = { r =>
    logger.debug("kafka-response-extractJValueToAnyF:" + r)
    Future(extractResult(r))
  }

  val anyToJValueF: (Any => Future[json.JValue]) = { m =>
    logger.debug("kafka-request-anyToJValueF:" + m)
    Future(Extraction.decompose(m))
  }

  val serializeF: (json.JValue => Future[String]) = { m =>
    logger.debug("kafka-request-serializeF:" + m)
    Future(json.compactRender(m))
  }

  //private val RESP: String = "{\"count\": \"\", \"data\": [], \"state\": \"\", \"pager\": \"\", \"target\": \"banks\"}"

  override def preStart(): Unit = {
    super.preStart()
    val conn = {

      val c = Props.get("connector").openOr("June2017")
      if (c.contains("_")) c.split("_")(1) else c
    }
    //configuration optimization is postponed
    //self ? conn
  }
  
  /**
    * Check the Future, if there are Exceptions, recover the Exceptions to specific JValue 
    * @param sender the sender who send the message to the Actor
    * @param future the future need to be checked 
    *               
    * @return If there is no exception, pipeTo sender
    *         If there is exception, recover to JValue to sender 
    */
  def pipeToSender(sender: ActorRef, future: Future[Any]) = future recover {
    case e: InterruptedException =>
      logger.error(KafkaInterruptedException,e)
      Failure(KafkaInterruptedException+e.toString,Full(e),None)
    case e: ExecutionException =>
      logger.error(KafkaExecutionException,e)
      Failure(KafkaExecutionException+e.toString,Full(e),None)
    case e: TimeoutException =>
      logger.error(KafkaStreamTimeoutException,e)
      Failure(KafkaStreamTimeoutException+e.toString,Full(e),None)
    case e: Throwable =>
      logger.error(KafkaUnknownError,e)
      Failure(KafkaUnknownError+e.toString,Full(e),None)
  } pipeTo sender

  def receive = {
    case value: String =>
      logger.debug("kafka_request[value]: " + value)
      for {
        t <- Future(Topics.topicPairHardCode) // Just have two Topics: obp.request.version and obp.response.version
        r <- sendRequestAndGetResponseFromKafka(t, keyAndPartition, value)
        jv <- stringToJValueF(r)
        any <- extractJValueToAnyF(jv)
      } yield {
        logger.debug("South Side recognises version info")
        any
      }

    // This is for KafkaMappedConnector_vJune2017, the request is TopicTrait  
    case request: TopicTrait =>
      logger.debug("kafka_request[TopicCaseClass]: " + request)
      val f = for {
        t <- Future(Topics.createTopicByClassName(request.getClass.getSimpleName))
        d <- anyToJValueF(request)
        s <- serializeF(d)
        r <- sendRequestAndGetResponseFromKafka(t,keyAndPartition, s)
        jv <- stringToJValueF(r)
        any <- extractJValueToAnyF(jv)
      } yield {
        any
      }
      pipeToSender(sender, f)

    // This is for KafkaMappedConnector_JVMcompatible, KafkaMappedConnector_vMar2017 and KafkaMappedConnector, the request is Map[String, String]  
    case request: Map[String, String] =>
      logger.debug("kafka_request[Map[String, String]]: " + request)
      val orgSender = sender
      val f = for {
        t <- Future(Topics.topicPairFromProps) // Just have two Topics: Request and Response
        d <- anyToJValueF(request)
        v <- serializeF(d)
        r <- sendRequestAndGetResponseFromKafka(t, keyAndPartition, v)
        jv <- stringToJValueF(r)
        any <- extractJValueToAnyF(jv)
      } yield {
        any
      }
      pipeToSender(orgSender, f)
  }
}
/**
  * This case class design a pair of Topic, for both North and South side.
  * They are a pair
  * @param request  eg: obp.June2017.N.GetBanks
  * @param response eg: obp.June2017.S.GetBanks
  */
case class TopicPair(request: String, response: String)

object Topics {
  
  /**
    * Two topics:
    * Request : North is producer, South is the consumer. North --> South
    * Response: South is producer, North is the consumer. South --> North
    */
  private val requestTopic = Props.get("kafka.request_topic").openOr("Request")
  private val responseTopic = Props.get("kafka.response_topic").openOr("Response")
  
  /**
    * set in props, we have two topics: Request and Response
    */
  val topicPairFromProps = TopicPair(requestTopic, responseTopic)

  def topicPairHardCode = TopicPair("obp.Request.version", "obp.Response.version")

  def createTopicByClassName(className: String): TopicPair = {
    /**
      * get the connectorVersion from Props connector attribute
      * eg: in Props, connector=kafka_vJune2017
      *     -->
      *     connectorVersion = June2017
      */
    val connectorVersion = {
      val connectorNameFromProps = Props.get("connector").openOr("June2017")
      val c = if (connectorNameFromProps.contains("_")) connectorNameFromProps.split("_")(1) else connectorNameFromProps
      c.replaceFirst("v", "")
    }
  
    /**
      *  eg: 
      *  obp.June2017.N.GetBank
      *  obp.June2017.S.GetBank
      */
    TopicPair(s"obp.${connectorVersion}.N." + className.replace("$", ""),
      s"obp.${connectorVersion}.S." + className.replace("$", ""))
  }
  
  // @see 'case request: TopicTrait' in  code/bankconnectors/kafkaStreamsHelper.scala 
  // This is for Kafka topics for both North and South sides.
  // In OBP-API, these topics will be created automatically. 
  trait TopicTrait
  
}
