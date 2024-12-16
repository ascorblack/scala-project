package endpoints

import sttp.tapir._
import sttp.tapir.json.circe._
import sttp.tapir.generic.auto._
import io.circe.generic.auto._
import db_client._
import errors.errors
import db_client.Encoders._
import sttp.tapir.server.ServerEndpoint
import sttp.model.StatusCode
import scala.concurrent.Future
import app.ApiResponse.{JSONResponse, ErrorResponse}
import scala.concurrent.ExecutionContext.Implicits.global
import cats.effect.unsafe.implicits.global

object Endpoints {

  private def handleError(e: errors.AppError): JSONResponse[ErrorResponse] = e match {
    case errors.ImageAlreadyExists(name) =>
      JSONResponse(
        status = StatusCode.Conflict.code,
        message = "Conflict",
        data = None,
        error = Some(ErrorResponse(e.message))
      )
    case errors.DatabaseError(msg) =>
      JSONResponse(
        status = StatusCode.InternalServerError.code,
        message = "Internal Server Error",
        data = None,
        error = Some(ErrorResponse(msg))
      )
  }

  val getImagesEndpoint: PublicEndpoint[Unit, JSONResponse[ErrorResponse], JSONResponse[List[ImageObject]], Any] =
    endpoint.get
      .in("api" / "v1" / "images")
      .out(jsonBody[JSONResponse[List[ImageObject]]])
      .errorOut(
        jsonBody[JSONResponse[ErrorResponse]]
      )

  val insertImageEndpoint: PublicEndpoint[ImageItem, JSONResponse[ErrorResponse], JSONResponse[ImageObject], Any] =
    endpoint.post
      .in("api" / "v1" / "image")
      .in(jsonBody[ImageItem])
      .out(jsonBody[JSONResponse[ImageObject]])
      .errorOut(
        jsonBody[JSONResponse[ErrorResponse]]
      )

  val findImageEndpoint: PublicEndpoint[String, JSONResponse[ErrorResponse], JSONResponse[ImageObject], Any] =
    endpoint.get
      .in("api" / "v1" / "image")
      .in(query[String]("imageName"))
      .out(jsonBody[JSONResponse[ImageObject]])
      .errorOut(
        jsonBody[JSONResponse[ErrorResponse]]
      )

  val deleteImageEndpoint: PublicEndpoint[Int, JSONResponse[ErrorResponse], JSONResponse[Int], Any] =
    endpoint.delete
      .in("api" / "v1" / "image" / path[Int]("imageId"))
      .out(jsonBody[JSONResponse[Int]])
      .errorOut(
        jsonBody[JSONResponse[ErrorResponse]]
      )

  val getTagsEndpoint: PublicEndpoint[Int, JSONResponse[ErrorResponse], JSONResponse[List[ImageTagObject]], Any] =
    endpoint.get
      .in("api" / "v1" / "imageTags")
      .in(query[Int]("imageId"))
      .out(jsonBody[JSONResponse[List[ImageTagObject]]])
      .errorOut(
        jsonBody[JSONResponse[ErrorResponse]]
      )

  val insertImageTagEndpoint: PublicEndpoint[TagItem, JSONResponse[ErrorResponse], JSONResponse[ImageTagObject], Any] =
    endpoint.post
      .in("api" / "v1" / "imageTags")
      .in(jsonBody[TagItem])
      .out(jsonBody[JSONResponse[ImageTagObject]])
      .errorOut(
        jsonBody[JSONResponse[ErrorResponse]]
      )

  val deleteImageTagsEndpoint: PublicEndpoint[Int, JSONResponse[ErrorResponse], JSONResponse[Int], Any] =
    endpoint.delete
      .in("api" / "v1" / "imagesTags")
      .in(query[Int]("imageId"))
      .out(jsonBody[JSONResponse[Int]])
      .errorOut(
        jsonBody[JSONResponse[ErrorResponse]]
      )

  val allEndpoints: List[AnyEndpoint] = List(
    getImagesEndpoint,
    insertImageEndpoint,
    findImageEndpoint,
    deleteImageEndpoint,
    getTagsEndpoint,
    insertImageTagEndpoint,
    deleteImageTagsEndpoint
  )

