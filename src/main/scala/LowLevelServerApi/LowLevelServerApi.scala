package LowLevelServerApi

import akka.Done
import akka.actor.ActorSystem
import akka.http.scaladsl.Http.IncomingConnection
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.headers.Location
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpMethods, HttpRequest, HttpResponse, StatusCodes, Uri}
import akka.stream.Materializer
import akka.stream.scaladsl.{Flow, Sink, Source}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.language.postfixOps
import scala.util.{Failure, Success}
//Gives us a lot of control on how to process requests and responses
//but still a lot of things done by hand and a lot of boilerplate involved

//Objectives:
// - set up our first Akka Http server
// - understand basic principles of Akka Http

//What is Akka Http:
//  -suite of libraries
// -focus on HTTP Integration of an application
// -Designed for both servers and clients
// -Based on Akka Actors And Streams

//Akka Http Is Not:
// -A Framework
// -It's an Un-Opinionated suite of libraries for providing HP functionality
// -And You Still Can Write Your Rest Web Api And Microservices with it
// -Akka frameworks like play and lagom which are opinionated

//Akka Http Strength:
// -stream-based , with backpressure for free and go all the way to TCP level
// -multiple API levels for control vs ease of use
//Akka Http cire concepts:
// -Http Request, ANd Http response
// -Http Entity
// -Marshalling

//Akka Http server:
//receives Http requests and send Http responses in Multitude of ways:
// -synchronously via HttpRequest => HttpResponse
// -asynchronously via HttpRequest => Future[HttpResponse]
// -asynchronously via akka streams with FLow[HttpRequest,HttpResponse,_]
// {{all the above turns into flows sooner or later}}

//Akka http Under The Hood:
// -the server receives HttpRequests(transparently)
// -the requests go through the flow we write ==> our focus is here
// -the resulting responses are served back (transparently)
object LowLevelServerApi extends App {
  implicit val actorsystem: ActorSystem = ActorSystem("LowLevelServerApi")
  implicit val materialize: Materializer = Materializer(actorsystem)
  implicit val executionContext: ExecutionContextExecutor = actorsystem.dispatcher
  //                   CORE SERVER API

  /*On the connection level Akka HTTP offers basically the same kind of interface as Working with streaming IO:
  A socket binding is represented as a stream of incoming connections. The application pulls connections from
  this stream source and, for each of them,
  provides a Flow[HttpRequest, HttpResponse, _] to “translate” requests into responses.
  Apart from regarding a socket bound on the server-side as a Source[IncomingConnection, _]
  and each connection as a Source[HttpRequest, _] with a Sink[HttpResponse, _] the stream abstraction
  is also present inside a single HTTP message: The entities of HTTP requests and responses are generally modeled
  as a Source[ByteString, _]. See also the HTTP Model for more information on how HTTP messages are represented in Akka HTTP.*/
  //----------------------------------------------------------------------------------------------
  //create first server
  val serverHttpSource: Source[IncomingConnection, Future[Http.ServerBinding]] =
  Http().newServerAt("localhost", 8000).connectionSource()
  //send to a sink
  val connectorSink: Sink[IncomingConnection, Future[Done]] =
    Sink.foreach[IncomingConnection] {
      connection => println(s"Accepted Connection from ${connection.remoteAddress}")
    }
  //we need to materialize the graph to run the connection server
  val serverBindingFuture: Future[Http.ServerBinding] = serverHttpSource.to(connectorSink).run()
  serverBindingFuture.onComplete {
    case Success(binding) =>
      println("Server binding successful.")
      /*Asynchronously triggers the unbinding of the port that was bound by the materialization of the connections Source
      Note that unbinding does NOT terminate existing connections. Unbinding only means that the server will not accept new connections,
      and existing connections are allowed to still perform request/response cycles.
      This can be useful when one wants to let clients finish whichever work they have remaining,
      while signalling them using some other way that the server will be terminating soon
      -- e.g. by sending such information in the still being sent out responses, such that the client can switch to a new server when it is ready.
      Alternatively you may want to use the terminate method which unbinds and performs some level of gracefully replying with
      The produced Future is fulfilled when the unbinding has been completed.
      Note: rather than unbinding explicitly you can also use addToCoordinatedShutdown to add this task to Akka's coordinated shutdown.*/
      //binding.unbind()
      binding.terminate(10 seconds)
    case Failure(ex) => println(s"Server binding failed: $ex")
  }
  //Making Connections Useful:
  //Request Response Cycle
  /*When a new connection has been accepted it will be published as an Http.
  IncomingConnection which consists of the remote address and methods to provide a Flow[HttpRequest, HttpResponse, _]
  to handle requests coming in over this connection.
  Requests are handled by calling one of the handleWithXXX methods with a handler, which can either be:
  a Flow[HttpRequest, HttpResponse, _] for handleWith,
  a function HttpRequest => HttpResponse for handleWithSyncHandler,
  a function HttpRequest => Future[HttpResponse] for handleWithAsyncHandler.*/

