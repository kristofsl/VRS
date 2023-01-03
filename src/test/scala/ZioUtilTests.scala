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
      }
    )
  )
}










