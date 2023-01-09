package services

import distance.Model.*
import program.Main.AppConfig
import util.JsonUtils.*
import util.Utils.{buildLocationString, buildUri}
import zio.*

import java.net.http.*
import java.net.{ConnectException, URI, URISyntaxException}
import java.time.{Duration, LocalDate, LocalDateTime}
import java.util.UUID
import java.util.concurrent.TimeUnit
import scala.collection.mutable.ArrayBuffer

/** The trait description for the external location service (RADAR) */
trait LocationService:
  def matrixLookup(origin: LocationEntity, destinations: List[LocationEntity], allEntities: List[LocationEntity]): ZIO[AppConfig, Throwable, Matrix]

/** The implementation for the Radar API */
case class RadarLocationServiceImpl(appConfig: AppConfig) extends LocationService :
  val MAPS_URL: String = "https://api.radar.io/v1/route/matrix"
  val QUESTION_URL: String = "?"
  val AND_URL: String = "&"
  val OR_URL: String = "%7C"
  val DESTINATION_URL: String = "destinations="
  val ORIGINS_URL: String = "origins="
  val MODE: String = "mode=car"
  val UNITS: String = "units=metric"

  def appendLocation(temp: String, locations: Array[GeoLocation], firstAppend: Boolean): String =
    if locations.isEmpty then
      temp
    else if firstAppend then
      appendLocation(temp + locations(0).latitude + "," + locations(0).longitude, locations.drop(1), false)
    else
      appendLocation(temp + OR_URL + locations(0).latitude + "," + locations(0).longitude, locations.drop(1), false)

  def buildMapUrl(origins: List[GeoLocation], destinations: List[GeoLocation]): Task[String] =
    // Sample request :
    // curl "https://api.radar.io/v1/route/matrix?origins=40.78382,-73.97536&destinations=40.70390,-73.98690|40.73237,-73.94884&mode=car&units=imperial" \
    // -H "Authorization: prj_live_pk_..."
    ZIO.attempt {
      val baseUrl = s"""$MAPS_URL$QUESTION_URL$ORIGINS_URL"""
      val urlWithOrigins = s"""${appendLocation(baseUrl, origins.toArray, true)}$AND_URL"""
      val urlWithDestinations = s"""${appendLocation(urlWithOrigins + DESTINATION_URL, destinations.toArray, true)}$AND_URL"""
      s"""$urlWithDestinations$MODE$AND_URL$UNITS"""
    }

  override def matrixLookup(origin: LocationEntity, destinations: List[LocationEntity], allEntities: List[LocationEntity]): ZIO[AppConfig, Throwable, Matrix] =
    for
      url <- buildMapUrl(origins = List(origin.location), destinations = destinations.map(_.location))
      _ <- ZIO.logDebug(s"URL build up : $url")
      uri <- buildUri(url)
      request <- ZIO.attemptBlocking(HttpRequest.newBuilder(uri).version(HttpClient.Version.HTTP_2).GET().header("Authorization", appConfig.key).timeout(Duration.ofMinutes(5)).build())
      response: HttpResponse[String] <- ZIO.attemptBlocking(HttpClient.newBuilder().connectTimeout(Duration.ofMinutes(5)).build().send(request, HttpResponse.BodyHandlers.ofString()))
      _ <- ZIO.logDebug(s"Radar API response with code ${response.statusCode} : ${response.body} ")
      matrix: Matrix <- parseMatrixResponse(response.body, origin, destinations, allEntities)
    yield matrix

/** The companion object that creates the ZLayer */
object RadarLocationServiceImpl:
  val zLayer: ZLayer[AppConfig, Nothing, LocationService] = ZLayer {
    for
      config <- ZIO.service[AppConfig]
    yield RadarLocationServiceImpl(config)
  }

  def create(appConfig: AppConfig) = RadarLocationServiceImpl(appConfig)

/** The clean API object */
object LocationService:
  def matrixLookup(origin: LocationEntity, destinations: List[LocationEntity], allEntities: List[LocationEntity]) = ZIO.serviceWithZIO[LocationService](_.matrixLookup(origin, destinations, allEntities))