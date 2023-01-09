import distance.Model
import distance.Model.*
import program.Main.AppConfig
import services.{DataStructureBuildServiceImpl, RadarLocationServiceImpl}
import util.JsonUtils
import util.Utils
import zio.*
import zio.test.*

object UtilsTesting extends ZIOSpecDefault {

  def spec = suite("ZIO utils testing suite")(
    suite("Utils")(
      test("build combinations one element") {
        assert(Utils.buildRelationShips(1))(Assertion.equalTo(
          Map()
        ))
      },
      test("build combinations two elements") {
        assert(Utils.buildRelationShips(2))(Assertion.equalTo(
          Map(0 -> List(LocationRelationCombination(0,1)))
        ))
      },
      test("build combinations three elements") {
        assert(Utils.buildRelationShips(3))(Assertion.equalTo(
          Map(
            0 -> List(LocationRelationCombination(0,1), LocationRelationCombination(0,2)),
            1 -> List(LocationRelationCombination(1,2))
          )
        ))
      },
      test("build combinations four elements") {
        assert(Utils.buildRelationShips(4))(Assertion.equalTo(
          Map(
            0 -> List(LocationRelationCombination(0,1), LocationRelationCombination(0,2), LocationRelationCombination(0,3)),
            1 -> List(LocationRelationCombination(1,2), LocationRelationCombination(1,3)),
            2 -> List(LocationRelationCombination(2,3))
          )
        ))
      },
      test("find exact match") {
        assert(Utils.findMatch(
          latitude = 1f,
          longitude = 1f,
          locations = List(LocationEntity(0, GeoLocation(1f,1f),"dummy","dummyuid",EntityType.Customer,0l))
          )
        )(Assertion.equalTo(0))
      },
      test("find match") {
        assert(Utils.findMatch(
          latitude = 1.11112,
          longitude = 1.11112,
          locations = List(LocationEntity(0, GeoLocation(1.11112,1.11112),"dummy","dummyuid",EntityType.Customer,0l))
        )
        )(Assertion.equalTo(0))
      },
      test("find match within precision") {
        assert(Utils.findMatch(
          latitude = 1.111124,
          longitude = 1.111124,
          locations = List(LocationEntity(0, GeoLocation(1.111123,1.111123),"dummy","dummyuid",EntityType.Customer,0l))
        )
        )(Assertion.equalTo(0))
      },
      test("validate input - empty input") {
        assert(Utils.validateInput(List()))(Assertion.equalTo(false))
      },
      test("validate input - one customer") {
        assert(Utils.validateInput(List(
          LocationEntity(index = 0, location = GeoLocation(1f,1f), name = "0", uid = "0", entityType = EntityType.Customer, weightInGramConstraint = 0)
        )))(Assertion.equalTo(false))
      },
      test("validate input - no depot / 3 customers") {
        assert(Utils.validateInput(List(
          LocationEntity(index = 0, location = GeoLocation(1f,1f), name = "0", uid = "0", entityType = EntityType.Customer, weightInGramConstraint = 0),
          LocationEntity(index = 1, location = GeoLocation(1.1,1f), name = "1", uid = "1", entityType = EntityType.Customer, weightInGramConstraint = 0),
          LocationEntity(index = 2, location = GeoLocation(1.2,1f), name = "2", uid = "2", entityType = EntityType.Customer, weightInGramConstraint = 0),
        )))(Assertion.equalTo(false))
      },
      test("validate input - depot with invalid weight") {
        assert(Utils.validateInput(List(
          LocationEntity(index = 0, location = GeoLocation(1f,1f), name = "0", uid = "0", entityType = EntityType.Customer, weightInGramConstraint = 0),
          LocationEntity(index = 1, location = GeoLocation(1.1,1f), name = "1", uid = "1", entityType = EntityType.Customer, weightInGramConstraint = 0),
          LocationEntity(index = 2, location = GeoLocation(1.2,1f), name = "2", uid = "2", entityType = EntityType.Depot, weightInGramConstraint = 1),
        )))(Assertion.equalTo(false))
      },
      test("validate input - basic valid input") {
        assert(Utils.validateInput(List(
          LocationEntity(index = 0, location = GeoLocation(1f,1f), name = "0", uid = "0", entityType = EntityType.Customer, weightInGramConstraint = 1),
          LocationEntity(index = 1, location = GeoLocation(1.1,1f), name = "1", uid = "1", entityType = EntityType.Customer, weightInGramConstraint = 1),
          LocationEntity(index = 2, location = GeoLocation(1.2,1f), name = "2", uid = "2", entityType = EntityType.Depot, weightInGramConstraint = 0),
        )))(Assertion.equalTo(true))
      },
      test("validate input - invalid geo location") {
        assert(Utils.validateInput(List(
          LocationEntity(index = 0, location = GeoLocation(-400f,1f), name = "0", uid = "0", entityType = EntityType.Customer, weightInGramConstraint = 1),
          LocationEntity(index = 1, location = GeoLocation(1.1,1f), name = "1", uid = "1", entityType = EntityType.Customer, weightInGramConstraint = 1),
          LocationEntity(index = 2, location = GeoLocation(1.2,1f), name = "2", uid = "2", entityType = EntityType.Depot, weightInGramConstraint = 0),
        )))(Assertion.equalTo(false))
      },
      test("validate input - duplicate uid") {
        assert(Utils.validateInput(List(
          LocationEntity(index = 0, location = GeoLocation(-400f,1f), name = "0", uid = "0", entityType = EntityType.Customer, weightInGramConstraint = 1),
          LocationEntity(index = 1, location = GeoLocation(1.1,1f), name = "0", uid = "1", entityType = EntityType.Customer, weightInGramConstraint = 1),
          LocationEntity(index = 2, location = GeoLocation(1.2,1f), name = "2", uid = "2", entityType = EntityType.Depot, weightInGramConstraint = 0),
        )))(Assertion.equalTo(false))
      },
      test("validate input - duplicate geo location") {
        assert(Utils.validateInput(List(
          LocationEntity(index = 0, location = GeoLocation(-400f,1f), name = "0", uid = "0", entityType = EntityType.Customer, weightInGramConstraint = 1),
          LocationEntity(index = 1, location = GeoLocation(1.111112,1f), name = "0", uid = "1", entityType = EntityType.Customer, weightInGramConstraint = 1),
          LocationEntity(index = 2, location = GeoLocation(1.111113,1f), name = "2", uid = "2", entityType = EntityType.Depot, weightInGramConstraint = 0),
        )))(Assertion.equalTo(false))
      },
      test("validate matrix non zero diagonal") {
        assert(Utils.validateInputMatrix(Array(Array(1l,2l,2l),Array(1l,0l,1l),Array(1l,2l,0l)),0,0))(Assertion.equalTo(false))
      },
      test("validate matrix zero on none diagonal") {
        assert(Utils.validateInputMatrix(Array(Array(0l,1l,1l),Array(1l,0l,1l),Array(1l,0l,0l)),0,0))(Assertion.equalTo(false))
      },
      test("validate matrix ok") {
        assert(Utils.validateInputMatrix(Array(Array(0l,1l,1l),Array(1l,0l,1l),Array(1l,1l,0l)),0,0))(Assertion.equalTo(true))
      }
    )
  )
}









