package db_client

import scala.concurrent.Future
import java.io.File
import scala.util.{Either, Left, Right}
import cats.effect.IO
import cats.syntax.all.*

import java.nio.file.{Files, Paths, StandardCopyOption, StandardOpenOption}
import errors.errors.{AppError, FailedToDeleteFile, FailedToSaveFile, ImageNotFound}
import sttp.model.Part

import java.util.{Base64, UUID}

trait ImageStorage {
  def getImages: IO[Either[AppError, List[ImageObject]]]
  def insertImage(uploadedImage: UploadedImage): IO[Either[AppError, ImageObject]]
  def findImage(imageName: String): IO[Either[AppError, Option[ImageObject]]]
  def getImageById(imageId: Int): IO[Either[AppError, Option[ImageObject]]]
  def getTags(imageId: Int): IO[Either[AppError, List[ImageTagObject]]]
  def insertImageTags(tagItems: List[TagItem]): IO[Either[AppError, List[ImageTagObject]]]
  def deleteImage(imageId: Int): IO[Either[AppError, Int]]
  def deleteImageTags(imageId: Int): IO[Either[AppError, Int]]
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

  override def insertImageTags(tagItems: List[TagItem]): IO[Either[AppError, List[ImageTagObject]]] = {
    val test = psqlClient.insertImageTags(tagItems)
    println(test)
    test
  }

  override def deleteImage(imageId: Int): IO[Either[AppError, Int]] = {
    getImageById(imageId).flatMap {
      case Left(error) =>
        IO.pure(Left(error))
      case Right(Some(image)) =>
        IO {
          val file = new File(image.image_path)
          if (file.exists()) {
            if (file.delete()) {
              Right(())
            } else {
              Left(FailedToDeleteFile(s"Failed to delete file at path: ${image.image_path}"))
            }
          } else {
            Right(())
          }
        }.flatMap {
          case Left(fileError) =>
            IO.pure(Left(fileError))
          case Right(_) =>
            psqlClient.deleteImage(imageId)
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
      if (!Files.exists(dirPath)) {
        Files.createDirectories(dirPath)
      }

      val allowedExtensions = Set(".png", ".jpg", ".jpeg")

      def hasAllowedExtension(fileName: String): Boolean = {
        allowedExtensions.exists(ext => fileName.toLowerCase.endsWith(ext))
      }

      val finalImageName = if (hasAllowedExtension(file.image_name)) {
        file.image_name
      } else {
        file.image_name + ".png"
      }

      val uniqueFileName = UUID.randomUUID().toString + "_" + finalImageName
      val targetPath = dirPath.resolve(uniqueFileName)

      val base64Data = if (file.base64Image.contains(",")) {
        file.base64Image.split(",").last
      } else {
        file.base64Image
      }

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

}