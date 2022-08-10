package HighLevelServerApi

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.server.Route
import akka.stream.Materializer

import scala.concurrent.ExecutionContextExecutor

//Objectives:
//Familiarize with high-level server Api (Routing DSL)
//      -Routing DSL => Akka HTTP provides a flexible routing DSL for elegantly defining RESTful web services.
//       It picks up where the low-level API leaves off and offers much of the higher-level functionality
//       of typical web servers or frameworks, like deconstruction of URIs, content negotiation or static content serving.
//We Will Discuss in particular{Routes and Directives}
object HighLevelIntro extends App {
  implicit val actorsystem: ActorSystem = ActorSystem("LowLevelServerApi")
  implicit val materialize: Materializer = Materializer(actorsystem)
  implicit val executionContext: ExecutionContextExecutor = actorsystem.dispatcher
  //Directives
  /*A “Directive” is a small building block used for creating arbitrarily complex route structures.
  Akka HTTP already pre-defines a large number of directives and you can easily construct your own:*/
  //What Directives Do:
  //A directive can do one or more of the following:
  //    -Transform the incoming RequestContext before passing it on to its inner route (i.e. modify the request)
  //    -Filter the RequestContext according to some logic, i.e. only pass on certain requests and reject others
  //    -Extract values from the RequestContext and make them available to its inner route as “extractions”
  //    -Chain some logic into the RouteResult future transformation chain (i.e. modify the response or rejection)
  //    -Complete the request
  //This means a Directive completely wraps the functionality of its inner route and can apply arbitrarily complex transformations,
  //both (or either) on the request and on the response side.
  //importing Directives

  import akka.http.scaladsl.server.Directives._

  val simpleRoute: Route /*RequestContext => Future[RouteResult]*/ =
    path("home") //directive that filters http request
    { //directive that decides how's send back http response
      complete(StatusCodes.OK)
    }
  val pathGetRoute = path("home") {
    get {
      complete(StatusCodes.OK)
    }
  }
  //spinning a server
  Http().newServerAt("localhost", 8088).bindFlow(simpleRoute)
  //in browser http://localhost:8088/home
  Http().newServerAt("localhost", 8089).bindFlow(pathGetRoute)
  // http-prompt http://localhost:8089/home then get => OK
  //----------------------------------------------------------------
  //In MicroServices Architecture often have multiple paths and multiple end points
  //   -In this case we chain directives with ~
  val chainRoute = {
    path("endpoint") {
      get {
        complete(StatusCodes.OK)
      } ~ /*inside chaining*/
        post {
          complete(StatusCodes.Forbidden)
        }
    } ~ /*outside chaining with another path*/
      path("home") {
        get {
          complete(
            HttpEntity(
              ContentTypes.`text/html(UTF-8)`,
              """
                |<html>
                |<body>
                |<div>
                |<h1>
                |I Love AKKA
                |</h1>
                |</div>
                |</body>
                |</html>
                |""".stripMargin
            )
          )
        }
      }//routing tree
  }
  Http().newServerAt("localhost", 8087).bindFlow(chainRoute)
  //your browser http://localhost:8087/endpoint ==> ok
  //your browser http://localhost:8087/home ==> I Love AKkA
}
