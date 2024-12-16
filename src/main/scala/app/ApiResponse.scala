package app

import sttp.model.StatusCode

object ApiResponse {

  case class JSONResponse[T](
                              status: Int = StatusCode.Ok.code,
                              message: String = "success",
                              data: Option[T] = None,
                              error: Option[ErrorResponse] = None
                            )

  case class ErrorResponse(message: String)
}
