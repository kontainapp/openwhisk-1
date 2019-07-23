package org.apache.openwhisk.core.containerpool.kontain

import akka.actor.ActorSystem
import akka.event.Logging.{ErrorLevel, InfoLevel}
import org.apache.openwhisk.common.{Logging, LoggingMarkers, MetricEmitter, TransactionId}
import org.apache.openwhisk.core.containerpool.docker.{DockerClient, ProcessRunner, ProcessTimeoutException}
import org.apache.openwhisk.core.containerpool.{ContainerAddress, ContainerId}

import scala.concurrent.duration.Duration
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

trait KontainApi {
  protected implicit val executionContext: ExecutionContext

  def inspectIPAddress(containerId: ContainerId)(implicit transid: TransactionId): Future[ContainerAddress]

  def run(image: String, args: Seq[String])(implicit transid: TransactionId): Future[ContainerId]

  def importImage(image: String)(implicit transid: TransactionId): Future[Boolean]

  def rm(containerId: ContainerId)(implicit transid: TransactionId): Future[Unit]

}

class KontainClient(dockerClient: DockerClient)(override implicit val executionContext: ExecutionContext,
                                                implicit val as: ActorSystem,
                                                implicit val log: Logging)
    extends KontainApi
    with ProcessRunner {

  override def inspectIPAddress(containerId: ContainerId)(implicit transid: TransactionId): Future[ContainerAddress] = {
    log.info(this, "inspect the container ip address")
    Future(ContainerAddress("localhost"))
  }

  override def run(image: String, args: Seq[String])(implicit transid: TransactionId): Future[ContainerId] = {
    // TODO: set the correct args
    //dockerClient.run(image, args)
    log.info(this, "kontain run")
    Future(ContainerId(transid.id))
  }

  override def importImage(image: String)(implicit transid: TransactionId): Future[Boolean] = {
    log.info(this, "importing the image")
    // For now, we manually load the image to local, so always return success
    Future.successful(true)
  }

  override def rm(containerId: ContainerId)(implicit transid: TransactionId): Future[Unit] = {
    //dockerClient.rm(containerId)
    log.info(this, "removing kontain")
    Future{}
  }

  // TODO:
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

}
