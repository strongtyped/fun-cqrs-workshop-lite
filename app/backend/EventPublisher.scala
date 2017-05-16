package backend

import akka.NotUsed
import akka.actor.{ ActorContext, ActorSystem }
import akka.persistence.query.journal.leveldb.scaladsl.LeveldbReadJournal
import akka.persistence.query.{ EventEnvelope, PersistenceQuery }
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{ Sink, Source }
import io.funcqrs.Tag
import io.funcqrs.projections.PublisherFactory
import model.write.{ Order, OrderEvent }
import org.reactivestreams.Publisher

object EventPublisher {

  /**
    * Builds a [[Source]] of [[EventEnvelope]]s containing the [[Tag]]
    * and starting from the passed offset.
    *
    * @param offset - initial offset to start to read from
    * @return
    */
  def source(offset: Long, tag: Tag)(implicit actorSys: ActorSystem): Source[EventEnvelope, NotUsed] = {

    val readJournal =
      PersistenceQuery(actorSys)
        .readJournalFor[LeveldbReadJournal](LeveldbReadJournal.Identifier)

    readJournal.eventsByTag(tag.value, offset)
  }

  def orderEvents(implicit actorSys: ActorSystem) = {

    new PublisherFactory[Long, OrderEvent] {

      implicit val mat = ActorMaterializer()

      override def from(offset: Option[Long]): Publisher[(Long, OrderEvent)] = {

        val offsetNum = offset.getOrElse(0L)

        EventPublisher
          .source(offsetNum, Order.tag)
          .collect {
            case EventEnvelope(eventOffset, _, _, event: OrderEvent) =>
              (eventOffset, event)
          }
          .runWith(Sink.asPublisher(false))
      }

    }
  }

}
