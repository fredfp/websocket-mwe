package controllers

import javax.inject._

import akka.actor.{Actor, ActorRef, ActorSystem, PoisonPill, Props, Status}
import akka.stream.Materializer
import play.api.libs.streams.ActorFlow
import play.api.mvc._

/**
  * This controller creates an `Action` to handle HTTP requests to the
  * application's home page.
  */
@Singleton
class HomeController @Inject()(implicit system: ActorSystem, mat: Materializer) extends Controller {

  /**
    * Create an Action to render an HTML page with a welcome message.
    * The configuration in the `routes` file means that this method
    * will be called when the application receives a `GET` request with
    * a path of `/`.
    */
  def index = Action { r =>
    Ok(views.html.index("Your new application is ready.", r))
  }

  def webSocketWithActorBad = WebSocket.accept[String, String] { r =>
    ActorFlow.actorRef(out => StreamActor.props(false, out, mat))
  }

  def webSocketWithActorGood = WebSocket.accept[String, String] { r =>
    ActorFlow.actorRef(out => StreamActor.props(true, out, mat))
  }

  def fixedWebSocketWithActorBad = WebSocket.accept[String, String] { r =>
    MyActorFlow.actorRef(out => StreamActor.props(false, out, mat))
  }

  def fixedWebSocketWithActorGood = WebSocket.accept[String, String] { r =>
    MyActorFlow.actorRef(out => StreamActor.props(true, out, mat))
  }
}

object StreamActor {
  def props(good: Boolean, out: ActorRef, materializer: Materializer): Props = Props(StreamActor(good, out, materializer))
}

case class StreamActor(good: Boolean, out: ActorRef, materializer: Materializer)
  extends Actor {

  implicit val mat = materializer

  case object DoIt
  self ! DoIt

  override def receive: Receive = {
    case DoIt =>
      out ! "1"
      out ! "2"
      out ! "3"
      if (good)
        out ! Status.Success(())
      else
        self ! PoisonPill
  }

  override def postStop ={
    println("actor stopped")
    super.postStop
  }
}

object MyActorFlow {
  import akka.actor._
  import akka.stream.{ Materializer, OverflowStrategy }
  import akka.stream.scaladsl.{ Sink, Keep, Source, Flow }

  /**
   * Create a flow that is handled by an actor.
   *
   * Messages can be sent downstream by sending them to the actor passed into the props function.  This actor meets
   * the contract of the actor returned by [[akka.stream.scaladsl.Source.actorRef]].
   *
   * The props function should return the props for an actor to handle the flow. This actor will be created using the
   * passed in [[akka.actor.ActorRefFactory]]. Each message received will be sent to the actor - there is no back pressure,
   * if the actor is unable to process the messages, they will queue up in the actors mailbox. The upstream can be
   * cancelled by the actor terminating itself.
   *
   * @param props A function that creates the props for actor to handle the flow.
   * @param bufferSize The maximum number of elements to buffer.
   * @param overflowStrategy The strategy for how to handle a buffer overflow.
   */
  def actorRef[In, Out](props: ActorRef => Props, bufferSize: Int = 16, overflowStrategy: OverflowStrategy = OverflowStrategy.dropNew)(implicit factory: ActorRefFactory, mat: Materializer): Flow[In, Out, _] = {

    val (outActor, publisher) = Source.actorRef[Out](bufferSize, overflowStrategy)
      .toMat(Sink.asPublisher(false))(Keep.both).run()

    Flow.fromSinkAndSource(
      Sink.actorRef(factory.actorOf(Props(new Actor {
        val flowActor = context.watch(context.actorOf(props(outActor), "flowActor"))

        def receive = {
          case Status.Success(_) | Status.Failure(_) => flowActor ! PoisonPill
          case Terminated(_)                         => outActor ! Status.Success(())
          case other                                 => flowActor ! other
        }

        override def supervisorStrategy = OneForOneStrategy() {
          case _ => SupervisorStrategy.Stop
        }
      })), Status.Success(())),
      Source.fromPublisher(publisher)
    )
  }
}
