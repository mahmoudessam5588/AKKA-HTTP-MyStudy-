package LowLevelServerApi

import akka.actor.ActorSystem
import akka.http.scaladsl.{ ConnectionContext, Http, HttpsConnectionContext }
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpMethods, HttpRequest, HttpResponse, StatusCodes, Uri}
import akka.stream.Materializer
import java.io.InputStream
import java.security.{KeyStore, SecureRandom}
import javax.net.ssl.{KeyManagerFactory, SSLContext, TrustManagerFactory}
import scala.concurrent.{ExecutionContextExecutor, Future}

//Setting Https {Encryption all the data between client and the server}
//using certificate in production if you need to expose akka http server to the public
//you will need https certificate signed by a certificate authority so browsers and other clients
//can trust your server.
object HttpsContext{
  //Step One defining key Store object
  val ks: KeyStore = KeyStore.getInstance("PKCS12")
  val keyStoreFile: InputStream = getClass.getClassLoader.getResourceAsStream("keystore.pkcs12")
  //never store password explicitly you should fetch them from secure database
  //or secure source to fetch the password
  val password: Array[Char] = "akka-https".toCharArray
  ks.load(keyStoreFile, password)

  //Step Two Initialize a Key Manager
  //PKI = public key infrastructure
  val keyManagerFactory: KeyManagerFactory = KeyManagerFactory.getInstance("SunX509")
  keyManagerFactory.init(ks, password)

  //Step Three Initialize a Trust Manager
  val trustManagerFactory: TrustManagerFactory = TrustManagerFactory.getInstance("SunX509")
  trustManagerFactory.init(ks)

  //Step Four Initialize an SSL context  {Secure Socket Layer}
  val sslContext: SSLContext = SSLContext.getInstance("TLS") //Transport Layer Security
  sslContext.init(keyManagerFactory.getKeyManagers, trustManagerFactory.getTrustManagers, new SecureRandom)

  //Step Five Return HTTP Connection Context
  val httpsConnectionContext: HttpsConnectionContext = ConnectionContext.httpsServer(sslContext)
}
object HTTPSLowLevelApi extends App {
  import HttpsContext._
  implicit val actorsystem: ActorSystem = ActorSystem("LowLevelServerApi")
  implicit val materialize: Materializer = Materializer(actorsystem)
  implicit val executionContext: ExecutionContextExecutor = actorsystem.dispatcher


  //request handler
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
  // you can run both HTTP and HTTPS in the same application as follows:
  Http().newServerAt("localhost",8443).enableHttps(httpsConnectionContext).bind(requestAsyncHandler)
  Http().newServerAt("localhost", 8445).bind(requestAsyncHandler)

  //in your browser
  //https://localhost:8443/home
  //http://localhost:8445/home
  //this is self signed certificate proceed to localhost (unsafe)

}
