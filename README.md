# zio-vrs
An implementation of the vehicle routing problem in the ZIO scala ecosystem using the Google OR optimisation tools and the RADAR API

## Introduction

This project is an implementation of the [Vehicle Routing problem](https://en.wikipedia.org/wiki/Vehicle_routing_problem) using the Scala 3 ZIO 2.0 ecosystem.

The archtecture is the following:
- a REST service exposed via ZIO HTTP and ZIO JSON
- a backend client to the Radar API for retrieving distances matrices via [Radar.com] (https://radar.com/)
- an optimization module that uses the [google OR tools] (https://developers.google.com/optimization) 

## Vehicle routing problem

The Vehicle Routing Problem (VRP) optimizes the routes of delivery trucks, cargo lorries, public transportation (buses, taxis and airplanes) or technicians on the road, by improving the order of the visits. This routing optimization heavily reduces driving time and fuel consumption compared to manual planning.

## Radar API

To optimize we need distances between the depot and the customers. In this implementation we are using the [Radar.com] (https://radar.com/) API for getting distance matrices. The free account is limited with 100,000 monthly calls and you can pass the key as an environment variable with the name GEO_KEY to the application / docker image.

## Building 

### Clean

sbt clean

### Compile

sbt compile

### Assemble fat jar

sbt assembly

### Create a docker container for linux platform using the docker file

docker buildx build --platform linux/amd64 -f ./project/dockerfile -t zio-vrs:1.0 .

Remark : The -- platform paramater is needed for build the image from an apple macbook

### Run the container

docker run -d --name zio-vrs --restart unless-stopped -e "GEO_KEY=?" zio-vrs:1.0

Remark : Replace ? with radar API key

## API explanation

The optimisation expects the following input:

- A list of customers and each customer has a demand of X grams, a unique uid and a geolocation
- The location of the depot, a uid and the geolocation of the depot
- The fleet information: For each vehicle we expect a capacity in grams, a driver name and a unique identifier
- General constraints:
  - Maximum stops for a vehicle for one day
  - Maximum kilometers for a vehicle for one day
- The maximum seconds that can be used for optimising the problem

## Example request / response

Request:

```json
{
    "locations": [
        {
            "location": {
                "latitude": 51.24601563591366,
                "longitude": 4.416294928800372
            },
            "name": "customer_1",
            "uid": "c_1",
            "weightInGramConstraint": 20000
        },
        {
            "location": {
                "latitude": 50.93021966123263,
                "longitude": 5.36893267535137
            },
            "name": "customer_2",
            "uid": "c_2",
            "weightInGramConstraint": 30000
        },
        {
            "location": {
                "latitude": 51.10163546963735,
                "longitude": 5.789792511606002
            },
            "name": "customer_3",
            "uid": "c_3",
            "weightInGramConstraint": 70000
        }
    ],
    "depotLocation": {
        "location": {
            "latitude": 50.907600055332146,
            "longitude": 5.345893584612312
        },
        "name": "depot",
        "uid": "d_1"
    },
    "vehicles": [
        {
            "vehicleIdentifier": "small_truck_0002",
            "driverName" : "Pierre",
            "capacityInGrams": 4000000
        },
        {
            "vehicleIdentifier": "small_car_0003",
            "driverName" : "Jef",
            "capacityInGrams": 200000
        }
    ],
    "maxCustomerStops": 10,
    "maxKm": 400,
    "secondsOptimizationLimit": 60
}
```

Response:


```json
{
    "objectiveValue": 16945094,
    "usedVehicleCount": 2,
    "totalKm": 267.0,
    "availableVehicleCount": 2,
    "maxKmVehicle": 400,
    "maxCustomerStopCount": 10,
    "routes": [
        {
            "vehicleId": 0,
            "vehicleIdentifier": "small_truck_0002",
            "driverName": "Pierre",
            "distanceKm": 100.0,
            "customerStopCount": 2,
            "tour": [
                {
                    "location": {
                        "latitude": 50.9076,
                        "longitude": 5.3458934
                    },
                    "name": "depot",
                    "uid": "d_1",
                    "weightInGramConstraint": 0
                },
                {
                    "location": {
                        "latitude": 51.101635,
                        "longitude": 5.7897925
                    },
                    "name": "customer_3",
                    "uid": "c_3",
                    "weightInGramConstraint": 70000
                },
                {
                    "location": {
                        "latitude": 50.93022,
                        "longitude": 5.3689327
                    },
                    "name": "customer_2",
                    "uid": "c_2",
                    "weightInGramConstraint": 30000
                },
                {
                    "location": {
                        "latitude": 50.9076,
                        "longitude": 5.3458934
                    },
                    "name": "depot",
                    "uid": "d_1",
                    "weightInGramConstraint": 0
                }
            ],
            "totalWeightInGrams": 100000,
            "vehicleCapacityInGrams": 4000000
        },
        {
            "vehicleId": 1,
            "vehicleIdentifier": "small_car_0003",
            "driverName": "Jef",
            "distanceKm": 166.0,
            "customerStopCount": 1,
            "tour": [
                {
                    "location": {
                        "latitude": 50.9076,
                        "longitude": 5.3458934
                    },
                    "name": "depot",
                    "uid": "d_1",
                    "weightInGramConstraint": 0
                },
                {
                    "location": {
                        "latitude": 51.246017,
                        "longitude": 4.416295
                    },
                    "name": "customer_1",
                    "uid": "c_1",
                    "weightInGramConstraint": 20000
                },
                {
                    "location": {
                        "latitude": 50.9076,
                        "longitude": 5.3458934
                    },
                    "name": "depot",
                    "uid": "d_1",
                    "weightInGramConstraint": 0
                }
            ],
            "totalWeightInGrams": 20000,
            "vehicleCapacityInGrams": 200000
        }
    ],
    "durationMinutes": 0
}
```
