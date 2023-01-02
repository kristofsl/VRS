package services

import distance.Model.*
import program.Main.AppConfig
import util.JsonUtils.*
import util.Utils.{buildRelationShips, validateInput}
import zio.*

import java.net.http.*
import java.net.{ConnectException, URI, URISyntaxException}
import java.time.{Duration, LocalDate, LocalDateTime}
import java.util.UUID
import java.util.concurrent.TimeUnit
import scala.collection.immutable.List
import scala.collection.mutable.ArrayBuffer

/** The trait description for the service that will build up all the information in one data structure for the optimisation algorithm */
trait DataStructureBuildService:
  def build(entities: List[LocationEntity]): ZIO[LocationService with AppConfig, Throwable, Matrix]

  def createInput(depotIndex: Int, input: Matrix, dimension: Int): Task[Input]

/** The Live implementation */
case class DataStructureBuildServiceImpl(locationService: LocationService) extends DataStructureBuildService :
  override def build(entities: List[LocationEntity]): ZIO[LocationService & AppConfig, Throwable, Matrix] =
    for
    // build all needed relationships without including unneeded relationships that require no API calls
      relationships: Map[Int, List[LocationRelationCombination]] <- ZIO.cond(validateInput(entities), buildRelationShips(entities.length), InputException("Invalid input detected : at least 2 customers and one depot is required and only valid latitude / longitudes are accepted"))
      // fetch all the results from the location service by calling the service with one origin and multiple destinations
      results: Iterable[Matrix] <- ZIO.foreach(relationships.keys) {
        (index: Int) => {
          val originIndex: Int = index
          val destinationIndexList: List[Int] = relationships(index).map(_.index2)
          fetchDataForOrigin(originIndex, destinationIndexList, entities)
        }
      }
      // merge the results from multiple origins
      result: Matrix <- ZIO.succeed(results.fold(Matrix.createEmpty(entities))(Matrix.combine))
    yield result

  def fetchDataForOrigin(originIndex: Int, destinationIndexList: List[Int], entities: List[LocationEntity]): ZIO[LocationService & AppConfig, Throwable, Matrix] =
    for
    // fetch the origin information from all the entities
      origin: LocationEntity <- ZIO.succeed(entities.filter(_.index == originIndex).last)
      // fetch the destinations information from all the entities
      destinations: List[LocationEntity] <- ZIO.succeed(entities.filter(l => destinationIndexList.contains(l.index)))
      // group the destinations by 25 because of the limits in the location services (max relationships in one API call is 25)
      destinationsGrouped: Iterator[List[LocationEntity]] <- ZIO.succeed(destinations.grouped(25))
      // call the location service API for each group
      results: Iterable[Matrix] <- ZIO.foreach(destinationsGrouped.toList) {
        (group: List[LocationEntity]) => ZIO.logDebug(s"Calling the matrix API for origin index $originIndex and destination indexes ${group.toString}") *> LocationService.matrixLookup(origin, group, entities)
      }
      // merge the results from all the API calls
      result: Matrix <- ZIO.succeed(results.fold(Matrix.createEmpty(entities))(Matrix.combine))
    yield result

  override def createInput(depotIndex: Int, input: Matrix, dimension: Int): Task[Input] =
    for
    // create multi dimensional array for all relationships and initialize with zeros (even the ones that require no API call)
      datamatrix: Array[Array[Long]] <- ZIO.succeed(Array.ofDim[Long](dimension, dimension))
      datamatrixFull: Array[Array[Long]] <- ZIO.attempt {
        // fill the values and the opposite direction with the same value
        for mp <- input.results do {
          datamatrix(mp.originIndex)(mp.destinationIndex) = mp.distanceMeters
          datamatrix(mp.destinationIndex)(mp.originIndex) = mp.distanceMeters
        }
        datamatrix
      }
    yield (Input(fullMatrix = datamatrixFull, depotIndex = depotIndex, dataMatrix = input, demands = input.entities.map(_.weightInGramConstraint)))

/** The companion object that creates the ZLayer */
object DataStructureBuildServiceImpl:
  val zLayer: ZLayer[LocationService with AppConfig, Nothing, DataStructureBuildService] = ZLayer {
    for
      loc <- ZIO.service[LocationService]
    yield DataStructureBuildServiceImpl(loc)
  }

  def create(locationService: LocationService) = DataStructureBuildServiceImpl(locationService)

/** The clean API object */
object DataStructureBuildService:
  def build(entities: List[LocationEntity]) = ZIO.serviceWithZIO[DataStructureBuildService](_.build(entities))

  def createInput(depotIndex: Int, input: Matrix, dimension: Int) = ZIO.serviceWithZIO[DataStructureBuildService](_.createInput(depotIndex, input, dimension))