  def serverEndpoints(psqlClient: PsqlClient): List[ServerEndpoint[Any, Future]] = {

    val getImagesServer: ServerEndpoint[Any, Future] =
      getImagesEndpoint.serverLogic { _ =>
        psqlClient.getImages.map {
          case Right(images) if images.nonEmpty =>
            Right(JSONResponse[List[ImageObject]](
              status = StatusCode.Ok.code,
              message = "success",
              data = Some(images),
              error = None
            ))
          case Right(_) =>
            Left(JSONResponse[ErrorResponse](
              status = StatusCode.NotFound.code,
              message = "No images found",
              data = None,
              error = Some(ErrorResponse("No images found"))
            ))
          case Left(err) =>
            Left(handleError(err))
        }.unsafeToFuture()
      }

    val insertImageServer: ServerEndpoint[Any, Future] =
      insertImageEndpoint.serverLogic { imageItem =>
        psqlClient.insertImage(imageItem).map {
          case Right(image) =>
            Right(JSONResponse[ImageObject](
              status = StatusCode.Ok.code,
              message = "success",
              data = Some(image),
              error = None
            ))
          case Left(err) =>
            Left(handleError(err))
        }.unsafeToFuture()
      }

    val findImageServer: ServerEndpoint[Any, Future] =
      findImageEndpoint.serverLogic { imageName =>
        psqlClient.findImage(imageName).map {
          case Right(Some(image)) =>
            Right(JSONResponse[ImageObject](
              status = StatusCode.Ok.code,
              message = "success",
              data = Some(image),
              error = None
            ))
          case Right(None) =>
            Left(JSONResponse[ErrorResponse](
              status = StatusCode.NotFound.code,
              message = s"Image '$imageName' not found",
              data = None,
              error = Some(ErrorResponse(s"Image '$imageName' not found"))
            ))
          case Left(err) =>
            Left(handleError(err))
        }.unsafeToFuture()
      }

    val deleteImageServer: ServerEndpoint[Any, Future] =
      deleteImageEndpoint.serverLogic { imageId =>
        psqlClient.deleteImage(imageId).map {
          case Right(count) if count > 0 =>
            Right(JSONResponse[Int](
              status = StatusCode.Ok.code,
              message = "success",
              data = Some(count),
              error = None
            ))
          case Right(_) =>
            Left(JSONResponse[ErrorResponse](
              status = StatusCode.NotFound.code,
              message = s"Image with ID $imageId not found",
              data = None,
              error = Some(ErrorResponse(s"Image with ID $imageId not found"))
            ))
          case Left(err) =>
            Left(handleError(err))
        }.unsafeToFuture()
      }

    val getTagsServer: ServerEndpoint[Any, Future] =
      getTagsEndpoint.serverLogic { imageId =>
        psqlClient.getTags(imageId).map {
          case Right(tags) =>
            Right(JSONResponse[List[ImageTagObject]](
              status = StatusCode.Ok.code,
              message = "success",
              data = Some(tags),
              error = None
            ))
          case Left(err) =>
            Left(handleError(err))
        }.unsafeToFuture()
      }

    val insertImageTagServer: ServerEndpoint[Any, Future] =
      insertImageTagEndpoint.serverLogic { tagItem =>
        psqlClient.insertImageTag(tagItem).map {
          case Right(tag) =>
            Right(JSONResponse[ImageTagObject](
              status = StatusCode.Ok.code,
              message = "success",
              data = Some(tag),
              error = None
            ))
          case Left(err) =>
            Left(handleError(err))
        }.unsafeToFuture()
      }

    val deleteImageTagsServer: ServerEndpoint[Any, Future] =
      deleteImageTagsEndpoint.serverLogic { imageId =>
        psqlClient.deleteImageTags(imageId).map {
          case Right(count) =>
            Right(JSONResponse[Int](
              status = StatusCode.Ok.code,
              message = "success",
              data = Some(count),
              error = None
            ))
          case Left(err) =>
            Left(handleError(err))
        }.unsafeToFuture()
      }

    List(
      getImagesServer,
      insertImageServer,
      findImageServer,
      deleteImageServer,
      getTagsServer,
      insertImageTagServer,
      deleteImageTagsServer
    )
  }
}
