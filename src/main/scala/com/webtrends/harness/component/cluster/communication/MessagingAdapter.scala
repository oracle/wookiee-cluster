package com.webtrends.harness.component.cluster.communication

import java.util.concurrent.TimeUnit

import akka.actor.{Actor, ActorRef, ActorSelection}
import akka.pattern.ask
import com.webtrends.harness.component.cluster.communication.MessageSubscriptionEvent.Internal.{RegisterSubscriptionEvent, UnregisterSubscriptionEvent}
import com.webtrends.harness.component.cluster.communication.MessageSubscriptionEvent.MessageSubscriptionEvent

import scala.concurrent.Future
import scala.concurrent.duration._

trait MessagingAdapter {
  this: Actor =>
  import MessageService._

  private[communication] lazy val messTimeout = context.system.settings.
    config.getDuration("message-processor.default-send-timeout", TimeUnit.MILLISECONDS) milliseconds

  /**
    * Subscribe for messages. When receiving a message, it will be wrapped in an instance
    * of {@link com.webtrends.harness.logging.communication.MessageEvent}.
    * A SubscribeAck message will be sent to the caller of this method to acknowledge the
    * addition of the subscription.
    * @param topic the topic to receive message from
    * @param subscriber the actor that is to receive the messages. If this actor is terminated then
    *                   the subscription is no longer valid and will have to be reset.
    * @param localOnly are published messages only to come from local sources
    */
  def subscribe(topic: String, subscriber: ActorRef = self, localOnly: Boolean = false): Unit = {
    getOrInitMediator(context.system).tell(Subscribe(Seq(topic), subscriber, localOnly), self)
  }

  /**
    * Subscribe for messages. When receiving a message, it will be wrapped in an instance
    * of {@link com.webtrends.harness.logging.communication.MessageEvent}.
    * A SubscribeAck message will be sent to the caller of this method to acknowledge the
    * addition of the subscription.
    * @param topics the topics to receive message from
    * @param subscriber the actor that is to receive the messages. If this actor is terminated then
    *                   the subscription is no longer valid and will have to be reset.
    * @param localOnly are published messages only to come from local sources
    */
  def subscribeToMany(topics: Seq[String], subscriber: ActorRef = self, localOnly: Boolean = false): Unit = {
    getOrInitMediator(context.system).tell(Subscribe(topics, subscriber, localOnly), self)
  }

  /**
    * Un-subscribe to the given topic.
    * An UnsubscribeAck message will be sent to the caller of this method to acknowledge the
    * addition of the subscription.
    * @param topic the topic to un-subscribe
    * @param subscriber the actor to un-subscribe
    */
  def unsubscribe(topic: String, subscriber: ActorRef = self): Unit = {
    getOrInitMediator(context.system).tell(Unsubscribe(Seq(topic), subscriber), self)
  }

  /**
    * Un-subscribe to the given topic.
    * An UnsubscribeAck message will be sent to the caller of this method to acknowledge the
    * addition of the subscription.
    * @param topics the topic to un-subscribe
    * @param subscriber the actor to un-subscribe
    */
  def unsubscribeFromMany(topics: Seq[String], subscriber: ActorRef = self): Unit = {
    getOrInitMediator(context.system).tell(Unsubscribe(topics, subscriber), self)
  }

  /**
    * Get the subscribers for the given topics. This method should normally only be used as a reference point.
    * @param topics the topics to look up subscribers
    * @return a future that contains a map of topics to subscribers
    */
  def getSubscriptions(topics: Seq[String])(implicit timeout: akka.util.Timeout = messTimeout): Future[Map[String, Seq[ActorSelection]]] =
    getOrInitMediator(context.system).ask(GetSubscriptions(topics))(timeout, self).mapTo[Map[String, Seq[ActorSelection]]]

  /**
    * Sends the message to the given topic. The message will go
    * to one subscriber and is a one-way asynchronous message. E.g. fire-and-forget semantics.
    * When receiving a message, it will be wrapped in an instance
    * of {@link com.webtrends.harness.logging.communication.MessageEvent}.
    * @param topic the topic for the message
    * @param msg the message to send
    * @param sender the implicit ActorRef to act as the sender and will default to self
    */
  def send(topic: String, msg: Any)(implicit sender: ActorRef = self): Unit = {
    getOrInitMediator(context.system).tell(Send(topic, Message.createMessage(topic, msg)), sender)
  }

  /**
    * Sends the message to the given topic. The message will go
    * to one subscriber and a future will be returned.
    * When receiving a message, it will be wrapped in an instance
    * of {@link com.webtrends.harness.logging.communication.MessageEvent}.
    * @param topic the topic for the message
    * @param msg the message to send
    * @param timeout the implicit timeout
    */
  def sendWithFuture(topic: String, msg: Any)(implicit timeout: akka.util.Timeout = messTimeout): Future[Any] = {
    getOrInitMediator(context.system).ask(Send(topic, Message.createMessage(topic, msg)))(timeout, self)
  }

  /**
    * Publish the message to the given topic. The message will go
    * to all subscribers and no return is made. When receiving a message, it will be wrapped in an instance
    * of {@link com.webtrends.harness.logging.communication.MessageEvent}.
    * @param topic the topic for the message
    * @param msg the message to send
    */
  def publish(topic: String, msg: Any): Unit = {
    getOrInitMediator(context.system).tell(Publish(topic, Message.createMessage(topic, msg)), self)
  }

  /**
    * Register for subscription events. This is not used for maintaining
    * subscriptions, but can be used more for testing subscription events.
    * @param registrar the actor that is to receive the events
    * @param to the class to register for
    */
  def register(registrar: ActorRef, to: Class[_ <: MessageSubscriptionEvent]): Unit =
    getOrInitMediator(context.system).tell(RegisterSubscriptionEvent(registrar, to), self)

  /**
    * Unregister for subscription events. This is not used for maintaining
    * subscriptions, but can be used more for testing subscription events.
    * @param registrar the actor that is to receive the events
    * @param to the class to register for
    */
  def unregister(registrar: ActorRef, to: Class[_ <: MessageSubscriptionEvent]): Unit =
    getOrInitMediator(context.system).tell(UnregisterSubscriptionEvent(registrar, to), self)
}
