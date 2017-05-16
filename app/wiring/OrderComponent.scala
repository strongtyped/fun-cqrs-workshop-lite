package wiring

import akka.actor.ActorSystem
import backend._
import com.softwaremill.macwire._
import io.funcqrs.config.Api._
import model.OrderNumber
import model.write.Order
import services.{ BillingService, ProductService, StockService }
import io.funcqrs.config.AkkaOffsetPersistenceStrategy

trait OrderComponent extends RemoteServiceComponent {

  def actorSystem: ActorSystem

  lazy val orderBackend = wire[OrderAkkaBackend]

  def stockService: StockService

  def billingService: BillingService

  def productService: ProductService

  lazy val orderDetailsRepo = wire[OrderDetailsRepo]

  orderBackend
    .configure {
      aggregate { number: OrderNumber =>
        // behavior get's services injected
        Order.behavior(number, stockService, billingService)
      }
    }
    .configure {
      projection(
        projection       = wire[OrderDetailsProjection],
        publisherFactory = EventPublisher.orderEvents(actorSystem)
      )
      .withOffsetPersistenceStrategy(
        // quick-win: saves offset as an event in akka-persistence journal
        AkkaOffsetPersistenceStrategy.offsetAsLong(actorSystem, "order-details")
      )
    }
}
