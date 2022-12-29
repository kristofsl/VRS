package program

import distance.Model.*
import io.netty.handler.codec.json.JsonObjectDecoder
import services.*
import util.{MockUtils, Utils}
import zio.{Console, ExitCode, Scope, Task, ZIO, ZIOAppArgs, ZIOAppDefault, ZLayer}
import zhttp.http.*
import zhttp.service.Server
import java.util.UUID
import zio.json.*
import zio.logging.LogFormat
import zio.logging.backend.SLF4J
import zio.LogLevel
import scala.collection.mutable.ArrayBuffer

object Main extends ZIOAppDefault :

  //TODO validate geo location
  //TODO validate the solution
  //TODO scan voor andere oplossingen
  //TODO add health check
  //TODO JSON transforms
  //TODO Unit and other tests

  /* log settings */
  override val bootstrap = SLF4J.slf4j(LogLevel.Info, LogFormat.colored)

  /** Configuration */
  case class AppConfig(key: String)

  object AppConfig:
    val zLayer: ZLayer[Any, Throwable, AppConfig] =
      ZLayer {
        for
          geoKey: String <- ZIO.attempt(scala.util.Properties.envOrElse("GEO_KEY", ""))
        yield AppConfig(key = geoKey)
      }

  def mainProgram(input:OptimizationInput): ZIO[AppConfig & LocationService & DataStructureBuildService & OptimizationService, Throwable, OptimalSolution] =
    for
      _ <- ZIO.logInfo("Handling optimization request")
      vehicleCapacities <- ZIO.attempt(input.vehicles)
      locations         <- ZIO.attempt{
          val locations: ArrayBuffer[LocationEntity] = ArrayBuffer()
          // add the depot
          locations.addOne(
            LocationEntity(
              index = 0,
              location = input.depotLocation.location,
              name = input.depotLocation.name,
              uid = input.depotLocation.uid,
              entityType = EntityType.Depot,
              weightInGramConstraint = 0)
          )
          // add all the customers
          for i <- input.locations.indices do {
              locations.addAll(input.locations.map(l =>
                LocationEntity(
                  index = i + 1,
                  location = input.locations(i).location,
                  name = input.locations(i).name,
                  uid = input.locations(i).uid,
                  entityType = EntityType.Customer,
                  weightInGramConstraint = input.locations(i).weightInGramConstraint)
              ))
          }
          locations.toList
      }
      // build the matrix with all the API call results
      m: Matrix <- DataStructureBuildService.build(locations)
      // build an input for the optimization services
      input <- DataStructureBuildService.createInput(0, m, locations.length)
      // optimize based on the input
      solutionOpt: Option[Solution] <- OptimizationService.optimize(
        input,
        vehicleCapacities.length,
        vehicleCapacities.map(_.capacityInGrams),
        200,
        8)
        .foldZIO(
          error => ZIO.logError(s"Optimization failed : $error.getMessage") *> ZIO.succeed(None),
          success => ZIO.logInfo(s"Solution found : ${success.toString}") *> ZIO.succeed(Some(success))
        )
      solution <- ZIO.getOrFailWith[OptimizationException, Solution](OptimizationException("No solution found"))(solutionOpt)
      result   <- ZIO.attempt(
        OptimalSolution(
          objectiveValue = solution.objectiveValue,
          vehicleCount = solution.vehicleCount,
          maxKmVehicle = solution.maxKmVehicle,
          routes = solution.routes.map((r:Route) =>
            VehicleRoute(
              vehicleId = r.vehicleId,
              distanceMeters = r.distanceMeters,
              tour = r.tour
                .filter(_.entityType == EntityType.Customer)
                .map(l => CustomerLocation(location = l.location, name = l.name, uid = l.uid, weightInGramConstraint = l.weightInGramConstraint))
            )).filter(_.tour.length > 2),
          vehicleCapacity = solution.vehicleCapacity,
          comment = solution.comment,
          durationMinutes = solution.durationMinutes)
        )
    yield result

  val port: Int = 8080

  // the HTTP input / output definitions and their JSON encoders / decoders
  case class Vehicle(capacityInGrams:Long)
  case class OptimizationInput(locations: List[CustomerLocation], depotLocation: DepotLocation, vehicles: List[Vehicle], maxStops: Long, maxKm: Long)
  case class OptimizationOutput(solution: Solution)
  case class CustomerLocation(location: GeoLocation, name: String, uid: String, weightInGramConstraint: Long)
  case class DepotLocation(location: GeoLocation, name: String, uid: String)
  case class VehicleRoute(vehicleId: Int, distanceMeters: Long, tour: List[CustomerLocation])
  case class OptimalSolution(objectiveValue: Long, vehicleCount: Int, maxKmVehicle: Int, routes: List[VehicleRoute], vehicleCapacity: List[Long], comment: Option[String], durationMinutes:Long)

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
  given JsonEncoder[Solution] = DeriveJsonEncoder.gen[Solution]
  given JsonDecoder[Solution] = DeriveJsonDecoder.gen[Solution]
  given JsonEncoder[OptimizationOutput] = DeriveJsonEncoder.gen[OptimizationOutput]
  given JsonDecoder[OptimizationOutput] = DeriveJsonDecoder.gen[OptimizationOutput]

  val app: Http[Any, Throwable, Request, Response] = Http.collectZIO[Request] {
    case req@Method.POST -> !! / "optimize" =>
      for
        input:Either[String, OptimizationInput]       <- req.body.asString.map(_.fromJson[OptimizationInput])
        r:Response                                    <- input match
                                                    case Left(e) => ZIO.succeed(Response.text(e).setStatus(Status.BadRequest))
                                                    case Right(v) =>
                                                      mainProgram(v)
                                                        .flatMap(s => ZIO.attempt(s.toJson))
                                                        .flatMap(j => ZIO.attempt(Response.json(j).setStatus(Status.Ok)))
                                                        .catchSome {
                                                          case ie : InputException =>
                                                            ZIO.logError(s"Returning input error as invalid request HTTP code : ${ie.msg}") *>
                                                            ZIO.succeed(Response.text(ie.msg).setStatus(Status.BadRequest))
                                                          case oe: OptimizationException =>
                                                            ZIO.logError(s"Returning optimization error as not found HTTP code : ${oe.msg}") *>
                                                              ZIO.succeed(Response.text(oe.msg).setStatus(Status.NotFound))
                                                        }
                                                        // provide all the live dependencies
                                                        .provide(
                                                          AppConfig.zLayer,
                                                          RadarLocationServiceImpl.zLayer,
                                                          DataStructureBuildServiceImpl.zLayer,
                                                          OrToolsOptimizationServiceImpl.zLayer)
      yield r
  }

  val program: ZIO[Any, Throwable, ExitCode] = for {
    _ <- ZIO.logInfo(s"Booting server on http://localhost:$port")
    _ <- Server.start(port, app)
  } yield ExitCode.success

  override def run: ZIO[Any with ZIOAppArgs with Scope, Any, Any] =
    program
