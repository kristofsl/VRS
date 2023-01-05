package services

import com.google.ortools.constraintsolver.RoutingIndexManager
import distance.Model.*
import program.Main.AppConfig
import util.JsonUtils.*
import util.Utils.{buildLocationString, buildUri}
import zio.*
import java.net.http.*
import java.net.{ConnectException, URI, URISyntaxException}
import java.time.temporal.ChronoUnit
import java.time.{Duration, LocalDate, LocalDateTime}
import java.util.UUID
import java.util.concurrent.TimeUnit
import scala.collection.mutable.ArrayBuffer

/** The trait description for the optimization services * */
trait OptimizationService:
  def optimize(input: Input, maxKmVehicle: Int, stopCountLimit: Int,secondsOptimizationLimit: Int): Task[Solution]

/** The implementation for the Google OR tools */
case class OrToolsOptimizationServiceImpl() extends OptimizationService :

  import com.google.ortools.Loader
  import com.google.ortools.constraintsolver.{Assignment, FirstSolutionStrategy, RoutingDimension, RoutingIndexManager, RoutingModel, RoutingSearchParameters, main}
  import com.google.protobuf.Duration

  import java.util.logging.Logger

  val DISTANCE = "Distance"
  val CAPACITY = "Capacity"
  val STOPS = "Stops"

  override def optimize(input: Input, maxKmVehicle: Int, stopCountLimit: Int, secondsOptimizationLimit: Int): Task[Solution] =
    for
      startTime: LocalDateTime <- ZIO.succeed(LocalDateTime.now)
      loader <- ZIO.attempt(Loader.loadNativeLibraries())
      manager <- ZIO.attempt(new RoutingIndexManager(input.fullMatrix.length, input.fleet.length, input.depotIndex))
      model: RoutingModel <- ZIO.attempt(new RoutingModel(manager))
      // callback for our distance
      transitCallbackIndex: Int <- ZIO.attempt(model.registerTransitCallback(
        (fromIndex: Long, toIndex: Long) => {
          val fromNode: Int = manager.indexToNode(fromIndex)
          val toNode: Int = manager.indexToNode(toIndex)
          input.fullMatrix(fromNode)(toNode)
        }
      ))
      // callback for the weight constraints per vehicle
      demandCallbackIndex: Int <- ZIO.attempt(model.registerUnaryTransitCallback((fromIndex: Long) => {
        val fromNode = manager.indexToNode(fromIndex)
        input.dataMatrix.entities(fromNode).weightInGramConstraint
      }))
      // callback for the max stops per vehicle
      stopsCallbackIndex: Int <- ZIO.attempt(model.registerUnaryTransitCallback((fromIndex: Long) => {
        1
      }))
      _ <- ZIO.attempt(model.setArcCostEvaluatorOfAllVehicles(transitCallbackIndex))
      _ <- ZIO.attempt(model.addDimension(transitCallbackIndex, 0, maxKmVehicle * 1000, true, DISTANCE))
      _ <- ZIO.attempt(model.addDimension(stopsCallbackIndex, 0, stopCountLimit, true, STOPS))
      _ <- ZIO.attempt(model.addDimensionWithVehicleCapacity(demandCallbackIndex, 0, input.fleet.map(_.capacityInGrams).toArray, true, CAPACITY))
      distanceDimension: RoutingDimension <- ZIO.attempt(model.getMutableDimension(DISTANCE))
      _ <- ZIO.attempt(distanceDimension.setGlobalSpanCostCoefficient(100))
      searchParams <- ZIO.attempt(
        main.defaultRoutingSearchParameters()
          .toBuilder
          .setTimeLimit(Duration.newBuilder().setSeconds(secondsOptimizationLimit).build())
          .setFirstSolutionStrategy(FirstSolutionStrategy.Value.AUTOMATIC)
          .build
      )
      result: Assignment <- ZIO.attempt(model.solveWithParameters(searchParams))
      objectiveValue: Option[Long] <- ZIO.attempt(if model.status() == 1 then Some(result.objectiveValue) else None)
      objectiveValue: Long <- ZIO.getOrFailWith(OptimizationException("Model solver did not succeed (status != 1)"))(objectiveValue)
      routes: IndexedSeq[Route] <- ZIO.foreach(0 until input.fleet.length) {
        vehicleIndex => {
          collectSolutionForVehicle(vehicleIndex, input, model, manager, result, input.fleet)
        }
      }
      endTime: LocalDateTime <- ZIO.succeed(LocalDateTime.now)
      c <- ZIO.attempt {
        if routes.count(_.tour.length > 2) < input.fleet.length then
          Some("Full capacity of vehicles is not needed in optimal solution. Try to downscale the fleet and compare the results")
        else
          None
      }
      solution <- ZIO.succeed(
        Solution(
          objectiveValue = objectiveValue,
          vehicleCount = routes.count(_.tour.length > 2),
          maxKmVehicle = maxKmVehicle,
          routes = routes.toList,
          fleet = input.fleet,
          comment = c,
          durationMinutes = ChronoUnit.MINUTES.between(startTime, endTime)
        ))
    yield solution


  def collectSolutionForVehicle(vehicleIndex: Int, input: Input, routing: RoutingModel, manager: RoutingIndexManager, result: Assignment, fleet: List[FleetEntity]): Task[Route] =
    for
      _ <- ZIO.logDebug(s"Collecting the route for vehicle $vehicleIndex")
      route <- ZIO.attempt {
        val entities = ArrayBuffer[LocationEntity]()
        var index = routing.start(vehicleIndex)
        var routeDistanceMeters = 0L
        while (!routing.isEnd(index)) {
          val indexStop = manager.indexToNode(index)
          val previousIndex = index
          index = result.value(routing.nextVar(index))
          routeDistanceMeters = routeDistanceMeters + routing.getArcCostForVehicle(previousIndex, index, vehicleIndex)
          val e: LocationEntity = input.dataMatrix.entities.filter(_.index == indexStop).last
          entities.addOne(e)
        }
        val depotIndex = manager.indexToNode(index)
        routeDistanceMeters += routing.getArcCostForVehicle(index, depotIndex, vehicleIndex)
        entities.addOne(input.dataMatrix.entities.filter(_.index == depotIndex).last)
        Route(vehicleId = vehicleIndex, distanceMeters = routeDistanceMeters, tour = entities.toList, fleetEntity = fleet(vehicleIndex))
      }
    yield route


/** The companion object that creates the ZLayer */
object OrToolsOptimizationServiceImpl:
  val zLayer: ZLayer[Any, Nothing, OptimizationService] = ZLayer.succeed(create())

  def create() = OrToolsOptimizationServiceImpl()

/** The clean API object */
object OptimizationService:
  def optimize(input: Input, maxKmVehicle: Int, stopCountLimit: Int,secondsOptimizationLimit: Int) = ZIO.serviceWithZIO[OptimizationService](_.optimize(input, maxKmVehicle, stopCountLimit, secondsOptimizationLimit))