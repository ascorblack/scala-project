package endpoints

import sttp.tapir._
import sttp.tapir.json.circe._
import sttp.tapir.generic.auto._
import io.circe.generic.auto._
import dbClient._
import errors.errors
import sttp.tapir.server.ServerEndpoint
import sttp.model.{HeaderNames, StatusCode}
import cats.effect.IO
import app.ApiResponse.{ErrorResponse, JSONResponse}
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

  private def readHtmlFromResource(resourcePath: String): IO[String] = IO {
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

  private val getImagesWithoutTagsEndpoint: PublicEndpoint[Unit, JSONResponse[ErrorResponse], JSONResponse[List[DownloadedBase64Image]], Any] =
    endpoint.get
      .in("api" / "v1" / "imagesWithoutTags")
      .out(jsonBody[JSONResponse[List[DownloadedBase64Image]]])
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


  private val searchByTagEndpoint: PublicEndpoint[String, JSONResponse[ErrorResponse], JSONResponse[List[ImageObject]], Any] =
    endpoint.get
      .in("api" / "v1" / "search")
      .in(query[String]("query"))
      .out(jsonBody[JSONResponse[List[ImageObject]]])
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
    deleteImageTagsEndpoint,
    getImagesWithoutTagsEndpoint,
    searchByTagEndpoint
  )

  def serverEndpoints(imageStorage: ImageStorage, psqlClient: PsqlClient): List[ServerEndpoint[Any, IO]] = {

    val indexServer: ServerEndpoint[Any, IO] =
      indexEndpoint.serverLogic { _ =>
        readHtmlFromResource("static/index.html").map(Right(_))
      }

    val getImagesServer: ServerEndpoint[Any, IO] =
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
        }
      }

    val insertImageServer: ServerEndpoint[Any, IO] =
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
        }
      }

    val findImageServer: ServerEndpoint[Any, IO] =
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
        }
      }

    val getImageServer: ServerEndpoint[Any, IO] =
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
        }
      }

    val deleteImageServer: ServerEndpoint[Any, IO] =
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
        }
      }

    val getTagsServer: ServerEndpoint[Any, IO] =
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
        }
      }

    val getImagesWithoutTagsServer: ServerEndpoint[Any, IO] =
      getImagesWithoutTagsEndpoint.serverLogic { _ =>
        imageStorage.getImagesWithoutTags.map {
          case Right(images) =>
            Right(JSONResponse[List[DownloadedBase64Image]](
              status = StatusCode.Ok.code,
              message = "success",
              data = Some(images),
              error = None
            ))
          case Left(err) =>
            Left(handleError(err))
        }
      }

    val insertImageTagServer: ServerEndpoint[Any, IO] =
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
        }
      }

    val deleteImageTagsServer: ServerEndpoint[Any, IO] =
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
        }
      }


    val searchByTagServer: ServerEndpoint[Any, IO] =
      searchByTagEndpoint.serverLogic { query =>
        imageStorage.searchByTag(query).map {
          case Right(result) =>
            Right(JSONResponse[List[ImageObject]](
              status = StatusCode.Ok.code,
              message = "success",
              data = Some(result),
              error = None
            ))
          case Left(err) =>
            Left(handleError(err))
        }
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
      deleteImageTagsServer,
      getImagesWithoutTagsServer,
      searchByTagServer
    )
  }
}
