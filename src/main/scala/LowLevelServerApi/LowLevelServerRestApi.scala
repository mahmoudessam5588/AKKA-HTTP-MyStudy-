package LowLevelServerApi

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives
import akka.pattern.ask
import akka.stream.Materializer
import akka.util.Timeout
import spray.json.DefaultJsonProtocol

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.language.postfixOps
//step 1
import spray.json._

//JSON Restful Api
//Handle Json payload in request/response with spray.json
final case class ZPhone(madeIn: String, model: String, quantity: Int = 0)

object ZPhone {
  case object AllZPhones

  case class FindZPhone(id: Int)

  case class CreateZPhone(zPhone: ZPhone)

  case class ZPhoneManufactured(id: Int)

  case class AddQuantity(id: Int, quantity: Int)

  case class FindZPhoneInStock(inStock: Boolean)
}

class ZPhoneDB extends Actor with ActorLogging {

  import ZPhone._

  var zPhones: Map[Int, ZPhone] = Map()
  var currentZPhonesId: Int = 0

  override def receive: Receive = {

    case AllZPhones => log.info("Searching for all ZPhones"); sender() ! zPhones.values.toList

    case FindZPhone(id) => log.info(s"Searching ZPhones by id: $id"); sender() ! zPhones.get(id)

    case CreateZPhone(zPhone) =>
      log.info(s"Adding guitar $zPhone with id $currentZPhonesId")
      zPhones = zPhones + (currentZPhonesId -> zPhone)
      sender() ! ZPhoneManufactured(currentZPhonesId)
      currentZPhonesId += 1


    case AddQuantity(id, quantity) =>
      log.info(s"Trying to add $quantity items for ZPhone $id")
      val zPhone: Option[ZPhone] = zPhones.get(id)
      val newZPhone: Option[ZPhone] =
        zPhone.map { case ZPhone(madeIn, model, q) => ZPhone(madeIn, model, q + quantity) }
      newZPhone.foreach { zPhone => zPhones = zPhones + (id -> zPhone) }
      sender() ! newZPhone

    case FindZPhoneInStock(inStock) =>
      log.info(s"Searching for all ZPhones ${if (inStock) "in" else "out of"} stock")
      if (inStock) sender() ! zPhones.values.filter(_.quantity > 0)
      else sender() ! zPhones.values.filter(_.quantity == 0)
  }
}

//Step 2
trait ZPhoneStoreJsonProtocol extends SprayJsonSupport with DefaultJsonProtocol {
  // step 3
  implicit val ZPhoneFormat: RootJsonFormat[ZPhone] =
    jsonFormat3(ZPhone.apply)
}

//extending the trait
object LowLevelServerRestApi extends App with ZPhoneStoreJsonProtocol with Directives {
  implicit val actorsystem: ActorSystem = ActorSystem("LowLevelServerApi")
  implicit val materialize: Materializer = Materializer(actorsystem)
  implicit val executionContext: ExecutionContextExecutor = actorsystem.dispatcher

  import ZPhone._

  /*
  There Kind Of Interaction Is An Actual Data Nit HTML AnyMore0
  We Will Need Serialization To JSON And Pass That As An Http Entity
  Also In Client Side We Need To Deserialize The Client String To Understandable Data
  {{{JSon~>Marshalling}}}
    - GET on localhost:8080/api/zPhones => ALL the guitars in the store
    - GET on localhost:8080/api/zPhone?id=X => fetches the guitar associated with id X
    - POST on localhost:8080/api/zPhone => insert the guitar into the store
   */
  //Marshalling
  val zPhoneModelManaretElMorabiteen = ZPhone(
    madeIn = "EmirateGreaterMoroccoAndWestAfricanCoast",
    model = "ManaretElMorabiteen",
    quantity = 5000000
  )
  println(zPhoneModelManaretElMorabiteen.toJson.prettyPrint)
  //prints
  /*{
    "madeIn": "EmirateGreaterMoroccoAndWestAfricanCoast",
    "model": "ManaretElMorabiteen",
    "quantity": 5000000
  }*/
  //unMarshalling
  val zPhoneModelManaretElMorabiteenString =
  """
    |{
    |    "madeIn": "EmirateGreaterMoroccoAndWestAfricanCoast",
    |    "model": "ManaretElMorabiteen",
    |    "quantity": 5000000
    |}
    |""".stripMargin
  println(zPhoneModelManaretElMorabiteenString.parseJson.convertTo[ZPhone])
  //Prints
  //ZPhone(EmirateGreaterMoroccoAndWestAfricanCoast,ManaretElMorabiteen,5000000)
  /*For Server Code We Are Going To Use Asynchronous Handler
  * Because We Are Interacting With An Actor As A database
  * so Whenever you need to interact with external resource use future otherwise
  * the response time will be really bad*/
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

