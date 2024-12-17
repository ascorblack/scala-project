package app

import config.*
import db_client.{PsqlClient, *}
import endpoints.Endpoints
import sttp.model.Method
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.interceptor.cors.{CORSConfig, CORSInterceptor}
import sttp.tapir.server.netty.{FutureRoute, NettyFutureServer, NettyFutureServerInterpreter, NettyFutureServerOptions}
import sttp.tapir.swagger.bundle.SwaggerInterpreter

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

object ServerApp extends App {
  val serverConfig = AppConfig.webServerConfig

  val psqlClient: PsqlClient = PsqlClient.make
  val imageStorage: ImageStorage = ImageStorageImpl(psqlClient = psqlClient, storagePath = serverConfig.imagesPath)

  val serverEndpoints: List[ServerEndpoint[Any, Future]] = Endpoints.serverEndpoints(imageStorage, psqlClient)

  val pureEndpoints = Endpoints.allEndpoints

  val swaggerEndpoints: List[ServerEndpoint[Any, Future]] =
    SwaggerInterpreter().fromEndpoints[Future](pureEndpoints, "My App", "1.0")

  val serverOptions = NettyFutureServerOptions.customiseInterceptors
    .corsInterceptor(
      CORSInterceptor.customOrThrow(
        CORSConfig.default
          .allowAllHeaders
          .allowAllMethods
          .allowAllOrigins
          .allowMethods(Method.GET, Method.POST, Method.PUT, Method.DELETE, Method.OPTIONS)
      )
    )
    .options

  val interpreter = NettyFutureServerInterpreter(serverOptions)

  val swaggerRoute: FutureRoute = interpreter.toRoute(swaggerEndpoints)

  val apiRoutes: FutureRoute = interpreter.toRoute(serverEndpoints)

  NettyFutureServer(serverOptions)
    .host(serverConfig.host)
    .port(serverConfig.port)
    .addRoute(swaggerRoute)
    .addRoute(apiRoutes)
    .start()

}
