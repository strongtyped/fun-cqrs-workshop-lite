package model.write

import io.funcqrs.behavior._
import io.funcqrs._
import io.funcqrs.behavior.handlers._
import util.Lists
import model._
import services._

import scala.concurrent.ExecutionContext.Implicits.global

sealed trait Order {

  def number: OrderNumber

  def id: OrderNumber = number
}

case class EmptyOrder(number: OrderNumber) extends Order {

  /**
    * Possible actions on an EmptyOrder are:
    * - add a first item
    * - cancel order
    */
  def possibleActions(stockService: StockService) =
    addFirstItem(stockService) ++ cancel

  def addFirstItem(stockService: StockService) =
    Order.actions
      .commandHandler {
        eventually.OneEvent {
          case AddItem(itemId, name, price) =>
            stockService.reserveItem(itemId).map { _ =>
              ItemWasAdded(number, itemId, name, price)
            }
        }
      }
      .eventHandler {
        case ItemWasAdded(_, itemId, name, price) =>
          NonEmptyOrder(number, List(Item(itemId, name, price)))
      }

  def cancel =
    Order.actions
      .commandHandler {
        // notice that an EmptyOrder doesn't have to cancel any reservation
        OneEvent {
          case CancelOrder => OrderWasCancelled(number)
        }
      }
      .eventHandler {
        case _: OrderWasCancelled => CancelledOrder(number)
      }

}

// TODO: think about it...
// Question - why NonEmptyOrder is the only variation that has Items?
case class NonEmptyOrder(number: OrderNumber, items: List[Item] = List.empty) extends Order {

  lazy val totalAmount = items.map(_.price).sum

  /**
    * Possible actions on a NonEmptyOrder are:
    * - add an item
    * - remove an item
    * - execute order
    * - cancel order
    */
  def possibleActions(stockService: StockService, billingService: BillingService) =
    addItems(stockService) ++
      removeItems ++
      pay(stockService, billingService) ++
      cancel

  /**
    * Command and Event handlers for adding an item to the [[Order]].
    * Depends on [[StockService]] to reserve Item on stock
    */
  def addItems(stockService: StockService) =
    Order.actions
      .commandHandler {
        eventually.OneEvent {
          case cmd: AddItem =>
            // AddItem requires a 'remote' call and returns a Future
            stockService.reserveItem(cmd.itemId).map { _ =>
              ItemWasAdded(number, cmd.itemId, cmd.name, cmd.price)
            }
        }
      }
      .eventHandler {
        case evt: ItemWasAdded =>
          val item = Item(evt.itemId, evt.name, evt.price)
          copy(items = item :: items)
      }

  /**
    * Command and Event handlers for removing an item from the [[Order]].
    */
  def removeItems =
    Order.actions
      .commandHandler {
        maybe.OneEvent {
          case cmd: RemoveItem =>
            // TODO: what about the item reservation?
            // if there is an item for this code, emit event
            // otherwise do nothing
            items.find(_.itemId == cmd.itemId).map { _ =>
              ItemWasRemoved(number, cmd.itemId)
            }
        }
      }
      .eventHandler {
        case evt: ItemWasRemoved =>
          val updatedItems = Lists.removeFirst(items)(_.itemId == evt.itemId)

          // is order empty now? go back to start
          if (updatedItems.isEmpty) EmptyOrder(number)
          else copy(items = updatedItems)

      }

  /**
    * Command and Event handlers for removing an item from the [[Order]].
    */
  def cancel =
    Order.actions
      .commandHandler {
        // TODO: what about the reservations?
        OneEvent {
          case CancelOrder => OrderWasCancelled(number)
        }
      }
      .eventHandler {
        case _: OrderWasCancelled => CancelledOrder(number)
      }

  /**
    * Command and Event handlers for paying the [[Order]].
    */
  def pay(reservationService: StockService, billingService: BillingService) =
    Order.actions
      .commandHandler {
        eventually.OneEvent {
          case cmd: PayOrder =>
            billingService
              .makePayment(cmd.accountNumber, number.value, totalAmount)
              .map { _ =>
                OrderWasPayed(number, cmd.accountNumber)
              }
        }
      }
      .eventHandler {
        case evt: OrderWasPayed => PayedOrder(number)
      }
}

case class PayedOrder(number: OrderNumber) extends Order {

  /** end-of-life, must reject all commands */
  def rejectAllCommands =
    Order.actions
      .rejectCommand {
        case anyCommand => new CommandException(s"Order [${number.value}] is already payed ")
      }
}

case class CancelledOrder(number: OrderNumber) extends Order {

  /** end-of-life, must reject all commands */
  def rejectAllCommands =
    Order.actions
      .rejectCommand {
        case anyCommand => new CommandException(s"Order [${number.value}] was cancelled ")
      }
}

object Order extends Types[Order] {

  val tag = Tags.aggregateTag("order")

  type Id      = OrderNumber
  type Command = OrderCommand
  type Event   = OrderEvent

  /** factory actions to bootstrap aggregate */
  def createActions(number: OrderNumber, stockService: StockService) =
    actions
      .commandHandler {
        OneEvent {
          case CreateOrder => OrderWasCreated(number)
        }
      }
      .eventHandler {
        case OrderWasCreated(_) => EmptyOrder(number)
      }

  def behavior(number: OrderNumber, stockService: StockService, billingService: BillingService) = {

    Behavior
      .first {
        // the initial behavior that triggers the creation of an order
        createActions(number, stockService)
      }
      .andThen {
        case order: EmptyOrder    => order.possibleActions(stockService)
        case order: NonEmptyOrder => order.possibleActions(stockService, billingService)

        // game over, no more commands
        case payed: PayedOrder         => payed.rejectAllCommands
        case cancelled: CancelledOrder => cancelled.rejectAllCommands
      }
  }
}

sealed trait OrderCommand

case object CreateOrder extends OrderCommand

case class AddItem(itemId: ItemId, name: String, price: Double) extends OrderCommand

case class RemoveItem(itemId: ItemId) extends OrderCommand

case object CancelOrder extends OrderCommand

case class PayOrder(accountNumber: AccountNumber) extends OrderCommand

sealed trait OrderEvent {
  def number: OrderNumber
}

/*
  Events are all named using the simple past tense in passive voice
  (eg: OrderWasPayed), to avoid confusion with the ADT names (eg: PayedOrder).
  Otherwise we will have PayedOrder ADT and OrderPayed event!
 */
case class OrderWasCreated(number: OrderNumber) extends OrderEvent

case class ItemWasAdded(number: OrderNumber, itemId: ItemId, name: String, price: Double) extends OrderEvent

case class ItemWasRemoved(number: OrderNumber, itemId: ItemId) extends OrderEvent

case class OrderWasCancelled(number: OrderNumber) extends OrderEvent

case class OrderWasPayed(number: OrderNumber, accountNumber: AccountNumber) extends OrderEvent
