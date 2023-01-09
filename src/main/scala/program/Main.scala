package program

import distance.Model.*
import io.netty.handler.codec.json.JsonObjectDecoder
import services.*
import util.Utils
import zhttp.http.*
import zhttp.service.Server
import zio.json.*
import zio.{Console, ExitCode, LogLevel, Scope, Task, ZIO, ZIOAppArgs, ZIOAppDefault, ZLayer}

import java.util.UUID
import scala.collection.mutable.ArrayBuffer

object Main extends ZIOAppDefault :

  val port: Int = 8080

  val app: Http[Any, Throwable, Request, Response] = Http.collectZIO[Request] {
    case req@Method.POST -> !! / "optimize" =>
      for
        input: Either[String, OptimizationInput] <- req.body.asString.map(_.fromJson[OptimizationInput])
        r: Response <- input match
          case Left(e) => ZIO.succeed(Response.text(e).setStatus(Status.BadRequest))
          case Right(v) =>
            mainProgram(v)
              .flatMap(s => ZIO.attempt(s.toJson))
              .flatMap(j => ZIO.attempt(Response.json(j).setStatus(Status.Ok)))
              .catchSome {
                case ie: InputException =>
                  ZIO.logError(s"Returning input error as invalid request HTTP code : ${ie.msg}") *>
                    ZIO.succeed(Response.json(Problem(ie.msg).toJson).setStatus(Status.BadRequest))
                case oe: OptimizationException =>
                  ZIO.logError(s"Returning optimization error as not found HTTP code : ${oe.msg}") *>
                    ZIO.succeed(Response.json(Problem(oe.msg).toJson).setStatus(Status.NotFound))
                case ea: ExternalAPIException =>
                  ZIO.logError(s"Returning external API error as failed dependency HTTP code : ${ea.msg}") *>
                    ZIO.succeed(Response.json(Problem(ea.msg).toJson).setStatus(Status.FailedDependency))
              }
              // provide all the live dependencies
              .provide(
                AppConfig.zLayer,
                RadarLocationServiceImpl.zLayer,
                DataStructureBuildServiceImpl.zLayer,
                OrToolsOptimizationServiceImpl.zLayer)
      yield r
    case req@Method.GET -> !! / "health" => ZIO.attempt(Response.status(Status.Ok))
  }
  val program: ZIO[Any, Throwable, ExitCode] = for {
    _ <- ZIO.logInfo(s"Booting server on http://localhost:$port")
    _ <- Server.start(port, app)
  } yield ExitCode.success

  def mainProgram(userInput: OptimizationInput): ZIO[AppConfig & LocationService & DataStructureBuildService & OptimizationService, Throwable, OptimalSolution] =
    for
      locations <- ZIO.attempt {
        val locations: ArrayBuffer[LocationEntity] = ArrayBuffer()
        // add the depot
        locations.addOne(
          LocationEntity(
            index = 0,
            location = userInput.depotLocation.location,
            name = userInput.depotLocation.name,
            uid = userInput.depotLocation.uid,
            entityType = EntityType.Depot,
            weightInGramConstraint = 0)
        )
        // add all the customers
        for i <- userInput.locations.indices do {
          locations.addOne(
            LocationEntity(
              index = i + 1,
              location = userInput.locations(i).location,
              name = userInput.locations(i).name,
              uid = userInput.locations(i).uid,
              entityType = EntityType.Customer,
              weightInGramConstraint = userInput.locations(i).weightInGramConstraint)
          )
        }
        locations.toList
      }
      // build the matrix with all the API call results
      m: Matrix <- DataStructureBuildService.build(locations)
      // build an input for the optimization services
      input <- DataStructureBuildService.createInput(0, m, locations.length, userInput.vehicles)
      // optimize based on the input
      solutionOpt: Option[Solution] <- OptimizationService.optimize(
        input,
        userInput.maxKm.toInt,
        userInput.maxCustomerStops.toInt + 1,
        userInput.secondsOptimizationLimit
      )
        .foldZIO(
          error => ZIO.logError(s"Optimization failed : $error.getMessage") *> ZIO.succeed(None),
          success => ZIO.logInfo(s"Solution found with objective value: ${success.objectiveValue}") *> ZIO.succeed(Some(success))
        )
      solution <- ZIO.getOrFailWith[OptimizationException, Solution](OptimizationException("No solution found"))(solutionOpt)
      result <- ZIO.attempt(
        OptimalSolution(
          objectiveValue = solution.objectiveValue,
          usedVehicleCount = solution.vehicleCount,
          availableVehicleCount = solution.fleet.length,
          maxKmVehicle = solution.maxKmVehicle,
          maxCustomerStopCount = userInput.maxCustomerStops.toInt,
          totalKm = (solution.routes.map(_.distanceMeters).sum / 1000).toInt,
          routes = solution.routes.map((r: Route) =>
            VehicleRoute(
              totalWeightInGrams = r.tour.map(_.weightInGramConstraint).sum,
              vehicleIdentifier = r.fleetEntity.vehicleIdentification,
              driverName = r.fleetEntity.driverName,
              vehicleCapacityInGrams = r.fleetEntity.capacityInGrams,
              vehicleId = r.vehicleId,
              customerStopCount = r.tour.length - 2,
              distanceKm = r.distanceMeters / 1000,
              tour = r.tour
                .map(l => CustomerLocation(location = l.location, name = l.name, uid = l.uid, weightInGramConstraint = l.weightInGramConstraint))
            )).filter(_.tour.length >= 3),
          comment = solution.comment,
          durationMinutes = solution.durationMinutes)
      )
    yield result

  // JSON encoding / decoding for the REST API
  given JsonDecoder[Vehicle] = DeriveJsonDecoder.gen[Vehicle]

  given JsonEncoder[Vehicle] = DeriveJsonEncoder.gen[Vehicle]

  given JsonDecoder[VehicleRoute] = DeriveJsonDecoder.gen[VehicleRoute]

  given JsonEncoder[VehicleRoute] = DeriveJsonEncoder.gen[VehicleRoute]

  given JsonDecoder[OptimalSolution] = DeriveJsonDecoder.gen[OptimalSolution]

  given JsonEncoder[OptimalSolution] = DeriveJsonEncoder.gen[OptimalSolution]

  given JsonEncoder[GeoLocation] = DeriveJsonEncoder.gen[GeoLocation]

  given JsonDecoder[GeoLocation] = DeriveJsonDecoder.gen[GeoLocation]

  given JsonEncoder[LocationEntity] = DeriveJsonEncoder.gen[LocationEntity]

  given JsonDecoder[LocationEntity] = DeriveJsonDecoder.gen[LocationEntity]

  given JsonDecoder[CustomerLocation] = DeriveJsonDecoder.gen[CustomerLocation]

  given JsonEncoder[CustomerLocation] = DeriveJsonEncoder.gen[CustomerLocation]

  given JsonEncoder[DepotLocation] = DeriveJsonEncoder.gen[DepotLocation]

  given JsonDecoder[DepotLocation] = DeriveJsonDecoder.gen[DepotLocation]

  given JsonEncoder[OptimizationInput] = DeriveJsonEncoder.gen[OptimizationInput]

  given JsonDecoder[OptimizationInput] = DeriveJsonDecoder.gen[OptimizationInput]

  given JsonEncoder[EntityType] = DeriveJsonEncoder.gen[EntityType]

  given JsonDecoder[EntityType] = DeriveJsonDecoder.gen[EntityType]

  given JsonEncoder[Route] = DeriveJsonEncoder.gen[Route]

  given JsonDecoder[Route] = DeriveJsonDecoder.gen[Route]

  given JsonEncoder[FleetEntity] = DeriveJsonEncoder.gen[FleetEntity]

  given JsonDecoder[FleetEntity] = DeriveJsonDecoder.gen[FleetEntity]

  given JsonEncoder[Solution] = DeriveJsonEncoder.gen[Solution]

  given JsonDecoder[Solution] = DeriveJsonDecoder.gen[Solution]

  given JsonEncoder[OptimizationOutput] = DeriveJsonEncoder.gen[OptimizationOutput]

  given JsonDecoder[OptimizationOutput] = DeriveJsonDecoder.gen[OptimizationOutput]

  given JsonEncoder[Problem] = DeriveJsonEncoder.gen[Problem]

  given JsonDecoder[Problem] = DeriveJsonDecoder.gen[Problem]

  override def run: ZIO[Any with ZIOAppArgs with Scope, Any, Any] =
    program

  /** Configuration */
  case class AppConfig(key: String)

  // the HTTP input / output definitions
  case class Vehicle(capacityInGrams: Long, driverName: String, vehicleIdentifier: String)

  case class Problem(msg: String)

  case class OptimizationInput(locations: List[CustomerLocation], depotLocation: DepotLocation, vehicles: List[Vehicle], maxCustomerStops: Long, maxKm: Long, secondsOptimizationLimit: Int)

  case class OptimizationOutput(solution: Solution)

  case class CustomerLocation(location: GeoLocation, name: String, uid: String, weightInGramConstraint: Long)

  case class DepotLocation(location: GeoLocation, name: String, uid: String)

  case class VehicleRoute(vehicleId: Int, vehicleIdentifier: String, driverName: String, distanceKm: Float, customerStopCount: Int, tour: List[CustomerLocation], totalWeightInGrams: Long, vehicleCapacityInGrams: Long)

  case class OptimalSolution(objectiveValue: Long, usedVehicleCount: Int, totalKm: Float, availableVehicleCount: Int, maxKmVehicle: Int, maxCustomerStopCount: Int, routes: List[VehicleRoute], comment: Option[String], durationMinutes: Long)

  object AppConfig:
    val zLayer: ZLayer[Any, Throwable, AppConfig] =
      ZLayer {
        for
          geoKey: String <- ZIO.attempt(scala.util.Properties.envOrElse("GEO_KEY", ""))
        yield AppConfig(key = geoKey)
      }