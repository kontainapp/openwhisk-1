package org.apache.openwhisk.core.containerpool.kontain

import akka.actor.ActorSystem
import akka.stream.scaladsl.Source
import akka.util.ByteString
import org.apache.openwhisk.common.{Logging, TransactionId}
import org.apache.openwhisk.core.containerpool.{Container, ContainerAddress, ContainerId}
import org.apache.openwhisk.core.entity.ByteSize
import org.apache.openwhisk.core.entity.ExecManifest.ImageName
import org.apache.openwhisk.core.entity.size._

import scala.concurrent.{ExecutionContext, Future}

case class KontainId(asString: String) {
  require(asString.nonEmpty, "kontainId must not be empty")
}

object KontainContainer {

  def create(transid: TransactionId,
             image: ImageName,
             memory: ByteSize = 256.MB,
             cpuShares: Int = 0,
             name: Option[String] = None)(implicit
                                          as: ActorSystem,
                                          ec: ExecutionContext,
                                          log: Logging,
                                          config: KontainConfig,
                                          kontain: KontainApi): Future[KontainContainer] = ???
}

class KontainContainer(protected val id: ContainerId, protected val addr: ContainerAddress, kontainId: KontainId)(
  implicit
  override protected val as: ActorSystem,
  protected val ec: ExecutionContext,
  protected val logging: Logging,
  kontain: KontainApi)
    extends Container {

  /** Obtains logs up to a given threshold from the container. Optionally waits for a sentinel to appear. */
  override def logs(limit: ByteSize, waitForSentinel: Boolean)(
    implicit transid: TransactionId): Source[ByteString, Any] = ???
}
