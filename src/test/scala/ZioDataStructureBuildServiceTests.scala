import ZioDataStructureBuildServiceTesting.suite
import distance.Model
import distance.Model.{LocationEntity, MatrixPosition, *}
import program.Main.AppConfig
import services.{DataStructureBuildService, DataStructureBuildServiceImpl, LocationService, RadarLocationServiceImpl}
import util.JsonUtils
import util.Utils
import zio.*
import zio.test.*
import scala.collection.immutable.List
import scala.collection.mutable.ArrayBuffer

object ZioDataStructureBuildServiceTesting extends ZIOSpecDefault {

  /** mocking the location services */
  case class LocationServiceMock() extends LocationService:
    override def matrixLookup(origin: LocationEntity, destinations: List[LocationEntity], allEntities: List[LocationEntity]): ZIO[AppConfig, Throwable, Matrix] =
      ZIO.attempt{
        val positions = ArrayBuffer[MatrixPosition]()
        for d <- destinations do {
            positions.addOne(MatrixPosition(originIndex = origin.index, destinationIndex = d.index, distanceMeters = origin.index + 1, durationMinutes = origin.index + 1))
        }
        Matrix(allEntities,positions.toList)
      }

  def createLocationServiceMock() = LocationServiceMock()
  val locationServiceMockZLayer: ZLayer[Any, Nothing, LocationService] =
    ZLayer.succeed(createLocationServiceMock())

