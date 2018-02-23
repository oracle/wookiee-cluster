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

import akka.actor.{ActorRef, ActorSystem, PoisonPill}
import akka.cluster.Cluster
import com.webtrends.harness.component.cluster.ClusterManager
import com.webtrends.harness.logging.LoggingAdapter

import scala.collection.concurrent.TrieMap


object MessageService extends LoggingAdapter {
  private val mediatorMap = TrieMap[ActorSystem, ActorRef]()
  private val clusterMap = TrieMap[ActorSystem, Cluster]()

  // Really only around for testing at this point
  def registerMediator(actor: ActorRef)(implicit system: ActorSystem) = {
    log.info(s"Registering Message Mediator: ${actor.path}")
    mediatorMap.put(system, actor)
  }

  def unregisterMediator(implicit system: ActorSystem) = {
    if (mediatorMap.contains(system)) {
      log.info(s"Unregistering Message Mediator for actor system: [$system]")
      mediatorMap.remove(system) foreach(_ ! PoisonPill)
    }
  }

  def getOrInitMediator(system: ActorSystem): ActorRef = {
    mediatorMap.getOrElse(system, {
      mediatorMap synchronized {
        mediatorMap.getOrElseUpdate(system, {
          val mediator = system.actorOf(
            MessagingActor.props(MessagingSettings(system.settings.config)),
            ClusterManager.MessagingName)
          log.info(s"Initial Messaging Mediator, [$mediator], " +
            s"Registered for System: $system")
          mediator
        })
      }
    })
  }

  def getOrRegisterCluster(system: ActorSystem): Cluster = {
    clusterMap.getOrElse(system, {
      clusterMap synchronized {
        clusterMap.getOrElseUpdate(system, {
          log.info(s"Initial Cluster Registered for System: $system")
          Cluster(system)
        })
      }
    })
  }

  sealed trait MessageCommand {
    def topic: String
  }

  sealed trait MessageSubscriptionCommand {
    def topics: Seq[String]
  }

  /**
   * This is a pre-defined topic that is used to broadcast subscription events.
   * This is not used for maintaining subscriptions, but can be used more for testing
   * subscription events. The message received by the subscriber will contain
   * an instance of {@link com.webtrends.harness.logging.communication.MessageService.Subscribe}
   * or {@link com.webtrends.harness.logging.communication.MessageService.Unsubscribe}.
   */
  val MessageSubscriptionEventTopic = "message-subscription-event"

  /**
   * Subscribe to the given topic
   * @param topics the topics to receive message from
   * @param ref the actor that is to receive the messages. If this actor is terminated then
   *            the subscription is no longer valid and will have to be reset.
   * @param localOnly are published messages only to come from local sources
   */
  @SerialVersionUID(1L) case class Subscribe(topics: Seq[String], ref: ActorRef, localOnly: Boolean) extends MessageSubscriptionCommand

  /**
   * Acknowledgement of the subscription
   * @param subscribe the original Subscribe message
   */
  @SerialVersionUID(1L) case class SubscribeAck(subscribe: Subscribe)

  /**
   * Un-subscribe to the given topic
   * @param topics the topics to un-subscribe
   * @param ref the actor to un-subscribe
   */
  @SerialVersionUID(1L) case class Unsubscribe(topics: Seq[String], ref: ActorRef) extends MessageSubscriptionCommand

  /**
   * Acknowledgement of the un-subscription
   * @param unsubscribe the original Unsubscribe message
   */
  @SerialVersionUID(1L) case class UnsubscribeAck(unsubscribe: Unsubscribe)

  /**
   * Get a map of subscribers for the given topic. This method should normally only be used as a reference point.
   * @param topics the topics to look up subscribers
   */
  @SerialVersionUID(1L) case class GetSubscriptions(topics: Seq[String]) extends MessageSubscriptionCommand

  /**
   * Publish a message to the given topic
   * @param topic the topic to send the message to
   * @param msg the message to send
   */
  @SerialVersionUID(1L) case class Publish(topic: String, msg: Message) extends MessageCommand

  /**
   * Send a message to the given topic. The message will only be sent to one
   * of the subscribers for the topic.
   * @param topic the topic to send the message to
   * @param msg the message to send
   */
  @SerialVersionUID(1L) case class Send(topic: String, msg: Message) extends MessageCommand

}
