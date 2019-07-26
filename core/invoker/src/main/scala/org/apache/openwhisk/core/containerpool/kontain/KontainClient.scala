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

  def run(image: String, name: String)(implicit transid: TransactionId): Future[ContainerId]

  def importImage(image: String)(implicit transid: TransactionId): Future[Boolean]

  def rm(containerId: ContainerId)(implicit transid: TransactionId): Future[Unit]

  def removeAllContainers()(implicit transactionId: TransactionId): Future[Unit]
}

class KontainClient()(override implicit val executionContext: ExecutionContext,
                      implicit val as: ActorSystem,
                      implicit val log: Logging)
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

  override def run(image: String, name: String)(implicit transid: TransactionId): Future[ContainerId] = {
    log.info(this, s"kontain run (image ${image}) (name ${name})")
    runCmd("runk", Seq("create", name, "-b", image), 5.seconds)
      .flatMap(_ => runCmd("runk", Seq("start", name), 5.seconds))
      .flatMap(_ => Future(ContainerId(name)))
  }

  protected def runCmd(cmd: String, args: Seq[String], timeout: Duration)(
    implicit transid: TransactionId): Future[String] = {
    val start = transid.started(
      this,
      LoggingMarkers.INVOKER_KONTAIN_CMD(args.head),
      s"running ${cmd.mkString(" ")} (timeout: $timeout)",
      logLevel = InfoLevel)
    executeProcess(Seq(cmd) ++ args, timeout).andThen {
      case Success(_) => transid.finished(this, start)
      case Failure(pte: ProcessTimeoutException) =>
        transid.failed(this, start, pte.getMessage, ErrorLevel)
        MetricEmitter.emitCounterMetric(LoggingMarkers.INVOKER_KONTAIN_CMD_TIMEOUT(args.head))
      case Failure(t) => transid.failed(this, start, t.getMessage, ErrorLevel)
    }
  }

  override def rm(containerId: ContainerId)(implicit transid: TransactionId): Future[Unit] = {
    log.info(this, s"removing kontain ${containerId.asString}")
    runCmd("runk", Seq("delete", "--force", containerId.asString), Duration.Inf)
      .map(_ => ())
  }

  override def removeAllContainers()(implicit transactionId: TransactionId): Future[Unit] = {
    log.info(this, "remove all containers")
    runCmd("runk", Seq("nuke"), 5.seconds).map(_ => ())
  }

}
