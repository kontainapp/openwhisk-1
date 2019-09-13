package org.apache.openwhisk.core.containerpool.kontain

import akka.actor.ActorSystem
import org.apache.openwhisk.common.{AkkaLogging, Logging, TransactionId}
import org.apache.openwhisk.core.containerpool.logging.{LogStore, LogStoreProvider}
import org.apache.openwhisk.core.containerpool.{Container, ContainerId}
import org.apache.openwhisk.core.database.UserContext
import org.apache.openwhisk.core.entity.{ActivationLogs, ExecutableWhiskAction, Identity, WhiskActivation}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

object KontainLogStoreProvider extends LogStoreProvider {
  override def instance(actorSystem: ActorSystem): LogStore = {
    implicit val logger = new AkkaLogging(akka.event.Logging.getLogger(actorSystem, this))
    new KontainLogStore(actorSystem)
  }
}

class KontainLogStore(system: ActorSystem)(implicit log: Logging) extends LogStore {
  implicit val ec: ExecutionContext = system.dispatcher
  private val kontainCli = new ExtendedKontainCli()(system.dispatcher, system, log)

  /** Additional parameters to pass to container creation */
  override def containerParameters: Map[String, Set[String]] = Map()

  /**
   * Collect logs after the activation has finished.
   *
   * This method is called after an activation has finished. The logs gathered here are stored along the activation
   * record in the database.
   *
   * @param transid    transaction the activation ran in
   * @param user       the user who ran the activation
   * @param activation the activation record
   * @param container  container used by the activation
   * @param action     action that was activated
   * @return logs for the given activation
   */
  override def collectLogs(
    transid: TransactionId,
    user: Identity,
    activation: WhiskActivation,
    container: Container,
    action: ExecutableWhiskAction): Future[ActivationLogs] = {

    kontainCli
      .collectLogs(container.containerId)(transid)
      .map(log => ActivationLogs(Seq(log).toVector))
  }

  /**
   * Fetch relevant logs for the given activation from the store.
   *
   * This method is called when a user requests logs via the API.
   *
   * @param activation activation to fetch the logs for
   * @return the relevant logs
   */
  override def fetchLogs(activation: WhiskActivation, context: UserContext): Future[ActivationLogs] = {
    Future.successful(activation.logs)
  }
}

class ExtendedKontainCli()(implicit executionContext: ExecutionContext, as: ActorSystem, log: Logging)
    extends KontainClient {

  def collectLogs(containerId: ContainerId)(implicit transid: TransactionId): Future[String] = {
    log.info(this, s"collection logs ${containerId.asString}")
    runCmd(Seq("kontain", "container", "logs", containerId.asString), 5.seconds, "kontain_log")
  }
}
