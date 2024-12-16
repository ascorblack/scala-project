package errors

object errors {

  sealed abstract class AppError {
    val message: String
  }

  case class ImageAlreadyExists(imageName: String) extends AppError {
    val message = s"Image with name $imageName already exists"
  }

  case class DatabaseError(message: String) extends AppError {}

}
