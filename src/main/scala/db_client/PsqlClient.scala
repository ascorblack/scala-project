package db_client

import doobie.*
import doobie.implicits.*
import cats.effect.IO
import config.*
import cats.syntax.applicative.*
import cats.implicits.catsSyntaxEitherId
import cats.implicits.catsSyntaxApplicativeError
import doobie.postgres.sqlstate
import errors.errors


trait PsqlClient {
  def insertImage(imageItem: ImageItem): IO[Either[errors.AppError, ImageObject]]

  def insertImageTag(imageTagItem: TagItem): IO[Either[errors.AppError, ImageTagObject]]

  def getImages: IO[Either[errors.AppError, List[ImageObject]]]

  def findImage(imageName: String): IO[Either[errors.AppError, Option[ImageObject]]]

  def getTags(image_id: Int): IO[Either[errors.AppError, List[ImageTagObject]]]

  def deleteImage(imageId: Int): IO[Either[errors.AppError, Int]]

  def deleteImageTags(imageId: Int): IO[Either[errors.AppError, Int]]
}


object PsqlClient {
  private val db_config: DatabaseConfig = AppConfig.databaseConfig

  val client: Transactor[IO] = Transactor.fromDriverManager[IO](
    "org.postgresql.Driver",
    url = db_config.url,
    user = db_config.user,
    password = db_config.password,
    logHandler = None
  )

  object RawSQL {
    def insertImageRaw(imageItem: ImageItem): Update0 =
      sql"insert into public.images (image_name, image_path, last_update) values (${imageItem.image_name}, ${imageItem.image_path}, now())".update

    def insertImageTagRaw(imageTagItem: TagItem): Update0 =
      sql"insert into public.image_tags (image_id, tag) values (${imageTagItem.image_id}, ${imageTagItem.tag})".update

    def getImagesRaw: Query0[ImageObject] =
      sql"select id, image_name, image_path from public.images".query[ImageObject]

    def findImageRaw(image_name: String): Query0[ImageObject] =
      sql"select * from public.images where image_name = ${image_name}".query[ImageObject]

    def getTagsRaw(image_id: Int): Query0[ImageTagObject] =
      sql"select * from public.image_tags where image_id = ${image_id}".query[ImageTagObject]

    def deleteImageRaw(imageId: Int): Update0 =
      sql"delete from public.images where id = $imageId".update

    def deleteImageTagsRaw(imageId: Int): Update0 =
      sql"delete from public.image_tags where image_id = $imageId".update

  }

  private final class Implementation extends PsqlClient {

    import RawSQL._

    override def insertImage(imageItem: ImageItem): IO[Either[errors.AppError, ImageObject]] = {
      findImageRaw(imageItem.image_name).option
        .flatMap {
          case Some(existingImage) =>
            errors.ImageAlreadyExists(imageItem.image_name).asLeft[ImageObject].pure[ConnectionIO]
          case None =>
            (insertImageRaw(imageItem).withUniqueGeneratedKeys[ImageObject]("id", "image_name", "image_path"))
              .attemptSomeSqlState {
                case sqlstate.class23.UNIQUE_VIOLATION => errors.ImageAlreadyExists(imageItem.image_name)
              }
              .map(_.left.map(err => errors.DatabaseError("")))
        }
        .transact(client)
    }

    override def insertImageTag(imageTagItem: TagItem): IO[Either[errors.AppError, ImageTagObject]] = {
      insertImageTagRaw(imageTagItem)
        .withUniqueGeneratedKeys[ImageTagObject]("id", "image_id", "tag")
        .attempt
        .map {
          case Right(tag) => Right(tag)
          case Left(e) => Left(errors.DatabaseError(e.getMessage))
        }
        .transact(client)
    }

    override def getImages: IO[Either[errors.AppError, List[ImageObject]]] = {
      getImagesRaw.to[List].attempt.map {
        case Right(images) => Right(images)
        case Left(e) => Left(errors.DatabaseError(e.getMessage))
      }.transact(client)
    }

    override def findImage(imageName: String): IO[Either[errors.AppError, Option[ImageObject]]] = {
      findImageRaw(imageName).option.attempt.map {
        case Right(imageOpt) => Right(imageOpt)
        case Left(e) => Left(errors.DatabaseError(e.getMessage))
      }.transact(client)
    }

    override def getTags(image_id: Int): IO[Either[errors.AppError, List[ImageTagObject]]] = {
      getTagsRaw(image_id).to[List].attempt.map {
        case Right(tags) => Right(tags)
        case Left(e) => Left(errors.DatabaseError(e.getMessage))
      }.transact(client)
    }

    override def deleteImage(imageId: Int): IO[Either[errors.AppError, Int]] = {
      deleteImageRaw(imageId).run.attempt.map {
        case Right(count) => Right(count)
        case Left(e) => Left(errors.DatabaseError(e.getMessage))
      }.transact(client)
    }

    override def deleteImageTags(imageId: Int): IO[Either[errors.AppError, Int]] = {
      deleteImageTagsRaw(imageId).run.attempt.map {
        case Right(count) => Right(count)
        case Left(e) => Left(errors.DatabaseError(e.getMessage))
      }.transact(client)
    }
  }

  def make: PsqlClient = new Implementation
}
