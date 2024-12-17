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

  def insertImageTags(imageTagItems: List[TagItem]): IO[Either[errors.AppError, List[ImageTagObject]]]

  def getImages: IO[Either[errors.AppError, List[ImageObject]]]

  def findImage(imageName: String): IO[Either[errors.AppError, Option[ImageObject]]]

  def getImageById(imageId: Int): IO[Either[errors.AppError, Option[ImageObject]]]

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

    def insertImageTagsRaw(tagItems: List[TagItem]): ConnectionIO[Int] = {
      val sqlBase = "insert into public.image_tags (image_id, tag) values (?, ?)"
      val sqlBatch = Update[TagItem](sqlBase)
      sqlBatch.updateMany(tagItems)
    }

    def getImagesRaw: Query0[ImageObject] =
      sql"select id, image_name, image_path from public.images".query[ImageObject]

    def findImageRaw(imageName: String): Query0[ImageObject] =
      sql"select * from public.images where image_name = ${imageName}".query[ImageObject]

    def getImageByIdRaw(imageId: Int): Query0[ImageObject] =
      sql"select * from public.images where id = ${imageId}".query[ImageObject]

    def getTagsRaw(imageId: Int): Query0[ImageTagObject] =
      sql"select * from public.image_tags where image_id = ${imageId}".query[ImageTagObject]

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

    import cats.data.NonEmptyList
    import doobie._
    import doobie.implicits._
    import doobie.util.fragments._

    def insertImageTags(tagItems: List[TagItem]): IO[Either[errors.AppError, List[ImageTagObject]]] = {
      val batchInsert = insertImageTagsRaw(tagItems)

      batchInsert
        .flatMap { _ =>
          val imageIds = tagItems.map(_.image_id)

          NonEmptyList.fromList(imageIds) match {
            case Some(nelImageIds) =>
              val whereClause = Fragments.in(fr"image_id", nelImageIds)
              val query =
                fr"""
                select id, image_id, tag
                from public.image_tags
                where
              """ ++ whereClause

              query.query[ImageTagObject].to[List]

            case None =>
              FC.pure(List.empty[ImageTagObject])
          }
        }
        .attempt
        .map {
          case Right(tags) => Right(tags)
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

    override def getImageById(imageId: Int): IO[Either[errors.AppError, Option[ImageObject]]] = {
      getImageByIdRaw(imageId).option.attempt.map {
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
