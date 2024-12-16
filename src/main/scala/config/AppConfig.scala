package config

case class DatabaseConfig(url: String, user: String, password: String)

case class WebServerConfig(host: String, port: Int)

object AppConfig {
  private def getEnv(key: String): Option[String] = Option(System.getenv(key))

  def databaseConfig: DatabaseConfig = {
    val host = getEnv("DB_HOST").getOrElse("0.0.0.0")
    val port = getEnv("DB_PORT").getOrElse("5435")
    val dbName = getEnv("DB_NAME").getOrElse("project")
    val user = getEnv("DB_USER").getOrElse("dev")
    val password = getEnv("DB_PASSWORD").getOrElse("752113")

    val url = s"jdbc:postgresql://$host:$port/$dbName"
    DatabaseConfig(url, user, password)
  }

  def webServerConfig: WebServerConfig = {
    val host = getEnv("HOST").getOrElse("0.0.0.0")
    val port = getEnv("PORT").map(_.toInt).getOrElse(8080)

    WebServerConfig(host, port)
  }

}