  /*
      Method 1: synchronously serve HTTP responses
  */
  val requestHandler: HttpRequest => HttpResponse = {
    /*Signature
    * final class HttpRequest(
      val method:     HttpMethod,
      val uri:        Uri,
      val headers:    immutable.Seq[HttpHeader],
      val attributes: Map[AttributeKey[_], _],
      val entity:     RequestEntity,
      val protocol:   HttpProtocol)
      * }extends jm.HttpRequest with HttpMessage*/
    case HttpRequest(HttpMethods.GET, _, _, _, _) =>
      /*
      final class HttpResponse(
        val status:     StatusCodes,
        val headers:    immutable.Seq[HttpHeader],
        val attributes: Map[AttributeKey[_], _],
        val entity:     ResponseEntity,
        val protocol:   HttpProtocol)
        extends jm.HttpResponse with HttpMessage
      */
      HttpResponse(
        StatusCodes.OK, // HTTP 200
        entity = HttpEntity(
          ContentTypes.`text/html(UTF-8)`,
          """
            |<html>
            | <body>
            |   Hello from Akka HTTP!
            | </body>
            |</html>
          """.stripMargin
        )
      )
    case r: HttpRequest =>
      r.discardEntityBytes() // important to drain incoming HTTP Entity stream
      HttpResponse(StatusCodes.NotFound, entity = HttpEntity(
        ContentTypes.`text/html(UTF-8)`,
        """
          |<html>
          | <body>
          | <h1>
          |   Not Found
          |</h1>
          | </body>
          |</html>
        """.stripMargin
      ))
  }
  val httpSyncHandlerResult: Sink[IncomingConnection, Future[Done]] =
    Sink.foreach[IncomingConnection] {
      connection => connection handleWithSyncHandler requestHandler
    }
  val bindingFuture: Future[Done] = Http()
    .newServerAt("localhost", 8080)
    .connectionSource()
    .runWith(httpSyncHandlerResult)
  //short hand
  Http().newServerAt("localhost", 8090).bindSync(requestHandler)
  //curl localhost:8080
  //or try in your browser
  //----------------------------------------------------------------------------------------
  /*
    Method 2: serve back HTTP response ASYNCHRONOUSLY
    {Returning A Future of HttpResponse}
  */
  val requestAsyncHandler: HttpRequest => Future[HttpResponse] = {
    case HttpRequest(HttpMethods.GET, Uri.Path("/home"), _, _, _) =>
      Future(HttpResponse(
        StatusCodes.OK, // HTTP 200
        entity = HttpEntity(
          ContentTypes.`text/html(UTF-8)`,
          """
            |<html>
            | <body>
            |   Hello from Akka HTTP!
            | </body>
            |</html>
          """.stripMargin
        )
      ))
    case r: HttpRequest =>
      r.discardEntityBytes() // important to drain incoming HTTP Entity stream
      Future(HttpResponse(StatusCodes.NotFound, entity = HttpEntity(
        ContentTypes.`text/html(UTF-8)`,
        """
          |<html>
          | <body>
          | <h1>
          |   Not Found
          |</h1>
          | </body>
          |</html>
        """.stripMargin
      )))
  }
  val httpAsyncHandlerResult: Sink[IncomingConnection, Future[Done]] =
    Sink.foreach[IncomingConnection] {
      connection => connection handleWithAsyncHandler requestAsyncHandler
    }
  //stream based version
  Http().newServerAt("localhost", 8081).connectionSource().runWith(httpAsyncHandlerResult)
  //shorter hand
  Http().newServerAt("localhost", 8081).bind(requestAsyncHandler)
  //curl localhost: 8081/home
  //or try on browser
  //----------------------------------------------------------------------------------------------------
  /*
      Method 3: async via Akka streams
  */
  val streamBasedRequestResponseHandler: Flow[HttpRequest, HttpResponse, _] = {
    Flow[HttpRequest].map {
      case HttpRequest(HttpMethods.GET, Uri.Path("/home"), _, _, _) => // method, URI, HTTP headers, content and the protocol (HTTP1.1/HTTP2.0)
        HttpResponse(
          StatusCodes.OK, // HTTP 200
          entity = HttpEntity(
            ContentTypes.`text/html(UTF-8)`,
            """
              |<html>
              | <body>
              |   Hello from Akka HTTP!
              | </body>
              |</html>
          """.stripMargin
          )
        )
      case request: HttpRequest =>
        request.discardEntityBytes()
        HttpResponse(
          StatusCodes.NotFound, // 404
          entity = HttpEntity(
            ContentTypes.`text/html(UTF-8)`,
            """
              |<html>
              | <body>
              |   OOPS! The resource can't be found.
              | </body>
              |</html>
          """.stripMargin
          )
        )
    }
  }
  //manual stream based
  Http()
    .newServerAt("localhost", 8082)
    .connectionSource()
    .runForeach(_.handleWith(streamBasedRequestResponseHandler))
  //shorter hand
  Http().newServerAt("localhost", 8082).bindFlow(streamBasedRequestResponseHandler)

