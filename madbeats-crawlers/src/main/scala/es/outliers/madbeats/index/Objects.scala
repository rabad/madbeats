package es.outliers.madbeats.index

import java.util.Date

/**
 * Created by dataclimber on 03/02/15.
 */
case class Author(id: String, name: String, screen_name: String, profile_pic: String)
case class Venue(id: String, name: String, country: String, cc: String, coord: List[Double], categories: List[Map[String, String]])

// Swarm
case class Checkin(id: String, timestamp: Date, author: Author, venue: Venue)

// Twitter
case class Tweet(id: String, timestamp: Date, text: String, url: String, coord: List[Double], author: Author)

// Traffic
case class TrafficMeasure(id: String, pm_id: String, timestamp: Date, intensity: Int, load: Int, occupation: Int, coord: List[Double])

// Flights
case class Flight(timestamp: Date, model: String, tail: String, callsign: String, coord: List[Double], altitude: Int,
  heading: Int, speed: Int, flightNo: String, route:String)

// Instagram
case class InstagramObj(id: String, timestamp: Date, url: String, photo: String, coord: List[Double], author: Author)