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

import akka.actor.{Actor, ActorSystem}
import akka.testkit.{TestActorRef, TestKit, TestProbe}
import com.typesafe.config.{Config, ConfigFactory}
import org.specs2.time.NoTimeConversions

import scala.concurrent.Await
import scala.concurrent.duration._

class MessageServiceSpec
  extends TestKitSpecificationWithJUnit(ActorSystem("harness", MessageServiceSpec.loadConfig)) with NoTimeConversions {

  lazy val msgActor = TestActorRef(
    new Actor with MessagingAdapter {
      def receive = {
        case Message(_, "ping") => sender() ! "pong"
        case Message(_, "future-ping") => sender() ! "pong"
      }
    }, "test")

  val probe = new TestProbe(system)
  val systemActor = TestMediatorActor(system)

  probe.send(systemActor, "test")
  implicit val sender = probe.ref
  
  // Run these tests sequentially so that the probes don't bump into the same subscriptions
  sequential

  "The message service adaptor" should {

    "allow actors to subscribe and receive events" in {
      msgActor.underlyingActor.subscribe("test", probe.ref, localOnly = true)
      msgActor.underlyingActor.publish("test", "ping")
      probe.expectMsgClass(classOf[Message])
      success
    }

    "allow actors to subscribe and receive send events using 'self' as the sender" in {
      msgActor.underlyingActor.subscribe("test", probe.ref, true)
      msgActor.underlyingActor.send("test", "ping")
      probe.expectMsgClass(classOf[Message])
      success
    }

    "allow actors to subscribe and receive send events using a defined sender" in {
      msgActor.underlyingActor.subscribe("test", msgActor, true)
      msgActor.underlyingActor.send("test", "ping")(probe.ref)
      "pong" must be equalTo probe.expectMsg("pong")
    }

    "allow actors to subscribe and receive sent messages with a Future" in {
      msgActor.underlyingActor.subscribe("test", msgActor, true)
      val fut = msgActor.underlyingActor.sendWithFuture("test", "future-ping")(4 seconds)

      val res = Await.result(fut, 4 seconds)
      res must be equalTo "pong"
    }
  }

  step {
    TestKit.shutdownActorSystem(system)
  }
}

object MessageServiceSpec {
  def loadConfig: Config = {
    ConfigFactory.parseString( """
        # The default future timeout
        message-processor.default-send-timeout=2
      """).withFallback(ConfigFactory.load).resolve
  }
}
