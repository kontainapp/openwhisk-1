package org.apache.openwhisk.core.containerpool.kontain

import akka.actor.ActorSystem
import akka.event.Logging.{ErrorLevel, InfoLevel}
import org.apache.openwhisk.common.{Logging, LoggingMarkers, MetricEmitter, TransactionId}
import org.apache.openwhisk.core.containerpool.docker.{ProcessRunner, ProcessTimeoutException}
import org.apache.openwhisk.core.containerpool.{ContainerAddress, ContainerId}

import scala.concurrent.duration.{Duration, _}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

trait KontainApi {
  protected implicit val executionContext: ExecutionContext

  def inspectIPAddress(containerId: ContainerId)(implicit transid: TransactionId): Future[ContainerAddress]

  def run(image: String, name: String)(port: Int)(implicit transid: TransactionId): Future[ContainerId]

  def importImage(image: String)(implicit transid: TransactionId): Future[Boolean]

  def rm(containerId: ContainerId)(implicit transid: TransactionId): Future[Unit]

  def removeAllContainers()(implicit transactionId: TransactionId): Future[Unit]
}

class KontainClient()(implicit val executionContext: ExecutionContext, as: ActorSystem, log: Logging)
    extends KontainApi
    with ProcessRunner {

  override def inspectIPAddress(containerId: ContainerId)(implicit transid: TransactionId): Future[ContainerAddress] = {
    // TODO: we need to support multiple networks and IP, or ports.
    Future(ContainerAddress("localhost", 8080))
  }

  override def importImage(image: String)(implicit transid: TransactionId): Future[Boolean] = {
    log.info(this, "importing the image")
    // For now, we manually load the image to local, so always return success
    Future.successful(true)
  }

  override def run(image: String, name: String)(port: Int)(implicit transid: TransactionId): Future[ContainerId] = {
    log.info(this, s"kontain run (image ${image}) (name ${name})")
    runCmd(
      Seq("kontain", "container", "run", image, name, "--args", port.toString, "--debug"),
      5.seconds,
      "kontain_run")
      .map(_ => ContainerId(name))
  }

  protected def runCmd(cmd: Seq[String], timeout: Duration, tag: String)(
    implicit transid: TransactionId): Future[String] = {
    val start = transid.started(
      this,
      LoggingMarkers.INVOKER_KONTAIN_CMD(tag),
      s"running ${cmd.mkString(" ")} (timeout: $timeout)",
      logLevel = InfoLevel)
    executeProcess(cmd, timeout).andThen {
      case Success(_) => transid.finished(this, start)
      case Failure(pte: ProcessTimeoutException) =>
        transid.failed(this, start, pte.getMessage, ErrorLevel)
        MetricEmitter.emitCounterMetric(LoggingMarkers.INVOKER_KONTAIN_CMD_TIMEOUT(tag))
      case Failure(t) => transid.failed(this, start, t.getMessage, ErrorLevel)
    }
  }

  override def rm(containerId: ContainerId)(implicit transid: TransactionId): Future[Unit] = {
    log.info(this, s"removing kontain ${containerId.asString}")
    runCmd(Seq("kontain", "container", "delete", containerId.asString), 5.seconds, "kontain_delete")
      .map(_ => ())
  }

  override def removeAllContainers()(implicit transactionId: TransactionId): Future[Unit] = {
    log.info(this, "remove all containers")
    runCmd(Seq("kontain", "container", "nuke"), 5.seconds, "kontain_nuke").map(_ => ())
  }

}
