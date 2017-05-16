package backend

import akka.actor.ActorSystem
import io.funcqrs.akka.backend.AkkaBackend
import model.OrderNumber
import model.write.Order

class OrderAkkaBackend(val actorSystem: ActorSystem) extends AkkaBackend {

  def orderRef(number: OrderNumber) = this.aggregateRef[Order].forId(number)
}
