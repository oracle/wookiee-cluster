package com.webtrends.harness.component.cluster.communication

import java.util.concurrent.TimeUnit

import akka.actor.{ActorRef, ActorSystem, PoisonPill, Props}
import akka.testkit.{TestKit, TestProbe}
import akka.util.Timeout
import com.webtrends.harness.component.cluster.ClusterActor
import com.webtrends.harness.component.cluster.ClusterActor.GetClusterState
import com.webtrends.harness.component.zookeeper.ZookeeperAdapterNonActor
import com.webtrends.harness.component.zookeeper.config.ZookeeperSettings
import com.webtrends.harness.component.zookeeper.mock.MockZookeeper
import com.webtrends.harness.service.test.config.TestConfig
import com.webtrends.harness.utils.ActorWaitHelper
import net.liftweb.json.JValue
import org.apache.curator.test.TestingServer
import org.specs2.mutable.SpecificationWithJUnit

import scala.concurrent.Await
import scala.concurrent.duration._

class ClusterActorSpec extends SpecificationWithJUnit {
  implicit val timeout = Timeout(5, TimeUnit.SECONDS)
  var zkServer: TestingServer = _
  implicit var system: ActorSystem = _
  var zkService: ZookeeperAdapterNonActor = _
  var clusterActor: ActorRef = _
  var messageActor: ActorRef = _

  sequential

  step {
    setLogLevel()
    zkServer = new TestingServer()
    val config = TestConfig.conf("""
      wookiee-zookeeper {
        enabled=false
        quorum="""" + zkServer.getConnectString + """"
        datacenter="Lab"
        pod="Dummy"
        base-path = "/test"
      }
      akka.actor.provider = "cluster"
    """)
    system = ActorSystem.create("ClusterActorSpec", config)
    zkService = MockZookeeper(ZookeeperSettings(config), clusterEnabled = true)

    Await.result(zkService.createNode("/test/Lab_Dummy/1.1/nodes/10.20.30.40:2552",
      ephemeral = true, None), 5 seconds)
    clusterActor = ActorWaitHelper.awaitActor(Props[ClusterActor], system)
    Thread.sleep(1000)
  }

  "The ClusterActor" should {
    "be able to start up" in {
      val probe = TestProbe()
      probe.send(clusterActor, GetClusterState)
      val resp = probe.expectMsgType[JValue]
      println((resp \ "cluster" \ "leader").values.toString)
      resp.isInstanceOf[JValue] mustEqual true
    }
  }

  step {
    clusterActor ! PoisonPill
    TestKit.shutdownActorSystem(system)

    zkServer.stop()
  }

  def setLogLevel(level: ch.qos.logback.classic.Level = ch.qos.logback.classic.Level.INFO) = {
    val logger = org.slf4j.LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)

    logger match {
      case l : ch.qos.logback.classic.Logger => l.setLevel(level)
      case _ => println("cannot get logger")
    }
  }
}
