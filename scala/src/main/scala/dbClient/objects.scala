package dbClient

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*
import sttp.tapir.*
import sttp.model.Part

import java.io.File
import sttp.tapir.generic.auto.*

import java.util.Base64


case class ImageItem(image_name: String, image_path: String)

case class UploadedImage(image_name: String, base64Image: String)

case class DownloadedBase64Image(id: Int, image_name: String, base64Image: String)

case class TagItem(image_id: Int, tag: String)

case class ImageObject(id: Int, image_name: String, image_path: String)

case class ImageTagObject(id: Int, image_id: Int, tag: String)

object Encoders {
  given imageTagObjectEncoder: Encoder[ImageTagObject] = deriveEncoder[ImageTagObject]

  given imageTagObjectDecoder: Decoder[ImageTagObject] = deriveDecoder[ImageTagObject]
}
