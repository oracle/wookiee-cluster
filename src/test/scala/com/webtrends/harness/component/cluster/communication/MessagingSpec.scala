/*
 * Copyright 2015 Webtrends (http://www.webtrends.com)
 *
 * See the LICENCE.txt file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.webtrends.harness.component.cluster.communication

import java.util.concurrent.TimeUnit

import akka.actor.{Actor, ActorRef, ActorSystem}
import akka.testkit.{TestActorRef, TestKit, TestProbe}
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import com.webtrends.harness.utils.ActorWaitHelper

import scala.concurrent.Await
import scala.concurrent.duration._

@SerialVersionUID(1L) case class RequestMessage(probeRef: ActorRef, string: String, fut: Boolean = false)

@SerialVersionUID(1L) case class ResponseMessage(text: String)


class MessagingSpec extends TestKitSpecificationWithJUnit(ActorSystem("test",
  ConfigFactory.parseString( """
      akka.actor.provider = "cluster"
    """).withFallback(ConfigFactory.load))) {

  implicit val timeout = system.settings.config.getDuration("message-processor.default-send-timeout", TimeUnit.MILLISECONDS) milliseconds
  implicit val actualTimeout = Timeout(timeout)

  lazy val msgActor1 =
    TestActorRef(new Actor with MessagingAdapter {
      def receive = {
        case m: Message =>
          val req = m.body.asInstanceOf[RequestMessage]
          if (req.fut)
            sender() ! ResponseMessage(req.string)
          else
            req.probeRef ! ResponseMessage(req.string)
      }
    }, "test1")

  sequential

  "The message service" should {
    val probe = new TestProbe(system)
    // Allow Messaging to start
    ActorWaitHelper.awaitActorRef(msgActor1, system)
    "allow actors to subscribe and receive published messages" in {
      msgActor1.underlyingActor.subscribe("senditmyway", msgActor1, localOnly = true)
      msgActor1.underlyingActor.publish("senditmyway", RequestMessage(probe.ref, "testPub"))
      ResponseMessage("testPub") must be equalTo probe.expectMsg(ResponseMessage("testPub"))
    }

    "allow actors to subscribe and receive sent messages" in {
      msgActor1.underlyingActor.subscribe("senditmyway", msgActor1, localOnly = true)
      msgActor1.underlyingActor.send("senditmyway", RequestMessage(probe.ref, "testSend"))
      ResponseMessage("testSend") must be equalTo probe.expectMsg(ResponseMessage("testSend"))
    }

    "allow actors to subscribe and receive sent messages as futures" in {
      msgActor1.underlyingActor.subscribe("senditmyway", msgActor1, localOnly = true)
      val fut = msgActor1.underlyingActor.sendWithFuture("senditmyway", RequestMessage(probe.ref, "testSendWFut", fut = true))
      Await.result(fut, timeout) must be equalTo ResponseMessage("testSendWFut")
    }

    "subscribe to many and take messages for each" in {
      msgActor1.underlyingActor.subscribe("topic1", msgActor1, localOnly = true)
      msgActor1.underlyingActor.subscribe("topic2", msgActor1, localOnly = true)
      msgActor1.underlyingActor.subscribe("topic3", msgActor1, localOnly = true)
      msgActor1.underlyingActor.subscribeToMany(Seq("topic4", "topic5", "topic6"),
        msgActor1, localOnly = true)
      val subsFut = msgActor1.underlyingActor.getSubscriptions(Seq("topic1",
        "topic2", "topic3", "topic4", "topic5", "topic6"))
      val subs = Await.result(subsFut, timeout)
      val notFoundTopics = subs.filter(_._2.isEmpty)
      notFoundTopics.keys.toList mustEqual List()
    }
  }

  step {
    TestKit.shutdownActorSystem(system)
  }
}
