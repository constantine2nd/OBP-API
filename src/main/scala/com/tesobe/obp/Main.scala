package com.tesobe.obp

import akka.actor.{ActorSystem, Props}
import akka.stream.{ActorMaterializer, ActorMaterializerSettings, Supervision}
import com.tesobe.obp.RunMockServer.startMockServer
import com.typesafe.scalalogging.StrictLogging

import scala.concurrent.Await
import scala.concurrent.duration.Duration

/**
  * Initialize actor system and as final step sends message to ActorOrchestration to initialize all actors that will be used.
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
object Main extends App with StrictLogging with Config with ProcessorFactory {

  logger.debug("The Adapter's current commit is : "+Util.gitCommit)
  logger.debug("The Adapter's complete props is : "+config)
/*  print("Enter the Password for the SSL Client Certificate: ")
  //As most IDEs do not provide a Console, we fall back to readLine
  val clientCertificatePw:String =  try {
    System.console.readPassword().toString
  } catch {
    case e: NullPointerException => scala.io.StdIn.readLine()
  }*/
  if (config.getBoolean("mockserver.run")) {
    startMockServer
  }
  /**
    * Reaction on unexpected events
    */
  val decider: Supervision.Decider = {
    case e: Throwable =>
      logger.error("Exception occurred, stopping...", e)
      Supervision.Restart
    case _ =>
      logger.error("Unknown problem, stopping...")
      Supervision.Restart
  }

  implicit val system = ActorSystem(s"$systemName")
  implicit val materializer = ActorMaterializer(ActorMaterializerSettings(system).withSupervisionStrategy(decider))
  implicit val executionContext = system.dispatcher

  val actorOrchestration = system.actorOf(Props(new ActorOrchestration()), ActorOrchestration.name)

  actorOrchestration ! getProcessor

  //  import fs2.Stream

  Await.ready(system.whenTerminated, Duration.Inf)
}
