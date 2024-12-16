package app

import config.*
import db_client.{PsqlClient, *}
import endpoints.Endpoints
import sttp.tapir.server.netty.{FutureRoute, NettyFutureServer, NettyFutureServerInterpreter}
import sttp.tapir.swagger.bundle.SwaggerInterpreter

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

object ServerApp extends App {
  val psqlClient: PsqlClient = PsqlClient.make

  val serverEndpoints: List[sttp.tapir.server.ServerEndpoint[Any, Future]] = Endpoints.serverEndpoints(psqlClient)

  val pureEndpoints = Endpoints.allEndpoints

  val swaggerEndpoints: List[sttp.tapir.server.ServerEndpoint[Any, Future]] =
    SwaggerInterpreter().fromEndpoints[Future](pureEndpoints, "My App", "1.0")

  val swaggerRoute: FutureRoute = NettyFutureServerInterpreter().toRoute(swaggerEndpoints)

  val apiRoutes: FutureRoute = NettyFutureServerInterpreter().toRoute(serverEndpoints)

  val serverConfig = AppConfig.webServerConfig

  NettyFutureServer()
    .host(serverConfig.host)
    .port(serverConfig.port)
    .addRoute(swaggerRoute)
    .addRoute(apiRoutes)
    .start()

  println(s"Server is running on http://${serverConfig.host}:${serverConfig.port}")
  println("Press ENTER to stop the server.")

  scala.io.StdIn.readLine()

  println("Shutting down the server...")
}