  /**
   * Exercise: enhance the ZPhone case class with a quantity field, by default 0
   * - GET to /api/zphone/inventory?inStock=true/false which returns the zPhones in stock as a JSON
   * - POST to /api/zphones/inventory?id=X&quantity=Y which adds Y ZPhones to the stock for ZPhone with id X
   *
   */
  zPhonesList.foreach { zPhone => zPhoneDB ! CreateZPhone(zPhone) }
  implicit val timeOut: Timeout = Timeout(2 seconds)

  def getZPhone(query: Query): Future[HttpResponse] = {
    val zPhoneId = query.get("id").map(_.toInt) //returns an Option[String] ==> toInt
    zPhoneId match {
      case None => Future(HttpResponse(StatusCodes.NotFound))
      case Some(id: Int) =>
        val zPhoneFuture: Future[Option[ZPhone]] = (zPhoneDB ? FindZPhone(id)).mapTo[Option[ZPhone]]
        zPhoneFuture.map {
          case None => HttpResponse(StatusCodes.NotFound)
          case Some(zPhone) => HttpResponse(entity = HttpEntity(
            ContentTypes.`application/json`, zPhone.toJson.prettyPrint
          ))
        }
    }
  }

  val requestAsyncHandler: HttpRequest => Future[HttpResponse] = {
    case HttpRequest(HttpMethods.POST, uri@Uri.Path("/api/zphone/inventory"), _, _, _) =>
      val query = uri.query()
      val zPhoneId: Option[Int] = query.get("id").map(_.toInt)
      val zPhoneQuantity: Option[Int] = query.get("quantity").map(_.toInt)
      //for comprehension
      val validZphoneResponseFuture: Option[Future[HttpResponse]] = for {
        id <- zPhoneId
        quantity <- zPhoneQuantity
      } yield {
        val newGuitarFuture: Future[Option[ZPhone]] =
          (zPhoneDB ? AddQuantity(id, quantity)).mapTo[Option[ZPhone]]
        newGuitarFuture.map(_ => HttpResponse(StatusCodes.OK))
      }
      validZphoneResponseFuture.getOrElse(Future(HttpResponse(StatusCodes.BadRequest)))

    case HttpRequest(HttpMethods.GET, uri@Uri.Path("/api/zphone/inventory"), _, _, _)=>
      val query: Query = uri.query()
      val inStockOption: Option[Boolean] = query.get("inStock").map(_.toBoolean)
      inStockOption match {
        case Some(inStock) =>
          val zPhonesFuture:Future[List[ZPhone]] =
            (zPhoneDB ? FindZPhoneInStock(inStock)).mapTo[List[ZPhone]]
          zPhonesFuture.map{
            zPhones =>
              HttpResponse(entity = HttpEntity(
                ContentTypes.`application/json`,
                zPhones.toJson.prettyPrint
              ))
          }
        case None => Future(HttpResponse(StatusCodes.BadRequest))
      }


    case HttpRequest(HttpMethods.GET, uri@Uri.Path("/api/zphone"), _, _, _) =>
      //using actor ask pattern to retrieve the data from zPhoneDB
      /*query parameter handling here
      * example:
        localhost:8080/api/endpoint?param1=value1&param2=value2*/
      val query = uri.query() //query object <=> map[String,String]
      if (query.isEmpty) {
        val zPhoneFuture: Future[List[ZPhone]] = (zPhoneDB ? AllZPhones).mapTo[List[ZPhone]]
        zPhoneFuture.map {
          zPhones =>
            HttpResponse(
              entity = HttpEntity(
                ContentTypes.`application/json`, zPhones.toJson.prettyPrint
              )
            )
        }
      } else {
        //fetch zPhone associated to zPhone id
        //localhost:8080/api/zphone?id=45
        getZPhone(query)
      }
    case HttpRequest(HttpMethods.POST, Uri.Path("api/zphone"), _, entity, _) =>
      //entities are a Source[ByteString]
      val strictEntityFuture: Future[HttpEntity.Strict] = entity.toStrict(3 seconds)
      strictEntityFuture.flatMap {
        strictEntity =>
          //parsing Json
          val zPhoneJsonString: String = strictEntity.data.utf8String
          val zphone: ZPhone = zPhoneJsonString.parseJson.convertTo[ZPhone]
          //Returning Future  And Map It To Http Response
          val zPhoneCreatedFuture = (zPhoneDB ? CreateZPhone(zphone)).mapTo[ZPhoneManufactured]
          zPhoneCreatedFuture.map { _ => HttpResponse(StatusCodes.OK) }
      }

    case r: HttpRequest =>
      r.discardEntityBytes()
      Future {
        HttpResponse(StatusCodes.NotFound)

      }
  }
  Http().newServerAt("localhost", 8080).bind(requestAsyncHandler)
}
