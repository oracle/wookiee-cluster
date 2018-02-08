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

import akka.actor.{ActorRef, ActorSystem}
import akka.cluster.Cluster
import com.webtrends.harness.component.cluster.ClusterManager
import com.webtrends.harness.logging.LoggingAdapter


object MessageService extends LoggingAdapter {
  private var mediatorOption: Option[ActorRef] = None
  private var clusterOption: Option[Cluster] = None

  // Really only around for testing at this point
  def registerMediator(actor: ActorRef) = {
    log.info(s"Registering Message Mediator: ${actor.path}")
    mediatorOption = Some(actor)
  }

  def unregisterMediator(actor: ActorRef) = {
    log.info(s"Unregistering Message Mediator: ${actor.path}")
    mediatorOption match {
      case Some(m) if m == actor => mediatorOption = None
      case _ => //just do nothing
    }
  }

  def getOrInitMediator(system: ActorSystem): ActorRef = {
    if (mediatorOption.nonEmpty) return mediatorOption.get
    mediatorOption synchronized {
      if (mediatorOption.isEmpty) {
        mediatorOption = Some(system.actorOf(
          MessagingActor.props(MessagingSettings(system.settings.config)),
          ClusterManager.MessagingName))
        log.info(s"Initial Messaging Mediator, [${mediatorOption.get}], " +
          s"Registered for System: $system")
      }
      mediatorOption.get
    }
  }

  def getOrRegisterCluster(system: ActorSystem): Cluster = {
    if (clusterOption.nonEmpty && clusterOption.get.system.equals(system)) return clusterOption.get
    clusterOption synchronized {
      if (clusterOption.isEmpty || !clusterOption.get.system.equals(system)) {
        log.info(s"Initial Cluster Registered for System: $system")
        clusterOption = Some(Cluster(system))
      }
      clusterOption.get
    }
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
