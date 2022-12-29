package util

import distance.Model.{GeoLocation, LocationEntity, Solution}
import grapple.json.JsonParser
import program.Main.{OptimizationInput, Vehicle}
import zio.*

import java.util.UUID

object JsonUtils:
  
  def parseMatrixResponse(jsonInput: String): Task[JsMatrixResponse] =
    ZIO.attempt {
      import grapple.json.{*, given}

      import scala.language.implicitConversions

      val json = Json.parse(jsonInput)

      // parse the origins section
      given jsonToOrigin: JsonInput[JsOrigin] with
        override def read(value: JsonValue): JsOrigin = JsOrigin(value("latitude"), value("longitude"))

      val origins: Seq[JsOrigin] = (json \ "origins").as[Seq[JsOrigin]]

      // parse the destinations section
      given jsonToDestination: JsonInput[JsDestination] with
        override def read(value: JsonValue): JsDestination = JsDestination(value("latitude"), value("longitude"))

      val destinations: Seq[JsDestination] = (json \ "destinations").as[Seq[JsDestination]]

      // parse the matrix section
      given jsonToDuration: JsonInput[JsDuration] with
        override def read(value: JsonValue): JsDuration = JsDuration(value("value"), value("text"))

      given jsonToDistance: JsonInput[JsDistance] with
        override def read(value: JsonValue): JsDistance = JsDistance(value("value"), value("text"))

      given jsonToMatrixPosition: JsonInput[JsMatrixPosition] with
        override def read(value: JsonValue): JsMatrixPosition = JsMatrixPosition(originIndex = value("originIndex"), destinationIndex = value("destinationIndex"), distance = value("distance"), duration = value("duration"))

      // parse the matrix
      val matrix: Seq[Seq[JsMatrixPosition]] = (json \ "matrix").as[Seq[Seq[JsMatrixPosition]]]

      JsMatrixResponse(origins = origins.toList, destinations = destinations.toList, matrix = matrix.flatten.toList)
    }

  case class JsOrigin(latitude: Float, longitude: Float)

  case class JsDestination(latitude: Float, longitude: Float)

  case class JsDistance(value: Float, text: String)

  case class JsDuration(value: Float, text: String)

  case class JsMatrixPosition(originIndex: Int, destinationIndex: Int, distance: JsDistance, duration: JsDuration)

  case class JsMatrixResponse(origins: List[JsOrigin], destinations: List[JsDestination], matrix: List[JsMatrixPosition])




