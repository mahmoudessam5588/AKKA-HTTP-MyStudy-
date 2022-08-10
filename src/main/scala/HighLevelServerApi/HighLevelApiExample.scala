package HighLevelServerApi

import LowLevelServerApi.ZPhone.{AllZPhones, CreateZPhone, FindZPhone, FindZPhoneInStock}
import LowLevelServerApi.{ZPhone, ZPhoneStoreJsonProtocol}
import akka.actor.{ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ContentTypes, HttpEntity}
import akka.http.scaladsl.server.Directives._
import akka.pattern.ask
import akka.stream.Materializer
import akka.util.Timeout
import spray.json._

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.language.postfixOps

object HighLevelApiExample extends App with ZPhoneStoreJsonProtocol {
  implicit val actorsystem: ActorSystem = ActorSystem("HighLevelExample")
  implicit val materialize: Materializer = Materializer(actorsystem)
  implicit val executionContext: ExecutionContextExecutor = actorsystem.dispatcher

  import LowLevelServerApi.ZPhoneDB

  /*
      GET /api/zphone fetches ALL the zphones in the store
      GET /api/zphone?id=x fetches the zphone with id X
      GET /api/zphone/X fetches zphone with id X
      GET /api/zphone/inventory?inStock=true
     */
  /*Setup*/
  val zPhoneDB = actorsystem.actorOf(Props[ZPhoneDB](), "zPhoneDB")
  val zPhonesList = List(
    ZPhone(
      "EmirateEl-Magrib&SahelAfrica",
      "ManaretElMorabiteen",
      5000000
    )
    ,
    ZPhone(
      "EmirateElIndo-Malay",
      "Malacca",
      6000000
    )
    ,
    ZPhone(
      "EmirateKhorasan-ElKubra",
      "Bukhara",
      9000000
    ),
    ZPhone(
      "EmirateGazeratElArab&Sham&Iraq",
      "ManaretElHuda",
      7000000
    ),
    ZPhone(
      "EmirateEl-Nile&KarnAfrica",
      "El-Wadi",
      0
    )
  )
  zPhonesList.foreach { zPhone => zPhoneDB ! CreateZPhone(zPhone) }
  implicit val timeout: Timeout = Timeout(2 seconds)
  val zPhoneServerRoute =
    path("api" / "zphone") {
      parameter(Symbol("id").as[Int]) {
        zPhoneId =>
          get {
            val zPhoneFuture: Future[Option[ZPhone]] =
              (zPhoneDB ? FindZPhone(zPhoneId)).mapTo[Option[ZPhone]]
            val zPhoneEntity = zPhoneFuture.map {
              zPhoneOption =>
                HttpEntity(
                  ContentTypes.`application/json`,
                  zPhoneOption.toJson.prettyPrint
                )
            }
            complete(zPhoneEntity)
          }
      } ~
        get {
          val zPhoneFuture: Future[List[ZPhone]] =
            (zPhoneDB ? AllZPhones).mapTo[List[ZPhone]]
          val entityFuture = zPhoneFuture.map {
            zPhoneOption =>
              HttpEntity(
                ContentTypes.`application/json`,
                zPhoneOption.toJson.prettyPrint
              )
          }
          complete(entityFuture)
        }
    } ~
      path("api" / "zphone" / IntNumber) {
        zPhoneId =>
          get {
            val zPhoneFuture: Future[Option[ZPhone]] =
              (zPhoneDB ? FindZPhone(zPhoneId)).mapTo[Option[ZPhone]]
            val zPhoneEntity = zPhoneFuture.map {
              zPhoneOption =>
                HttpEntity(
                  ContentTypes.`application/json`,
                  zPhoneOption.toJson.prettyPrint
                )
            }
            complete(zPhoneEntity)
          }
      } ~
      path("api" / "zphone" / "inventory") {
        get {
          parameter(Symbol("inStock").as[Boolean]) {
            inStock =>
              val futureZphoneInStock: Future[List[ZPhone]] =
                (zPhoneDB ? FindZPhoneInStock(inStock)).mapTo[List[ZPhone]]
              val zphoneInStockEntity = futureZphoneInStock.map {
                zphoneOption =>
                  HttpEntity(
                    ContentTypes.`application/json`, zphoneOption.toJson.prettyPrint
                  )
              }
              complete(zphoneInStockEntity)
          }
        }
      }

  Http().newServerAt("localhost", 8080).bindFlow(zPhoneServerRoute)

  //
  def toHttpEntity(payload: String) = HttpEntity(ContentTypes.`application/json`, payload)

  val simplifiedZPhoneServerRoute =
    (pathPrefix("api" / "zphone") & get) {
      path("inventory") {
        parameter(Symbol("inStock").as[Boolean]) { inStock =>
          complete(
            (zPhoneDB ? FindZPhoneInStock(inStock))
              .mapTo[List[ZPhone]]
              .map(_.toJson.prettyPrint)
              .map(toHttpEntity)
          )
        }
      } ~
        (path(IntNumber) | parameter(Symbol("id").as[Int])) { zPhoneId =>
          complete(
            (zPhoneDB ? FindZPhone(zPhoneId))
              .mapTo[Option[ZPhone]]
              .map(_.toJson.prettyPrint)
              .map(toHttpEntity)
          )
        } ~
        pathEndOrSingleSlash {
          complete(
            (zPhoneDB ? AllZPhones)
              .mapTo[List[ZPhone]]
              .map(_.toJson.prettyPrint)
              .map(toHttpEntity)
          )
        }
    }
  Http().newServerAt("localhost", 8099).bindFlow(simplifiedZPhoneServerRoute)
}

