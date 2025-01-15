package config

import scala.io.Source

case class DatabaseConfig(url: String, user: String, password: String)

case class WebServerConfig(host: String, port: Int, imagesPath: String)

object AppConfig {
  private def getEnv(key: String): Option[String] = Option(System.getenv(key))

  def databaseConfig: DatabaseConfig = {
    val host = getEnv("POSTGRES_HOST").getOrElse("0.0.0.0")
    val port = getEnv("POSTGRES_PORT").getOrElse("5435")
    val dbName = getEnv("POSTGRES_DB").getOrElse("project")
    val user = getEnv("POSTGRES_USER").getOrElse("dev")
    val password = getEnv("POSTGRES_PASSWORD").getOrElse("752113")

    val url = s"jdbc:postgresql://$host:$port/$dbName"
    DatabaseConfig(url, user, password)
  }

  def webServerConfig: WebServerConfig = {
    val host = getEnv("SERVER_HOST").getOrElse("0.0.0.0")
    val port = getEnv("SERVER_PORT").map(_.toInt).getOrElse(8888)
    val imagesPath = getEnv("IMAGES_PATH").getOrElse(Source.getClass.getClassLoader.getResource("images").getPath)

    WebServerConfig(host, port, imagesPath)
  }

}

