package controllers

import javax.inject._

import akka.actor.{Actor, ActorRef, ActorSystem, PoisonPill, Props}
import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source}
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

  def webSocketWithActor = WebSocket.accept[String, String] { r =>
    ActorFlow.actorRef(out => StreamActor.props(out, mat))
  }

  def webSocketWithBridgedActor = WebSocket.accept[String, String] { r =>
    ActorFlow.actorRef(out => StreamActor.props(out, mat))
  }

}

object StreamActor {
  def props(out: ActorRef, materializer: Materializer): Props = Props(StreamActor(out, materializer))
}

case class StreamActor(out: ActorRef, materializer: Materializer)
  extends Actor {

  implicit val mat = materializer

  override def receive: Receive = {
    case s: String => Source.fromIterator(() => (1 to 1000).toIterator)
      .map(i => i + "")
      .runWith(Sink.actorRef(out, onCompleteMessage = PoisonPill))
  }

  override def postStop() = {
    // Sending a PoisonPill to "out" stops this actor, is this the desired behaviour?
    println("Closing websocket handler")
  }

}

object StreamActorBridge {
  def props(out: ActorRef, materializer: Materializer): Props = Props(StreamActor(out, materializer))
}

case class StreamActorBridge(out: ActorRef, materializer: Materializer)
  extends Actor {

  implicit val mat = materializer

  override def receive: Receive = {
    case s: String =>
      context.become(bridge)
      Source.fromIterator(() => (1 to 1000).toIterator)
      .map(i => i + "")
      .runWith(Sink.actorRef(self, onCompleteMessage = PoisonPill))
  }

  def bridge: Receive = {
    case s: String => out ! s
  }

  override def postStop() = {
    // Sending a PoisonPill to "out" stops this actor, is this the desired behaviour?
    println("Closing websocket handler")
  }

}