package HighLevelServerApi

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpRequest, StatusCodes}
import akka.http.scaladsl.server.Route
import akka.stream.Materializer

import scala.concurrent.ExecutionContextExecutor
//Objectives:
//Familiarize With All Most Popular Directives
//Understand filtering,chaining,extraction and transformation
/*
* val simpleRoute: Route /*RequestContext => Future[RouteResult]*/ =
  path("home") //directive that filters http request
  { //directive that decides how's send back http response
    complete(StatusCodes.OK)
  }
  *RequestContex Contains:
    -the HttpRequest being handled
    -the actor system
    -the actor materializer
    -logging adapter
    -routing setting
 */
//directives-->creates{Routes}-->composing Routes-->creates{Routing Tree}
// ( -includes
//   -filtering and nesting
//   -Chaining with ~ operators
//   -Extracting Data)

//--------------------------------
//What Route Can Do With RequestContext:
// - complete it synchronously  with a response
// - complete it synchronously with Future(response)
// - handles it asynchronously by returning a Source(advanced)
// - reject it and pass it to next Route
// - fail it
object DirectivesBreakDown extends App {
  implicit val actorsystem: ActorSystem = ActorSystem("DirectiveBreakDown")
  implicit val materialize: Materializer = Materializer(actorsystem)
  implicit val executionContext: ExecutionContextExecutor = actorsystem.dispatcher

  import akka.http.scaladsl.server.Directives._

  /*Type One Filtering Directive*/
  val httpRouteMethod =
    post { //also there are get,put,patch,head,options
      complete(StatusCodes.Forbidden)
    }


  val simpleRoute = path("about") {
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
  }
  val completeRoute = path("api" / "other") {
    complete(StatusCodes.OK)
  }
  val pathEndRoute = pathEndOrSingleSlash { //localhost:8090 or localhost :8090/
    complete(StatusCodes.OK)
  }
  //-------------------------------------------------------------------------
  /*Type Two Extraction Directives:
  extract meaningful value out of request context*/
  //online store  GET /api/item/42
  val extractionRoute = {
    path("api" / "item" / IntNumber) {
      (number: Int) =>
        println(s"I've Got A Number In My Path $number")
        complete(StatusCodes.OK)
    }
  }
  Http().newServerAt("localhost", 8090).bindFlow(extractionRoute)
  // http GET http://localhost:8090/api/item/35  ==> ok
  //In Console I've Got A Number In My Path 35

  val multiExtractRoute = path("api" / "order" / IntNumber / IntNumber) {
    (id, inventory) =>
      println(s"id : $id & inventory: $inventory")
      complete(StatusCodes.OK)
  }
  Http().newServerAt("localhost", 8097).bindFlow(multiExtractRoute)
  //http GET http://localhost:8097/api/order/45/123 ===> ok
  //console  ==  http://localhost:8090/api/item/35
  val queryParamExtractionRoute = {
    // /api/item?id=5
    path("api" / "item") {
      parameter(Symbol("id").as[Int]) {
        (item: Int) =>
          println(s"Extracted ID $item")
          complete(StatusCodes.OK)
      }
    }
  }
  Http().newServerAt("localhost", 8067).bindFlow(queryParamExtractionRoute)
  //browser http://localhost:8067/api/item?id=5  ==> Ok
  //console Extracted ID 5
  //------------------------------------------------------------------
  //extraction the request from request context
  val extractRequestControl = {
    path("controlled") {
      extractRequest { //there are  a lot of extract methods provided
        (_: HttpRequest) => complete(StatusCodes.OK)
      }
    }
  }
  //-----------------------------------------------------------------
  /*Type Three Composite Directives */
  val simpleNestedRoute: Unit = {
    path("api" / "item") {
      get {
        complete(StatusCodes.OK)
      }
    }

    val simpleCompositeNestedRoute = (path("api" / "item") & get) {
      complete(StatusCodes.OK)
    }
  }
  val compactExtractRouteMethod = (path("controlled") & extractRequest & extractLog) {
    (request, log) =>
      log.info(s"got the http Request $request")
      complete(StatusCodes.OK)
  }
  //different path same result
  val dryRoute: Route = (path("about") | path("abouttUs")) {
    complete(StatusCodes.OK)
  }
  //one path one parameter extracting same amount of value
  val sameValueExtractor: Route =
    (path(IntNumber) | parameter(Symbol("id").as[Int])) {
      (id: Int) => complete(StatusCodes.OK)
    }
  //----------------------------------------------------------------
  /*
    Type four: "actionable" directives
   */

  val completeOkRoute = complete(StatusCodes.OK)

  val failedRoute =
    path("notSupported") {
      failWith(new RuntimeException("Unsupported!")) // completes with HTTP 500
    }

  val routeWithRejection =
    path("home") {
      reject
    } ~
      path("index") {
        completeOkRoute
      }

}

