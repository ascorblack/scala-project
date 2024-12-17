package errors

object errors {

  sealed abstract class AppError {
    val message: String
  }

  case class ImageAlreadyExists(imageName: String) extends AppError {
    val message = s"Image with name $imageName already exists"
  }

  case class ImageNotFound(imageId: Int) extends AppError {
    val message = s"Image with id $imageId not found"
  }

  case class DatabaseError(msg: String) extends AppError {
    val message = s"Database error: $msg"
  }

  case class FailedToSaveFile(msg: String) extends AppError {
    val message = s"Failed to save file: $msg"
  }

  case class FailedToDeleteFile(msg: String) extends AppError {
    val message = s"Failed to delete file: $msg"
  }



}
