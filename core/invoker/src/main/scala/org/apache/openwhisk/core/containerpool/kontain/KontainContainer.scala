package org.apache.openwhisk.core.containerpool.kontain

import java.time.Instant

import akka.actor.ActorSystem
import akka.stream.scaladsl.Source
import akka.util.ByteString
import org.apache.openwhisk.common.{Logging, TransactionId}
import org.apache.openwhisk.core.containerpool.logging.LogLine
import org.apache.openwhisk.core.containerpool.{BlackboxStartupError, Container, ContainerAddress, ContainerId, WhiskContainerStartupError}
import org.apache.openwhisk.core.entity.ByteSize
import org.apache.openwhisk.core.entity.ExecManifest.ImageName
import org.apache.openwhisk.core.entity.size._
import org.apache.openwhisk.http.Messages
import spray.json._

import scala.concurrent.{ExecutionContext, Future}

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
                                          kontain: KontainApi): Future[KontainContainer] = {
    implicit val tid: TransactionId = transid

    val args = Seq(
      "--device",
      "/dev/kvm",
      "--network",
      "bridge") ++
      name.map(n => Seq("--name", n)).getOrElse(Seq.empty)


    for {
      ret <- kontain.importImage(image.publicImageName)
      containerId <- kontain.run(image.publicImageName, args).recoverWith {
        case _ =>
          if (ret)
            Future.failed(WhiskContainerStartupError(Messages.resourceProvisionError))
          else
            Future.failed(BlackboxStartupError(Messages.imagePullError(image.publicImageName)))
      }
      ip <- kontain.inspectIPAddress(containerId).recoverWith {
        case _ =>
          kontain.rm(containerId)
          Future.failed(WhiskContainerStartupError(Messages.resourceProvisionError))
      }
    } yield new KontainContainer(containerId, ip)
  }
}

class KontainContainer(protected val id: ContainerId, protected val addr: ContainerAddress)(
  implicit
  override protected val as: ActorSystem,
  protected val ec: ExecutionContext,
  protected val logging: Logging,
  kontain: KontainApi)
    extends Container {

  /** Obtains logs up to a given threshold from the container. Optionally waits for a sentinel to appear. */
  override def logs(limit: ByteSize, waitForSentinel: Boolean)(
    implicit transid: TransactionId): Source[ByteString, Any] =
    Source.single(ByteString(LogLine("", "stdout", Instant.now.toString).toJson.compactPrint))

  override def destroy()(implicit transid: TransactionId): Future[Unit] = {
    super.destroy()
    kontain.rm(id)
  }
}
