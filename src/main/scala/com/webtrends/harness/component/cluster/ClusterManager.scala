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
package com.webtrends.harness.component.cluster

import com.webtrends.harness.component.Component
import com.webtrends.harness.component.cluster.communication.{MessageService, MessagingAdapter}
import com.webtrends.harness.component.zookeeper.Zookeeper

import scala.collection.mutable

/**
 * Important to note that if you use clustering you must not include the Zookeeper component
 * as Clustering will start it up independently, and zookeeper config must be within the clustering config
 */
class ClusterManager(name:String) extends Component(name)
    with Clustering
    with MessagingAdapter
    with Zookeeper {
  MessageService.getOrInitMediator(system)
  implicit val clusterSettings = ClusterSettings(config)

  override protected def defaultChildName: Option[String] = Some(ClusterManager.MessagingName)

  override def start = {
    startZookeeper()
    startClustering
    super.start
  }

  override def stop = {
    super.stop
    stopClustering
  }

  override protected def getHealthChildren = {
    super.getHealthChildren.toSet + MessageService.getOrInitMediator(system)
  }
}

object ClusterManager {
  val ComponentName = "wookiee-cluster"
  val MessagingName = "messaging"

  // TODO Think about moving this up to wookiee-core
  // Creates a thread-safe hash set as a Scala mutable.Set
  def createSet[T](): mutable.Set[T] = {
    import scala.collection.JavaConverters._
    java.util.Collections.newSetFromMap(
      new java.util.concurrent.ConcurrentHashMap[T, java.lang.Boolean]).asScala
  }
}
