package distance

import util.JsonUtils
import util.JsonUtils.JsMatrixResponse
import zio.*
import zio.json.*

import java.net.http.*
import java.net.{ConnectException, URI, URISyntaxException}
import java.time.{Duration, LocalDate, LocalDateTime}
import java.util.UUID
import java.util.concurrent.TimeUnit
import scala.annotation.tailrec
import scala.collection.mutable.ArrayBuffer

object Model:
  case class InputException(msg: String) extends Exception :
    override def getMessage: String = s"Input problem detected: $msg"

  case class OptimizationException(msg: String) extends Exception :
    override def getMessage: String = s"Optimization exception detected : $msg"

  case class LocationEntity(index: Int, location: GeoLocation, name: String, uid: String, entityType: EntityType, weightInGramConstraint: Long)

  case class GeoLocation(latitude: Float, longitude: Float)

  case class LocationDifference(durationMinutes: Float, distanceKm: Float)

  case class LocationRelationCombination(index1: Int, index2: Int)

  case class Matrix(entities: List[LocationEntity], results: List[MatrixPosition])

  case class MatrixPosition(originIndex: Int, destinationIndex: Int, distanceMeters: Long, durationMinutes: Long)

  case class Solution(objectiveValue: Long, vehicleCount: Int, maxKmVehicle: Int, routes: List[Route], vehicleCapacity: List[Long], comment: Option[String], durationMinutes: Long):
    override def toString: String =
      s"""
         |Summary solution:
         |-----------------
         |objective value               : $objectiveValue
         |available vehicle count       : $vehicleCount
         |used vehicle count            : ${routes.count(_.tour.length > 2)}
         |total km                      : ${routes.map(_.distanceMeters).sum / 1000}
         |max km by vehicle             : $maxKmVehicle
         |optimization duration minutes : $durationMinutes
         |comment                       : ${comment.getOrElse("/")}
         |${routes.map(_.toString(vehicleCapacity))}
         |""".stripMargin


  case class Route(vehicleId: Int, distanceMeters: Long, tour: List[LocationEntity]):
    def toString(vehicleCapacities: List[Long]): String =
      s"""
         |Route summary:
         |--------------
         |vehicle id                 : $vehicleId
         |vehicle max capacity kg    : ${vehicleCapacities(vehicleId) / 1000}
         |distance route in km       : ${distanceMeters / 1000}
         |total weight in kg         : ${tour.map(t => t.weightInGramConstraint).sum / 1000}
         |tour description           : \n${tour.map(t => s"${t.index} - ${t.name} with ${t.weightInGramConstraint / 1000} kg weight").mkString(" \n--> ")}
         |""".stripMargin

  case class Input(fullMatrix: Array[Array[Long]], depotIndex: Int, dataMatrix: Matrix, demands: List[Float]):
    override def toString: String =
      s"""\n
         |Input summary : \n
         |---------------
         |depot index   : $depotIndex \n
         |entities      : \n
         |${entitiesToString(dataMatrix.entities)} \n
         |matrix        : \n
         |${matrixElementToString(fullMatrix, 0, 0)}""".stripMargin

    def matrixElementToString(fullMatrix: Array[Array[Long]], row: Int, col: Int): String =
      if col < fullMatrix.length & row < fullMatrix.length then
        val v = s"row $row and col $col : ${fullMatrix(row)(col)} \n"
        val next = v + matrixElementToString(fullMatrix, row, col + 1)
        next
      else if col == fullMatrix.length & row < fullMatrix.length then
        matrixElementToString(fullMatrix, row + 1, 0)
      else ""

    def entitiesToString(entityList: List[LocationEntity]): String =
      entityList.map(l => s"${l.index} : ${l.name}").mkString("\n")

  object GeoLocation:
    def validate(latitude: Float, longitude: Float): Boolean =
      if latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180 then false else true

  object Matrix:
    def combine(left: Matrix, right: Matrix): Matrix =
      Matrix(entities = left.entities, results = left.results ++ right.results)

    def createEmpty(allEntities: List[LocationEntity]): Matrix = Matrix(allEntities, List())

  enum EntityType:
    case Customer, Depot


