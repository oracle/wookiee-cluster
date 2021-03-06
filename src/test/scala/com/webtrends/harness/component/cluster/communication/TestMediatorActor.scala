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

import akka.actor._
import akka.testkit.TestActorRef
import com.webtrends.harness.component.cluster.communication.MessageService._
import com.webtrends.harness.logging.ActorLoggingAdapter

object TestMediatorActor {
  def apply(system: ActorSystem): ActorRef = system.actorOf(Props[TestMediatorActor], "system")
}

class TestMediatorActor extends Actor with ActorLoggingAdapter {
  import context.system

  val mediator = TestActorRef(new Actor {
    var map = Map[String, ActorRef]()
    def receive = {
      case s: Subscribe => s.topics foreach {
        topic => map += (topic -> s.ref)
      }
        sender() ! SubscribeAck(s)
      case u: Unsubscribe => u.topics foreach {
        topic => map -= topic
      }
        sender() ! UnsubscribeAck(u)
      case Publish(topic, msg) => if (map.contains(topic)) map(topic) forward msg
      case s: Send => if (map.contains(s.topic)) map(s.topic) forward s.msg
    }
  })

  MessageService.registerMediator(mediator)

  def receive = {
    case x => log.info("Received {}", x.toString)
  }

  override def postStop() = {
    MessageService.unregisterMediator
  }
}
