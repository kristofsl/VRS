import distance.Model.*
import program.Main.AppConfig
import services.{DataStructureBuildServiceImpl, RadarLocationServiceImpl}
import util.JsonUtils
import util.Utils
import util.JsonUtils.{JsDestination, JsDistance, JsDuration, JsMatrixPosition, JsMatrixResponse, JsOrigin}
import zio.*
import zio.test.*

object UtilsTesting extends ZIOSpecDefault {

  def spec = suite("ZIO utils testing suite")(

    suite("Utils")(
      test("build location string") {
        assert(Utils.buildLocationString(GeoLocation.create(41.40338, 2.17403).get))(Assertion.equalTo("41.40338,2.17403"))
      },
      test("append url single location") {
        assert(RadarLocationServiceImpl(AppConfig(key = "")).appendLocation("https://api.radar.io/v1/route/matrix?origins=",Array(GeoLocation.create(41.40338, 2.17403).get),true))(Assertion.equalTo("https://api.radar.io/v1/route/matrix?origins=41.40338,2.17403"))
      },
      test("append url multiple locations") {
        assert(RadarLocationServiceImpl(AppConfig(key = "")).appendLocation("https://api.radar.io/v1/route/matrix?origins=",Array(GeoLocation.create(41.40338, 2.17403).get, GeoLocation.create(41.40338, 2.17403).get),true))(Assertion.equalTo("https://api.radar.io/v1/route/matrix?origins=41.40338,2.17403%7C41.40338,2.17403"))
      },
      test("build full URL single location parameters") {
        assertZIO(RadarLocationServiceImpl(AppConfig(key = "")).buildMapUrl(List(GeoLocation.create(41.40338, 2.17403).get), List(GeoLocation.create(42.40338, 2.17403).get)))(Assertion.equalTo("https://api.radar.io/v1/route/matrix?origins=41.40338,2.17403&destinations=42.40338,2.17403&mode=car&units=metric"))
      },
      test("build full URL multiple location parameters") {
        assertZIO(RadarLocationServiceImpl(AppConfig(key = "")).buildMapUrl(List(GeoLocation.create(41.40338, 2.17403).get,GeoLocation.create(42.40338, 2.17403).get), List(GeoLocation.create(42.40338, 2.17403).get, GeoLocation.create(41.40338, 2.17403).get)))(Assertion.equalTo("https://api.radar.io/v1/route/matrix?origins=41.40338,2.17403%7C42.40338,2.17403&destinations=42.40338,2.17403%7C41.40338,2.17403&mode=car&units=metric"))
      },
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
      test("validate valid input") {
        assert(Utils.validateInput(List(
          LocationEntity(
          index = 0,
          location = GeoLocation.create(41,2).get,
          name = "DEPOT",
          uid = "0",
          entityType = EntityType.Depot,
          weightInGramConstraint = 0),
          LocationEntity(
            index = 1,
            location = GeoLocation.create(41,2).get,
            name = "C_1",
            uid = "1",
            entityType = EntityType.Customer,
            weightInGramConstraint = 1),
          LocationEntity(
            index = 2,
            location = GeoLocation.create(41,2).get,
            name = "C_2",
            uid = "2",
            entityType = EntityType.Customer,
            weightInGramConstraint = 1),
        )))(Assertion.equalTo(
          true
        ))
      },
      test("validate invalid input") {
        assert(Utils.validateInput(List(
          LocationEntity(
            index = 0,
            location = GeoLocation.create(41,2).get,
            name = "DEPOT",
            uid = "0",
            entityType = EntityType.Depot,
            weightInGramConstraint = 1),
          LocationEntity(
            index = 1,
            location = GeoLocation.create(41,2).get,
            name = "C_1",
            uid = "1",
            entityType = EntityType.Customer,
            weightInGramConstraint = 1),
          LocationEntity(
            index = 2,
            location = GeoLocation.create(41,2).get,
            name = "C_2",
            uid = "2",
            entityType = EntityType.Customer,
            weightInGramConstraint = 1),
        )))(Assertion.equalTo(
          false
        ))
      },
      test("validate invalid input") {
        assert(Utils.validateInput(List(
          LocationEntity(
            index = 0,
            location = GeoLocation.create(41,2).get,
            name = "DEPOT",
            uid = "0",
            entityType = EntityType.Depot,
            weightInGramConstraint = 0),
          LocationEntity(
            index = 1,
            location = GeoLocation.create(41,2).get,
            name = "C_1",
            uid = "1",
            entityType = EntityType.Depot,
            weightInGramConstraint = 1),
          LocationEntity(
            index = 2,
            location = GeoLocation.create(41,2).get,
            name = "C_2",
            uid = "2",
            entityType = EntityType.Customer,
            weightInGramConstraint = 1),
        )))(Assertion.equalTo(
          false
        ))
      }
    ),
    suite("Json")(
      test("parse matrix API") {
        assertZIO(JsonUtils.parseMatrixResponse("""{
                                               |  "meta": {
                                               |    "code": 200
                                               |  },
                                               |  "origins": [
                                               |    {
                                               |      "latitude": 40.78382,
                                               |      "longitude": -73.97536
                                               |    }
                                               |  ],
                                               |  "destinations": [
                                               |    {
                                               |      "latitude": 40.70390,
                                               |      "longitude": -73.98670
                                               |    },
                                               |    {
                                               |      "latitude": 40.73237,
                                               |      "longitude": -73.94884
                                               |    }
                                               |  ],
                                               |  "matrix": [
                                               |    [
                                               |      {
                                               |        "distance": {
                                               |          "value": 42480.31,
                                               |          "text": "8.0 mi"
                                               |        },
                                               |        "duration": {
                                               |          "value": 19.9,
                                               |          "text": "20 mins"
                                               |        },
                                               |        "originIndex": 0,
                                               |        "destinationIndex": 0
                                               |      },
                                               |      {
                                               |        "distance": {
                                               |          "value": 31108.92,
                                               |          "text": "5.8 mi"
                                               |        },
                                               |        "duration": {
                                               |          "value": 17.13,
                                               |          "text": "17 mins"
                                               |        },
                                               |        "originIndex": 0,
                                               |        "destinationIndex": 1
                                               |      }
                                               |    ]
                                               |  ]
                                               |}""".stripMargin))(Assertion.equalTo(
          JsMatrixResponse(
            origins = List(
              JsOrigin(
              latitude = 40.78382,
              longitude = -73.97536
              )
            ),
            destinations = List(
              JsDestination(
              latitude = 40.7039,
              longitude = -73.9867
              ),
              JsDestination(
              latitude = 40.73237,
              longitude = -73.94884
              )
            ),
            matrix = List(
              JsMatrixPosition(
              originIndex = 0,
              destinationIndex = 0,
              distance = JsDistance(
                value = 42480.31,
                text = "8.0 mi"
              ),
              duration = JsDuration(
                value = 19.9,
                text = "20 mins"
              )
            ), JsMatrixPosition(
              originIndex = 0,
              destinationIndex = 1,
              distance = JsDistance(
                value = 31108.92,
                text = "5.8 mi"
              ),
              duration = JsDuration(
                value = 17.13,
                text = "17 mins"
              )
            )))))
      }
    )
  )
}










