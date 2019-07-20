package org.apache.openwhisk.core.containerpool.kontain

import org.apache.openwhisk.common.TransactionId
import org.apache.openwhisk.core.containerpool.docker.ProcessRunner
import org.apache.openwhisk.core.containerpool.{ContainerAddress, ContainerId}

import scala.concurrent.{ExecutionContext, Future}

trait KontainApi {
  protected implicit val executionContext: ExecutionContext

  def inspectIPAddress(containerId: ContainerId)(implicit transid: TransactionId): Future[ContainerAddress]

  def containerId(kontainId: KontainId)(implicit transid: TransactionId): Future[ContainerId]

  def run(image: String, args: Seq[String])(implicit transid: TransactionId): Future[KontainId]

  def importImage(image: String)(implicit transid: TransactionId): Future[Boolean]

  def rm(kontainId: KontainId)(implicit transid: TransactionId): Future[Unit]

  def stop(kontainId: KontainId)(implicit transid: TransactionId): Future[Unit]

  def stopAndRemove(kontainId: KontainId)(implicit transid: TransactionId): Future[Unit] = {
    for {
      _ <- stop(kontainId)
      _ <- rm(kontainId)
    } yield Unit
  }
}

class KontainClient()(override implicit val executionContext: ExecutionContext) extends KontainApi with ProcessRunner {

  override def inspectIPAddress(containerId: ContainerId)(implicit transid: TransactionId): Future[ContainerAddress] =
    Future.successful(ContainerAddress(""))

  override def containerId(kontainId: KontainId)(implicit transid: TransactionId): Future[ContainerId] =
    Future.successful(ContainerId(""))

  override def run(image: String, args: Seq[String])(implicit transid: TransactionId): Future[KontainId] =
    Future.successful(KontainId(""))

  override def importImage(image: String)(implicit transid: TransactionId): Future[Boolean] = Future.successful(true)

  override def rm(kontainId: KontainId)(implicit transid: TransactionId): Future[Unit] = ???

  override def stop(kontainId: KontainId)(implicit transid: TransactionId): Future[Unit] = ???
}
