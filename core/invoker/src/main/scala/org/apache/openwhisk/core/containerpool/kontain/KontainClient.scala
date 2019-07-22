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

class KontainClient(dockerClient: DockerClient)(override implicit val executionContext: ExecutionContext,
                                                implicit val as: ActorSystem,
                                                implicit val log: Logging)
    extends KontainApi
    with ProcessRunner {

  override def inspectIPAddress(containerId: ContainerId)(implicit transid: TransactionId): Future[ContainerAddress] = {
    // TODO:
    Future.successful(ContainerAddress(""))
  }

  override def containerId(kontainId: KontainId)(implicit transid: TransactionId): Future[ContainerId] = {
    // We container id is the kontain id.
    return Future.successful(ContainerId(kontainId.asString))
  }

  override def run(image: String, args: Seq[String])(implicit transid: TransactionId): Future[KontainId] = {
    // TODO:
    ???
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

  override def importImage(image: String)(implicit transid: TransactionId): Future[Boolean] = {
    // TODO:
    Future.successful(true)
  }

  override def rm(kontainId: KontainId)(implicit transid: TransactionId): Future[Unit] = {
    runDockerCmd(Seq("rm", kontainId.asString), Duration.Zero).map(_ => ())
  }

  override def stop(kontainId: KontainId)(implicit transid: TransactionId): Future[Unit] = {
    runDockerCmd(Seq("stop", kontainId.asString), Duration.Zero).map(_ => ())
  }

  protected def runDockerCmd(args: Seq[String], timeout: Duration)(implicit transid: TransactionId): Future[String] = {
    runCmd("docker", args, timeout)
  }
}
