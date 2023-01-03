package util

import distance.Model.{ExternalAPIException, GeoLocation, LocationEntity, Solution, Matrix, MatrixPosition}
import program.Main.{OptimizationInput, Vehicle}
import zio.*
import zio.json.*
import java.util.{Date, UUID}

object JsonUtils:

  // API specific model classes
  case class DistanceData(meta: Meta, origins: List[Origin], destinations: List[Destination], matrix: List[List[MatrixEntry]])
  case class Meta(code: Int)
  case class Origin(latitude: Float, longitude: Float)
  case class Destination(latitude: Float, longitude: Float)
  case class MatrixEntry(distance: Distance, duration: Duration, originIndex: Int, destinationIndex: Int)
  case class Distance(value: Float, text: String)
  case class Duration(value: Float, text: String)

  given JsonDecoder[DistanceData] = DeriveJsonDecoder.gen[DistanceData]
  given JsonDecoder[Meta] = DeriveJsonDecoder.gen[Meta]
  given JsonDecoder[Origin] = DeriveJsonDecoder.gen[Origin]
  given JsonDecoder[Destination] = DeriveJsonDecoder.gen[Destination]
  given JsonDecoder[MatrixEntry] = DeriveJsonDecoder.gen[MatrixEntry]
  given JsonDecoder[Distance] = DeriveJsonDecoder.gen[Distance]
  given JsonDecoder[Duration] = DeriveJsonDecoder.gen[Duration]

  def parseMatrixResponse(jsonInput: String, origin: LocationEntity, destinations: List[LocationEntity], allEntities: List[LocationEntity]): Task[Matrix] =
      for
        backendData:DistanceData        <- ZIO.attempt{jsonInput.fromJson[DistanceData] match
          case Left(e)  => throw ExternalAPIException(e)
          case Right(v) => v
        }
        matrixEntries:List[MatrixEntry] <- ZIO.attempt(backendData.matrix.flatten)
        results:List[MatrixPosition]    <- ZIO.attempt(matrixEntries.map((e:MatrixEntry) => MatrixPosition(
          originIndex = origin.index,
          destinationIndex = Utils.findMatch(
            backendData.destinations(e.destinationIndex).latitude,
            backendData.destinations(e.destinationIndex).longitude,
            allEntities
          ),
          distanceMeters = e.distance.value.toLong,
          durationMinutes = e.duration.value.toLong
        )))
        matrix <- ZIO.attempt(Matrix(allEntities,results))
      yield matrix