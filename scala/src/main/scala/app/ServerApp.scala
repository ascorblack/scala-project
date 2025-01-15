package app

import cats.effect.{ExitCode, IO, IOApp}
import config.*
import dbClient.*
import endpoints.Endpoints
import org.http4s.implicits._
import org.http4s.server.middleware.CORS
import sttp.tapir.server.http4s.Http4sServerInterpreter
import sttp.tapir.swagger.bundle.SwaggerInterpreter
import org.http4s.blaze.server.BlazeServerBuilder
import cats.syntax.semigroupk._
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.*

object ServerApp extends IOApp {

  def run(args: List[String]): IO[ExitCode] = {
    val serverConfig = AppConfig.webServerConfig

    val psqlClient: PsqlClient = PsqlClient.make
    val imageStorage: ImageStorage = ImageStorageImpl(
      psqlClient = psqlClient,
      storagePath = serverConfig.imagesPath
    )

    val serverEndpoints: List[sttp.tapir.server.ServerEndpoint[Any, IO]] =
      Endpoints.serverEndpoints(imageStorage, psqlClient)

    val pureEndpoints = Endpoints.allEndpoints

    val swaggerEndpoints = SwaggerInterpreter().fromEndpoints[IO](pureEndpoints, "My App", "1.0")

    val apiRoutes = Http4sServerInterpreter[IO]().toRoutes(serverEndpoints)

    val swaggerRoutes = Http4sServerInterpreter[IO]().toRoutes(swaggerEndpoints)

    val allRoutes = (apiRoutes <+> swaggerRoutes).orNotFound

    val corsMiddleware = CORS.policy
      .withAllowOriginAll
      .withAllowMethodsAll
      .withAllowHeadersAll
      .withMaxAge(84600.seconds)

    val httpAppWithCors = corsMiddleware(allRoutes)

    BlazeServerBuilder[IO](ExecutionContext.global)
      .bindHttp(serverConfig.port, serverConfig.host)
      .withHttpApp(httpAppWithCors)
      .resource
      .use(_ => IO.never)
      .as(ExitCode.Success)
  }
}
