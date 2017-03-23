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
}
