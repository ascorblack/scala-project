package dbClient

import java.io.File
import scala.util.{Either, Left, Right, Try, Success, Failure}
import cats.effect.IO

import java.nio.file.{Files, Paths, StandardOpenOption}
import errors.errors.{AppError, FailedToDeleteFile, FailedToSaveFile, ImageNotFound}

import java.util.{Base64, UUID}

trait ImageStorage {
  def getImages: IO[Either[AppError, List[ImageObject]]]

  def insertImage(uploadedImage: UploadedImage): IO[Either[AppError, ImageObject]]

  def findImage(imageName: String): IO[Either[AppError, Option[ImageObject]]]

  def getImageById(imageId: Int): IO[Either[AppError, Option[ImageObject]]]

  def getTags(imageId: Int): IO[Either[AppError, List[ImageTagObject]]]

  def getImagesWithoutTags: IO[Either[AppError, List[DownloadedBase64Image]]]

  def insertImageTags(tagItems: List[TagItem]): IO[Either[AppError, List[ImageTagObject]]]

  def deleteImage(imageId: Int): IO[Either[AppError, Int]]

  def deleteImageTags(imageId: Int): IO[Either[AppError, Int]]

  def searchByTag(query: String): IO[Either[AppError, List[ImageObject]]]
}

class ImageStorageImpl(psqlClient: PsqlClient, storagePath: String) extends ImageStorage {

  override def getImages: IO[Either[AppError, List[ImageObject]]] = {
    psqlClient.getImages
  }

  override def insertImage(uploadedImage: UploadedImage): IO[Either[AppError, ImageObject]] = {
    saveFile(uploadedImage).flatMap {
      case Left(error) => IO.pure(Left(error))
      case Right(savedPath) =>
        val imageItem = ImageItem(
          image_name = uploadedImage.image_name,
          image_path = savedPath
        )
        psqlClient.insertImage(imageItem)
    }
  }

  override def findImage(imageName: String): IO[Either[AppError, Option[ImageObject]]] = {
    psqlClient.findImage(imageName)
  }

  override def getImageById(imageId: Int): IO[Either[AppError, Option[ImageObject]]] = {
    psqlClient.getImageById(imageId)
  }

  override def getTags(imageId: Int): IO[Either[AppError, List[ImageTagObject]]] = {
    psqlClient.getTags(imageId)
  }

  override def getImagesWithoutTags: IO[Either[AppError, List[DownloadedBase64Image]]] = {
    psqlClient.getImagesWithoutTags.map {
      case Left(error) => Left(error)
      case Right(images) =>
        val uploadedImages = images.map { image =>
          Try {
            val bytes = Files.readAllBytes(Paths.get(image.image_path))
            Base64.getEncoder.encodeToString(bytes)
          } match {
            case Success(base64) => DownloadedBase64Image(image.id, image.image_name, base64)
            case Failure(_) => DownloadedBase64Image(image.id, image.image_name, "")
          }
        }
        Right(uploadedImages)
    }
  }

  override def insertImageTags(tagItems: List[TagItem]): IO[Either[AppError, List[ImageTagObject]]] = {
    val result = psqlClient.insertImageTags(tagItems)
    println(result)
    result
  }

  override def deleteImage(imageId: Int): IO[Either[AppError, Int]] = {
    getImageById(imageId).flatMap {
      case Left(error) =>
        IO.pure(Left(error))
      case Right(Some(image)) =>
        IO {
          val file = new File(image.image_path)
          Either.cond(
            !file.exists() || file.delete(),
            (),
            FailedToDeleteFile(s"Failed to delete file at path: ${image.image_path}")
          )
        }.flatMap {
          case Left(fileError) => IO.pure(Left(fileError))
          case Right(_) => psqlClient.deleteImage(imageId)
        }
      case Right(None) =>
        IO.pure(Left(ImageNotFound(imageId)))
    }
  }

  override def deleteImageTags(imageId: Int): IO[Either[AppError, Int]] = {
    psqlClient.deleteImageTags(imageId)
  }

  private def saveFile(file: UploadedImage): IO[Either[AppError, String]] = IO {
    try {
      val dirPath = Paths.get(storagePath)
      Files.createDirectories(dirPath)

      val allowedExtensions = Set(".png", ".jpg", ".jpeg")

      val finalImageName = allowedExtensions.find(ext => file.image_name.toLowerCase.endsWith(ext))
        .fold(file.image_name + ".png")(_ => file.image_name)

      val uniqueFileName = s"${UUID.randomUUID().toString}_$finalImageName"
      val targetPath = dirPath.resolve(uniqueFileName)

      val base64Data = Option(file.base64Image)
        .filter(_.contains(","))
        .map(_.split(",").last)
        .getOrElse(file.base64Image)

      val decodedBytes = Base64.getDecoder.decode(base64Data)

      Files.write(
        targetPath,
        decodedBytes,
        StandardOpenOption.CREATE,
        StandardOpenOption.TRUNCATE_EXISTING
      )

      Right(targetPath.toString)
    } catch {
      case ex: Exception =>
        Left(FailedToSaveFile(s"Failed to save file: ${ex.getMessage}"))
    }
  }

  override def searchByTag(query: String): IO[Either[AppError, List[ImageObject]]] = {
    psqlClient.searchByTag(query)
  }
}
