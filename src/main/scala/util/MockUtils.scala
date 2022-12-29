package util

import distance.Model.*

object MockUtils:
  def generateVehiclesCapacity: List[Long] = List(20000, 20000, 20000,40000)

  def generateMockInput: List[LocationEntity] =
    val depot = LocationEntity(
      index = 0,
      location = GeoLocation(50.907600055332146, 5.345893584612312),
      name = "VOOGDIJSTRAAT_HASSELT_DEPOT",
      uid = "0",
      entityType = EntityType.Depot,
      weightInGramConstraint = 0)
    val loc1 = LocationEntity(
      index = 1,
      location = GeoLocation(51.02348572185795, 5.744620167426376),
      name = "MAASSTRAAT_STOKKEM_CUSTOMER_1",
      uid = "1",
      entityType = EntityType.Customer,
      weightInGramConstraint = 1000)
    val loc2 = LocationEntity(
      index = 2,
      location = GeoLocation(50.9741100812702, 5.68226369810818),
      name = "KONING_ALBERTLAAN_162_MAASMECHELEN_CUSTOMER_2",
      uid = "2",
      entityType = EntityType.Customer,
      weightInGramConstraint = 1000)
    val loc3 = LocationEntity(
      index = 3,
      location = GeoLocation(50.9673920156866, 5.69489909810796),
      name = "KONING_ALBERTLAAN_12_MAASMECHELEN_CUSTOMER_3",
      uid = "3",
      entityType = EntityType.Customer,
      weightInGramConstraint = 5000)
    val loc4 = LocationEntity(
      index = 4,
      location = GeoLocation(50.970311722582736, 5.681796998108048),
      name = "OUDE_BAAN_MAASMECHELEN_CUSTOMER_4",
      uid = "4",
      entityType = EntityType.Customer,
      weightInGramConstraint = 1000)
    val loc5 = LocationEntity(
      index = 5,
      location = GeoLocation(50.9982009080772, 5.227733861366796),
      name = "KLAVERBLAD_LUMMEN_CUSTOMER_5",
      uid = "5",
      entityType = EntityType.Customer,
      weightInGramConstraint = 1000)
    val loc6 = LocationEntity(
      index = 6,
      location = GeoLocation(51.052732396391406, 5.734710684616973),
      name = "KBC_ROTEM_CUSTOMER_6",
      uid = "6",
      entityType = EntityType.Customer,
      weightInGramConstraint = 1000)
    val loc7 = LocationEntity(
      index = 7,
      location = GeoLocation(50.915826897946, 5.6839159692708705),
      name = "BASIC_FIT_LANAKEN_CUSTOMER_7",
      uid = "7",
      entityType = EntityType.Customer,
      weightInGramConstraint = 1000)
    val loc8 = LocationEntity(
      index = 8,
      location = GeoLocation(50.857374495082766, 5.659653798104413),
      name = "FORCADENTSTRAAT_MAASTRICHT_CUSTOMER_8",
      uid = "8",
      entityType = EntityType.Customer,
      weightInGramConstraint = 1000)
    val loc9 = LocationEntity(
      index = 9,
      location = GeoLocation(51.24601563591366, 4.416294928800372),
      name = "KINEPOLIS_ANTWERPEN_CUSTOMER_9",
      uid = "9",
      entityType = EntityType.Customer,
      weightInGramConstraint = 5000)
    List(depot, loc1, loc2, loc3, loc4, loc5, loc6, loc7, loc8, loc9)



