package es.outliers.madbeats.utils

/**
 * Created by dataclimber on 31/01/15.
 */

// Foursquare API Venue JSON
case class Meta(code: Option[Double])

case class Contact(phone: Option[String], formattedPhone: Option[String], twitter: Option[String])

case class Location(address: Option[String], crossStreet: Option[String], lat: Option[Double], lng: Option[Double], postalCode: Option[String], cc: Option[String],
                    city: Option[String], state: Option[String], country: Option[String], formattedAddress: Option[List[String]])

case class Icon(prefix: Option[String], suffix: Option[String])

case class Categories(id: Option[String], name: Option[String], pluralName: Option[String], shortName: Option[String], icon: Option[Icon], primary: Option[Boolean])

case class Stats(checkinsCount: Option[Double], usersCount: Option[Double], tipCount: Option[Double])

case class Likes(count: Option[Double], groups: Option[List[Groups]], summary: Option[String])

case class Specials(count: Option[Double], items: Option[List[Items]])

case class Source(name: Option[String], url: Option[String])

case class User(id: Option[String], firstName: Option[String], lastName: Option[String], gender: Option[String], photo: Option[Icon])

case class Items(id: Option[String], createdAt: Option[Double], source: Option[Source], prefix: Option[String], suffix: Option[String], width: Option[Double],
                 height: Option[Double], user: Option[User], visibility: Option[String])

case class Groups(`type`: Option[String], name: Option[String], count: Option[Double], items: Option[List[Items] ])

case class Photos(count: Option[Double], groups: Option[List[Groups]])

case class Mayor(count: Option[Double])

case class Entities(indices: Option[List[Double]], `type`: Option[String])

case class Sample(entities: Option[List[Entities]], text: Option[String])

case class Phrases(phrase: Option[String], sample: Option[Sample], count: Option[Double])

case class Attributes(groups: Option[List[Groups]])

case class Venue(id: Option[String], name: Option[String], contact: Option[Contact], location: Option[Location], canonicalUrl: Option[String], categories: Option[List[Categories]],
                 verified: Option[Boolean], stats: Option[Stats], url: Option[String], likes: Option[Likes], dislike: Option[Boolean], ok: Option[Boolean], rating: Option[Double],
                 ratingSignals: Option[Double], specials: Option[Specials], photos: Option[Photos], hereNow: Option[Likes], reasons: Option[Specials], createdAt: Option[Double],
                 mayor: Option[Mayor], tips: Option[Photos], tags: Option[List[String]], shortUrl: Option[String], timeZone: Option[String], listed: Option[Photos],
                 phrases: Option[List[Phrases]], pageUpdates: Option[Specials], inbox: Option[Specials], attributes: Option[Attributes])

case class Response(venue: Option[Venue])

case class VenueResponse(meta: Option[Meta], response: Option[Response])


// Traffic measure points
case class MeasurePoint(lat: Float, lng: Float)

case class MeasurePoints(pms: Map[String, MeasurePoint])

// Flights data
case class FlightsResponse(planes: List[Map[String, List[String]]], isPartial: Boolean)