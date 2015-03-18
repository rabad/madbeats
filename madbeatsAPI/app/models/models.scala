package models

import java.util.Date

/**
 * Created by dataclimber on 05/02/15.
 */

// Common
case class Location(lat: Double, lng: Double)
case class User(id: String, name: String, screen_name: String, profile_pict: String)

// Twitter endpint
case class Tweet(id: String, url: String, timestamp: Date, text: String, author: User, location: Location)

// Swarm endpoint
case class Venue(id: String, name: String, location: Location)
case class Checkin(id: String, timestamp: Date, author: User, venue: Venue)

// Traffic response
case class TrafficMeasure(id: String, timestamp: Date, intensity: Int, location: Location)

// Flight response
case class Flight(id: String, timestamp: Date, location: Location, heading: Int)

// Instagram response
case class Instagram(id: String, url: String, timestamp: Date, photo: String, author: User, location: Location)