  def spec = suite("ZIO data service building test suite")(
    suite("Create input")(
      test("build combinations 2 customers and depot with non zero index") {
        val inputMatrix = Matrix(
          entities = List(
            LocationEntity(index = 0, location = GeoLocation(0,0), name = "cust_0", uid = "0", entityType = EntityType.Customer, weightInGramConstraint = 1),
            LocationEntity(index = 1, location = GeoLocation(0,0), name = "depot_1", uid = "1", entityType = EntityType.Depot, weightInGramConstraint = 0),
            LocationEntity(index = 2, location = GeoLocation(0,0), name = "cust_1", uid = "2", entityType = EntityType.Customer, weightInGramConstraint = 1),
          ),
          results = List(
            MatrixPosition(originIndex = 0, destinationIndex = 1, distanceMeters = 1, durationMinutes = 1),
            MatrixPosition(originIndex = 0, destinationIndex = 2, distanceMeters = 1, durationMinutes = 1),
            MatrixPosition(originIndex = 1, destinationIndex = 2, distanceMeters = 1, durationMinutes = 1)
          )
        )
        assertZIO(ZIO.service[DataStructureBuildService].flatMap(_.createInput(
          depotIndex = 1,
          inputMatrix,
          dimension = 3,
          vehicles = List()
        ).flatMap(i => ZIO.succeed(i.fullMatrix.flatten.toList))))(Assertion.equalTo(
            Array(
              Array(0l, 1l, 1l),
              Array(1l, 0l, 1l),
              Array(1l, 1l, 0l)).flatten.toList))
      }.provide(DataStructureBuildServiceImpl.zLayer, locationServiceMockZLayer, AppConfig.zLayer),
      test("build combinations 2 customers and depot with zero index") {
        val inputMatrix = Matrix(
          entities = List(
            LocationEntity(index = 1, location = GeoLocation(0,0), name = "cust_0", uid = "0", entityType = EntityType.Customer, weightInGramConstraint = 1),
            LocationEntity(index = 0, location = GeoLocation(0,0), name = "depot_1", uid = "1", entityType = EntityType.Depot, weightInGramConstraint = 0),
            LocationEntity(index = 2, location = GeoLocation(0,0), name = "cust_1", uid = "2", entityType = EntityType.Customer, weightInGramConstraint = 1),
          ),
          results = List(
            MatrixPosition(originIndex = 0, destinationIndex = 1, distanceMeters = 1, durationMinutes = 1),
            MatrixPosition(originIndex = 0, destinationIndex = 2, distanceMeters = 1, durationMinutes = 1),
            MatrixPosition(originIndex = 1, destinationIndex = 2, distanceMeters = 1, durationMinutes = 1)
          )
        )
        assertZIO(ZIO.service[DataStructureBuildService].flatMap(_.createInput(
          depotIndex = 0,
          inputMatrix,
          dimension = 3,
          vehicles = List()
        ).flatMap(i => ZIO.succeed(i.fullMatrix.flatten.toList))))(Assertion.equalTo(
          Array(
            Array(0l, 1l, 1l),
            Array(1l, 0l, 1l),
            Array(1l, 1l, 0l)).flatten.toList))
      }.provide(DataStructureBuildServiceImpl.zLayer, locationServiceMockZLayer, AppConfig.zLayer),
      test("build combinations 2 customers and depot with zero index and random durations") {
        val inputMatrix = Matrix(
          entities = List(
            LocationEntity(index = 1, location = GeoLocation(0,0), name = "cust_0", uid = "0", entityType = EntityType.Customer, weightInGramConstraint = 1),
            LocationEntity(index = 0, location = GeoLocation(0,0), name = "depot_1", uid = "1", entityType = EntityType.Depot, weightInGramConstraint = 0),
            LocationEntity(index = 2, location = GeoLocation(0,0), name = "cust_1", uid = "2", entityType = EntityType.Customer, weightInGramConstraint = 1),
          ),
          results = List(
            MatrixPosition(originIndex = 0, destinationIndex = 1, distanceMeters = 12, durationMinutes = 1),
            MatrixPosition(originIndex = 0, destinationIndex = 2, distanceMeters = 11, durationMinutes = 1),
            MatrixPosition(originIndex = 1, destinationIndex = 2, distanceMeters = 9, durationMinutes = 1)
          )
        )
        assertZIO(ZIO.service[DataStructureBuildService].flatMap(_.createInput(
          depotIndex = 1,
          inputMatrix,
          dimension = 3,
          vehicles = List()
        ).flatMap(i => ZIO.succeed(i.fullMatrix.flatten.toList))))(Assertion.equalTo(
          Array(
            Array(0l, 12l, 11l),
            Array(12l, 0l, 9l),
            Array(11l, 9l, 0l)).flatten.toList))
      }.provide(DataStructureBuildServiceImpl.zLayer, locationServiceMockZLayer, AppConfig.zLayer),
      test("build combinations 3 customers and depot with zero index and random durations") {
        val inputMatrix = Matrix(
          entities = List(
            LocationEntity(index = 1, location = GeoLocation(0,0), name = "cust_0", uid = "0", entityType = EntityType.Customer, weightInGramConstraint = 1),
            LocationEntity(index = 0, location = GeoLocation(0,0), name = "depot_1", uid = "1", entityType = EntityType.Depot, weightInGramConstraint = 0),
            LocationEntity(index = 2, location = GeoLocation(0,0), name = "cust_1", uid = "2", entityType = EntityType.Customer, weightInGramConstraint = 1),
            LocationEntity(index = 3, location = GeoLocation(0,0), name = "cust_2", uid = "3", entityType = EntityType.Customer, weightInGramConstraint = 1),
          ),
          results = List(
            MatrixPosition(originIndex = 0, destinationIndex = 1, distanceMeters = 12, durationMinutes = 1),
            MatrixPosition(originIndex = 0, destinationIndex = 2, distanceMeters = 11, durationMinutes = 1),
            MatrixPosition(originIndex = 1, destinationIndex = 2, distanceMeters = 9, durationMinutes = 1),
            MatrixPosition(originIndex = 0, destinationIndex = 3, distanceMeters = 9, durationMinutes = 1),
            MatrixPosition(originIndex = 1, destinationIndex = 3, distanceMeters = 9, durationMinutes = 1),
            MatrixPosition(originIndex = 0, destinationIndex = 3, distanceMeters = 7, durationMinutes = 1),
            MatrixPosition(originIndex = 2, destinationIndex = 3, distanceMeters = 9, durationMinutes = 1),
          )
        )
        assertZIO(ZIO.service[DataStructureBuildService].flatMap(_.createInput(
          depotIndex = 0,
          inputMatrix,
          dimension = 4,
          vehicles = List()
        ).flatMap(i => ZIO.succeed(i.fullMatrix.flatten.toList))))(Assertion.equalTo(
          Array(
            Array(0l, 12l, 11l, 7l),
            Array(12l, 0l, 9l, 9l),
            Array(11l, 9l, 0l, 9l),
            Array(7l, 9l, 9l, 0l)).flatten.toList))
      }.provide(DataStructureBuildServiceImpl.zLayer, locationServiceMockZLayer, AppConfig.zLayer)
    ),
    suite("Build")(
      test("build simple test 2 batch rest API calls") {
        val input = List(
          LocationEntity(index = 0, location = GeoLocation(latitude = 40, longitude = 43), name = "depot", uid = "0", entityType = EntityType.Depot, weightInGramConstraint = 0),
          LocationEntity(index = 1, location = GeoLocation(latitude = 40, longitude = 42), name = "cust_1", uid = "1", entityType = EntityType.Customer, weightInGramConstraint = 1),
          LocationEntity(index = 2, location = GeoLocation(latitude = 40, longitude = 41), name = "cust_2", uid = "2", entityType = EntityType.Customer, weightInGramConstraint = 1)
        )
        for
          service      <- ZIO.service[DataStructureBuildService]
          result       <- service.build(input)
          filtered     <- ZIO.succeed(result.results)
        yield assertTrue(filtered == List(
          MatrixPosition(originIndex = 0, destinationIndex = 1, distanceMeters = 1, durationMinutes = 1),
          MatrixPosition(originIndex = 0, destinationIndex = 2, distanceMeters = 1, durationMinutes = 1),
          MatrixPosition(originIndex = 1, destinationIndex = 2, distanceMeters = 2, durationMinutes = 2)
        ))
      }.provide(DataStructureBuildServiceImpl.zLayer, locationServiceMockZLayer, AppConfig.zLayer),
      test("build simple test multiple batch rest API calls") {
        val input = List(
          LocationEntity(index = 0, location = GeoLocation(latitude = 40, longitude = 43), name = "depot", uid = "0", entityType = EntityType.Depot, weightInGramConstraint = 0),
          LocationEntity(index = 1, location = GeoLocation(latitude = 40, longitude = 42), name = "cust_1", uid = "1", entityType = EntityType.Customer, weightInGramConstraint = 1),
          LocationEntity(index = 2, location = GeoLocation(latitude = 40, longitude = 41), name = "cust_2", uid = "2", entityType = EntityType.Customer, weightInGramConstraint = 1),
          LocationEntity(index = 3, location = GeoLocation(latitude = 40, longitude = 44), name = "cust_3", uid = "3", entityType = EntityType.Customer, weightInGramConstraint = 1)
        )
        for
          service      <- ZIO.service[DataStructureBuildService]
          result       <- service.build(input)
          filtered     <- ZIO.succeed(result.results)
        yield assertTrue(filtered == List(
          MatrixPosition(originIndex = 0, destinationIndex = 1, distanceMeters = 1, durationMinutes = 1),
          MatrixPosition(originIndex = 0, destinationIndex = 2, distanceMeters = 1, durationMinutes = 1),
          MatrixPosition(originIndex = 0, destinationIndex = 3, distanceMeters = 1, durationMinutes = 1),
          MatrixPosition(originIndex = 1, destinationIndex = 2, distanceMeters = 2, durationMinutes = 2),
          MatrixPosition(originIndex = 1, destinationIndex = 3, distanceMeters = 2, durationMinutes = 2),
          MatrixPosition(originIndex = 2, destinationIndex = 3, distanceMeters = 3, durationMinutes = 3)
        ))
      }.provide(DataStructureBuildServiceImpl.zLayer, locationServiceMockZLayer, AppConfig.zLayer)
    )
  )
}









