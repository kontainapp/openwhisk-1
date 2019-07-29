package org.apache.openwhisk.core.containerpool.kontain

import akka.actor.ActorSystem
import org.apache.openwhisk.common.TransactionId
import org.apache.openwhisk.core.containerpool.Container
import org.apache.openwhisk.core.containerpool.logging.{LogStore, LogStoreProvider}
import org.apache.openwhisk.core.database.UserContext
import org.apache.openwhisk.core.entity.{ActivationLogs, ExecutableWhiskAction, Identity, WhiskActivation}

import scala.concurrent.Future

object KontainLogStoreProvider extends LogStoreProvider {
  override def instance(actorSystem: ActorSystem): LogStore = new KontainLogStore()
}

class KontainLogStore extends LogStore {

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
  override def collectLogs(transid: TransactionId,
                           user: Identity,
                           activation: WhiskActivation,
                           container: Container,
                           action: ExecutableWhiskAction): Future[ActivationLogs] = {
    Future.successful(ActivationLogs(Seq("kontain not implemented").toVector))
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
