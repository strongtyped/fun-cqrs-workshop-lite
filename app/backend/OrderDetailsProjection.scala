package backend

import io.funcqrs.projections.Projection
import model.read._
import model.write.{OrderEvent, _}
import util.Lists

import scala.concurrent.Future

class OrderDetailsProjection(orderDetailsRepo: OrderDetailsRepo) extends Projection[OrderEvent] {

  def handleEvent = sync.HandleEvent {
    case _ => ()
  }

  def created(evt: OrderWasCreated): Unit = {}

  def itemAdded(evt: ItemWasAdded): Unit = {}

  def itemRemoved(evt: ItemWasRemoved): Unit = {}

  def cancelled(evt: OrderWasCancelled): Unit = {}

  def payed(evt: OrderWasPayed): Unit = {}

}
