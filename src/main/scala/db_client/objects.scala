package db_client

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*

case class ImageItem(image_name: String, image_path: String)

case class TagItem(image_id: Int, tag: String)

case class ImageObject(id: Int, image_name: String, image_path: String)

case class ImageTagObject(id: Int, image_id: Int, tag: String)

object Encoders {
  given imageTagObjectEncoder: Encoder[ImageTagObject] = deriveEncoder[ImageTagObject]

  given imageTagObjectDecoder: Decoder[ImageTagObject] = deriveDecoder[ImageTagObject]
}
