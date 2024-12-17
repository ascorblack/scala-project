package endpoints

import sttp.tapir.*
import sttp.tapir.json.circe.*
import sttp.tapir.generic.auto.*
import io.circe.generic.auto.*
import db_client.*
import errors.errors
import sttp.tapir.server.ServerEndpoint
import sttp.model.{HeaderNames, StatusCode}

import scala.concurrent.Future
import app.ApiResponse.{ErrorResponse, JSONResponse}

import scala.concurrent.ExecutionContext.Implicits.global
import cats.effect.unsafe.implicits.global

import scala.io.Source

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
        error = Some(ErrorResponse(e.message))
      )
    case errors.ImageNotFound(msg) =>
      JSONResponse(
        status = StatusCode.NotFound.code,
        message = "Not Found",
        data = None,
        error = Some(ErrorResponse(e.message))
      )
    case errors.FailedToSaveFile(_) =>
      JSONResponse(
        status = StatusCode.InternalServerError.code,
        message = "Internal Server Error",
        data = None,
        error = Some(ErrorResponse(e.message))
      )
    case errors.FailedToDeleteFile(_) =>
      JSONResponse(
        status = StatusCode.InternalServerError.code,
        message = "Internal Server Error",
        data = None,
        error = Some(ErrorResponse(e.message))
      )
  }

  private def readHtmlFromResource(resourcePath: String): Future[String] = Future {
    val source = Source.fromResource(resourcePath)
    try source.mkString finally source.close()
  }

  private val indexEndpoint: PublicEndpoint[Unit, Unit, String, Any] =
    endpoint.get
      .in("ui")
      .out(header(HeaderNames.ContentType, "text/html"))
      .out(stringBody)

  private val getImagesEndpoint: PublicEndpoint[Unit, JSONResponse[ErrorResponse], JSONResponse[List[ImageObject]], Any] =
    endpoint.get
      .in("api" / "v1" / "images")
      .out(jsonBody[JSONResponse[List[ImageObject]]])
      .errorOut(
        jsonBody[JSONResponse[ErrorResponse]]
      )

  private val insertImageEndpoint: PublicEndpoint[UploadedImage, JSONResponse[ErrorResponse], JSONResponse[ImageObject], Any] =
    endpoint.post
      .in("api" / "v1" / "image")
      .in(jsonBody[UploadedImage])
      .out(jsonBody[JSONResponse[ImageObject]])
      .errorOut(
        jsonBody[JSONResponse[ErrorResponse]]
      )

  private val findImageEndpoint: PublicEndpoint[String, JSONResponse[ErrorResponse], JSONResponse[ImageObject], Any] =
    endpoint.get
      .in("api" / "v1" / "imageFind")
      .in(query[String]("imageName"))
      .out(jsonBody[JSONResponse[ImageObject]])
      .errorOut(
        jsonBody[JSONResponse[ErrorResponse]]
      )

  private val getImageEndpoint: PublicEndpoint[Int, JSONResponse[ErrorResponse], JSONResponse[ImageObject], Any] =
    endpoint.get
      .in("api" / "v1" / "image" / path[Int]("imageId"))
      .out(jsonBody[JSONResponse[ImageObject]])
      .errorOut(
        jsonBody[JSONResponse[ErrorResponse]]
      )

  private val deleteImageEndpoint: PublicEndpoint[Int, JSONResponse[ErrorResponse], JSONResponse[Int], Any] =
    endpoint.delete
      .in("api" / "v1" / "image" / path[Int]("imageId"))
      .out(jsonBody[JSONResponse[Int]])
      .errorOut(
        jsonBody[JSONResponse[ErrorResponse]]
      )

  private val getTagsEndpoint: PublicEndpoint[Int, JSONResponse[ErrorResponse], JSONResponse[List[ImageTagObject]], Any] =
    endpoint.get
      .in("api" / "v1" / "imageTags")
      .in(query[Int]("imageId"))
      .out(jsonBody[JSONResponse[List[ImageTagObject]]])
      .errorOut(
        jsonBody[JSONResponse[ErrorResponse]]
      )

  private val insertImageTagEndpoint: PublicEndpoint[List[TagItem], JSONResponse[ErrorResponse], JSONResponse[List[ImageTagObject]], Any] =
    endpoint.post
      .in("api" / "v1" / "imageTags")
      .in(jsonBody[List[TagItem]])
      .out(jsonBody[JSONResponse[List[ImageTagObject]]])
      .errorOut(
        jsonBody[JSONResponse[ErrorResponse]]
      )

  private val deleteImageTagsEndpoint: PublicEndpoint[Int, JSONResponse[ErrorResponse], JSONResponse[Int], Any] =
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
    getImageEndpoint,
    deleteImageEndpoint,
    getTagsEndpoint,
    insertImageTagEndpoint,
    deleteImageTagsEndpoint
  )

  def serverEndpoints(imageStorage: ImageStorage, psqlClient: PsqlClient): List[ServerEndpoint[Any, Future]] = {

    val indexServer: ServerEndpoint[Any, Future] =
      indexEndpoint.serverLogic { _ =>
        readHtmlFromResource("static/index.html").map(Right(_))
      }

    val getImagesServer: ServerEndpoint[Any, Future] =
      getImagesEndpoint.serverLogic { _ =>
        imageStorage.getImages.map {
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
      insertImageEndpoint.serverLogic { uploadedImage =>
        imageStorage.insertImage(uploadedImage).map {
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
        imageStorage.findImage(imageName).map {
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

    val getImageServer: ServerEndpoint[Any, Future] =
      getImageEndpoint.serverLogic { imageId =>
        imageStorage.getImageById(imageId).map {
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
              message = s"Image with id '$imageId' not found",
              data = None,
              error = Some(ErrorResponse(s"Image with id '$imageId' not found"))
            ))
          case Left(err) =>
            Left(handleError(err))
        }.unsafeToFuture()
      }

    val deleteImageServer: ServerEndpoint[Any, Future] =
      deleteImageEndpoint.serverLogic { imageId =>
        imageStorage.deleteImage(imageId).map {
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
        imageStorage.getTags(imageId).map {
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
        imageStorage.insertImageTags(tagItem).map {
          case Right(tag) =>
            Right(JSONResponse[List[ImageTagObject]](
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
        imageStorage.deleteImageTags(imageId).map {
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
      indexServer,
      getImagesServer,
      insertImageServer,
      findImageServer,
      getImageServer,
      deleteImageServer,
      getTagsServer,
      insertImageTagServer,
      deleteImageTagsServer
    )
  }
}
