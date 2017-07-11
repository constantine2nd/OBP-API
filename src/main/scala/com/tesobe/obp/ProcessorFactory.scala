package com.tesobe.obp

import com.tesobe.obp.Main.{executionContext, materializer}
import com.tesobe.obp.SouthKafkaStreamsActor.BusinessTopic
import com.tesobe.obp.jun2017._

/**
  * Defines kafka topics which will be used and functions that will be applied on received message
  *
  */
trait ProcessorFactory {
  this: Config =>

  /**
    *
    * @return sequence of functions which will be applied in processing of North Side messages
    */
  def getProcessor = {
    processorName match {
      case "localFile" => Seq(
        BusinessTopic(topic, LocalProcessor()(executionContext, materializer).generic),
        BusinessTopic(createTopicByClassName(GetBanks.getClass.getSimpleName), LocalProcessor()(executionContext, materializer).banksFn),
        BusinessTopic(createTopicByClassName(GetBank.getClass.getSimpleName), LocalProcessor()(executionContext, materializer).bankFn),
        BusinessTopic(createTopicByClassName(GetAdapterInfo.getClass.getSimpleName), LocalProcessor()(executionContext, materializer).adapterFn),
        BusinessTopic(createTopicByClassName(GetUserByUsernamePassword.getClass.getSimpleName), LocalProcessor()(executionContext, materializer).userFn),
        //BusinessTopic(createTopicByClassName(GetUserByUsernamePassword.getClass.getSimpleName), LocalProcessor()(executionContext, materializer).userByUsernameFn),
        //BusinessTopic(caseClassToTopic(GetUserByUsernamePassword.getClass.getSimpleName), LocalProcessor()(executionContext, materializer).userFn),
        BusinessTopic(createTopicByClassName(GetUserBankAccounts.getClass.getSimpleName), LocalProcessor()(executionContext, materializer).bankAccountsFn)
      )
      case "mockedSopra" => BusinessTopic(topic, LocalProcessor()(executionContext, materializer).generic)
      case "sopra" => BusinessTopic(topic, LocalProcessor()(executionContext, materializer).generic)
      case _ => BusinessTopic(topic, LocalProcessor()(executionContext, materializer).generic)
    }
  }
}
