package util

import distance.Model.*
import zio.{Task, UIO, ZIO}
import java.net.URI
import scala.collection.mutable.ArrayBuffer

object Utils:
  def buildLocationString(location: GeoLocation): String = s"${location.latitude},${location.longitude}"

  def buildUri(url: String): Task[URI] = ZIO.attempt(URI(url))

  def findMatch(latitude: Float, longitude: Float, locations: List[LocationEntity]): Int =
    locations.filter(l => compare(l.location.longitude, longitude) && compare(l.location.latitude, latitude)).last.index

  def compare(f1: Float, f2: Float, precision: Float = 0.0001) =
    if ((f1 - f2).abs < precision) true else false

  // build the combinations, prevent unneeded reversed combinations and group the results by origin
  def buildRelationShips(maxEntities: Int): Map[Int, List[LocationRelationCombination]] =
    val result: ArrayBuffer[LocationRelationCombination] = ArrayBuffer()
    for
      x <- Range(0, maxEntities)
      y <- Range(0, maxEntities)
    do {
      if !result.exists(combination => combination.index1 == x && combination.index2 == y)
        && !result.exists(combination => combination.index1 == y && combination.index2 == x)
        && x != y
      then
        result.addOne(LocationRelationCombination(index1 = x, index2 = y))
      end if
    }
    result.toList.groupBy(_.index1)

  def validateSolution(solution: Solution): Boolean =
    !solution.routes.map(_.distanceMeters * 1000).exists(_ > solution.maxKmVehicle)
      && !solution.routes.exists(r => r.distanceMeters > solution.vehicleCapacity(r.vehicleId))


  def validateInput(entities: List[LocationEntity]): Boolean =
    entities.length > 2
      && entities.exists(_.entityType == EntityType.Depot)
      && entities.count(l => l.entityType == EntityType.Depot) == 1
      && entities.filter(l => l.entityType == EntityType.Depot).last.weightInGramConstraint == 0
      && !entities.map(_.location.longitude).exists(_ < -180)
      && !entities.map(_.location.longitude).exists(_ > 180)
      && !entities.map(_.location.latitude).exists(_ < -90)
      && !entities.map(_.location.latitude).exists(_ > 90)