  /**
   * Exercise: create your own HTTP server running on localhost on 8388, which replies
   *   - with a welcome message on the "front door" localhost:8388
   *   - with a proper HTML on localhost:8388/about
   *   - with a 404 message otherwise
   */
  val httpServerFlow: Flow[HttpRequest, HttpResponse, _] = {
    Flow[HttpRequest].map {
      case HttpRequest(HttpMethods.GET, Uri.Path("/"), _, _, _) =>
        HttpResponse(
          // status code OK (200) is default
          entity = HttpEntity(
            ContentTypes.`text/html(UTF-8)`,
            "Hello from the exercise front door!"
          )
        )
      case HttpRequest(HttpMethods.GET, Uri.Path("/about"), _, _, _) =>
        HttpResponse(
          // status code OK (200) is default
          entity = HttpEntity(
            ContentTypes.`text/html(UTF-8)`,
            """
              |<html>
              | <body>
              |   <div style="color: red">
              |     Hello from the about page!
              |   <div>
              | </body>
              |</html>
              """.stripMargin
          )
        )

      // path /search redirects to some other part of our website/webapp/microservice
      case HttpRequest(HttpMethods.GET, Uri.Path("/search"), _, _, _) =>
        HttpResponse(
          StatusCodes.Found,
          headers = List(Location("http://google.com"))
        )

      case request: HttpRequest =>
        request.discardEntityBytes()
        HttpResponse(
          StatusCodes.NotFound,
          entity = HttpEntity(
            ContentTypes.`text/html(UTF-8)`,
            "OOPS, you're in no man's land, sorry."
          )
        )
    }
  }
  //binding future as above for shutting down server
  val httpServerBindingFuture: Future[Http.ServerBinding] =
    Http().newServerAt("localhost", 8388).bindFlow(httpServerFlow)
  httpServerBindingFuture.flatMap(_.unbind()).onComplete(_=>actorsystem.terminate())
}
