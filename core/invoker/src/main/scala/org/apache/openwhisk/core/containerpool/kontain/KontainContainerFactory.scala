package org.apache.openwhisk.core.containerpool.kontain

import akka.actor.ActorSystem
import org.apache.openwhisk.common.{Logging, TransactionId}
import org.apache.openwhisk.core.containerpool.docker.DockerClientWithFileAccess
import org.apache.openwhisk.core.containerpool.{Container, ContainerFactory, ContainerFactoryProvider}
import org.apache.openwhisk.core.entity.{ByteSize, ExecManifest, InvokerInstanceId}
import org.apache.openwhisk.core.{ConfigKeys, WhiskConfig}
import pureconfig.loadConfigOrThrow

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

case class KontainConfig(extraArgs: Map[String, Set[String]])

object KontainContainerFactoryProvider extends ContainerFactoryProvider {
  override def instance(actorSystem: ActorSystem,
                        logging: Logging,
                        config: WhiskConfig,
                        instance: InvokerInstanceId,
                        parameters: Map[String, Set[String]]): ContainerFactory = {

    val dockerClient = {
      new DockerClientWithFileAccess()(actorSystem.dispatcher)(logging, actorSystem)
    }
    val kontainClient = new KontainClient(dockerClient)(actorSystem.dispatcher, actorSystem, logging)
    new KontainContainerFactory(dockerClient)(instance)(actorSystem, actorSystem.dispatcher, logging, kontainClient)
  }
}

class KontainContainerFactory(docker: DockerClientWithFileAccess)(instance: InvokerInstanceId)(
  implicit actorSystem: ActorSystem,
  ec: ExecutionContext,
  logging: Logging,
  kontain: KontainApi,
  kontainConfig: KontainConfig = loadConfigOrThrow[KontainConfig](ConfigKeys.kontain))
    extends ContainerFactory {

  /**
   * Create a new Container
   *
   * The created container has to satisfy following requirements:
   * - The container's file system is based on the provided action image and may have a read/write layer on top.
   * Some managed action runtimes may need the capability to write files.
   * - If the specified image is not available on the system, it is pulled from an image
   * repository - for example, Docker Hub.
   * - The container needs a network setup - usually, a network interface - such that the invoker is able
   * to connect the action container. The container must be able to perform DNS resolution based
   * on the settings provided via ContainerArgsConfig. If needed by action authors,
   * the container should be able to connect to other systems or even the internet to consume services.
   * - The IP address of said interface is stored in the created Container instance if you want to use
   * the standard init / run behaviour defined in the Container trait.
   * - The default process specified in the action image is run.
   * - It is desired that all stdout / stderr written by processes in the container is captured such
   * that it can be obtained using the logs() method of the Container trait.
   * - It is desired that the container supports and enforces the specified memory limit and CPU shares.
   * In particular, action memory limits rely on the underlying container technology.
   */
  override def createContainer(tid: TransactionId,
                               name: String,
                               actionImage: ExecManifest.ImageName,
                               userProvidedImage: Boolean,
                               memory: ByteSize,
                               cpuShares: Int)(implicit config: WhiskConfig, logging: Logging): Future[Container] = {
    logging.info(this, "create a container")
    KontainContainer.create(tid, actionImage, memory, cpuShares, Some(name))
  }

  /** perform any initialization */
  override def init(): Unit = removeAllContainers()

  /** cleanup any remaining Containers; should block until complete; should ONLY be run at startup/shutdown */
  override def cleanup(): Unit = removeAllContainers()

  private def removeAllContainers(): Unit = {
    implicit val transid = TransactionId.invoker
    val cleaning =
      docker.ps(filters = Seq("name" -> s"${ContainerFactory.containerNamePrefix(instance)}_"), all = true).flatMap {
        containers =>
          logging.info(this, s"removing ${containers.size} action containers.")
          val removals = containers.map { id =>
            docker.rm(id)
          }
          Future.sequence(removals)
      }
    Await.ready(cleaning, 30.seconds)
  }
}
