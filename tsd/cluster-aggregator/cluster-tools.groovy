import groovy.grape.Grape

akka_version = '2.3.10'
scala_version = '2.11'
Grape.grab(group: "com.typesafe.akka", module: "akka-actor_$scala_version", version: akka_version, classLoader: Thread.currentThread().getContextClassLoader())
Grape.grab(group: 'com.typesafe.akka', module: "akka-cluster_$scala_version", version: akka_version, classLoader: Thread.currentThread().getContextClassLoader())
Grape.grab(group: 'com.typesafe.akka', module: "akka-remote_$scala_version", version: akka_version, classLoader: Thread.currentThread().getContextClassLoader())
Grape.grab(group: 'com.typesafe.akka', module: "akka-contrib_$scala_version", version: akka_version, classLoader: Thread.currentThread().getContextClassLoader())
import akka.actor.*
import akka.cluster.InternalClusterAction
import akka.cluster.ClusterUserAction
import akka.pattern.Patterns
import com.typesafe.config.ConfigFactory
import scala.concurrent.Await
import scala.concurrent.duration.FiniteDuration

import java.util.concurrent.TimeUnit

class Cluster {
    def ActorSystem system
    def String host
    def Cluster() {
        def rootConfig = ConfigFactory.load(this.class.classLoader)
        def config = ConfigFactory.parseString('''
            akka {
              loglevel = "DEBUG"
              stdout-loglevel = "DEBUG"

               actor {
                 debug {
                   unhandled = on
                 }
                 provider = "akka.remote.RemoteActorRefProvider"
               }
              remote {
                log-remote-lifecycle-events = on
                netty.tcp {
                  hostname = ""
                  port = 0
                }
              }
            }
        ''')
        rootConfig.resolve()
        system = akka.actor.ActorSystem.apply("Metrics", config.withFallback(rootConfig))
    }

    def connect(String host) {
        this.host = host
    }

    def leave(String host) {
        def selection = system.actorSelection("${this.host}/system/cluster")
        def ref = Await.result(Patterns.ask(selection, new InternalClusterAction.GetClusterCoreRef$(),
                5000),
                FiniteDuration.apply(10, TimeUnit.SECONDS))
        ref.tell(ClusterUserAction.Leave.apply(akka.actor.AddressFromURIString.apply(host)), ActorRef.noSender())
    }

    def down(String host) {
        def selection = system.actorSelection("${this.host}/system/cluster")
        def ref = Await.result(Patterns.ask(selection, new InternalClusterAction.GetClusterCoreRef$(),
                5000),
                FiniteDuration.apply(10, TimeUnit.SECONDS))
        ref.tell(new ClusterUserAction.Down(akka.actor.AddressFromURIString.apply(host)), ActorRef.noSender())
    }
}


cluster = new Cluster()
