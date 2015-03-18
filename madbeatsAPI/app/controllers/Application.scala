package controllers

import java.util.{ArrayList, HashMap, Calendar, Date}

import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.{ElasticClient, ElasticsearchClientUri, SearchType}
import models._
import play.api.Play.current
import play.api.libs.functional.syntax._
import play.api.libs.json._
import play.api.mvc._
import play.api.Logger
import scala.collection.mutable
import scala.collection.JavaConverters._

object Application extends Controller {

  implicit val locationWrites: Writes[Location] = (
    (JsPath \ "lat").write[Double] and
      (JsPath \ "lng").write[Double]
    )(unlift(Location.unapply))

  implicit val userWrites: Writes[User] = (
    (JsPath \ "id").write[String] and
      (JsPath \ "name").write[String] and
      (JsPath \ "screen_name").write[String] and
      (JsPath \ "profile_pict").write[String]
    )(unlift(User.unapply))

  implicit val tweetWrites: Writes[Tweet] = (
    (JsPath \ "id").write[String] and
      (JsPath \ "url").write[String] and
      (JsPath \ "timestamp").write[Date] and
      (JsPath \ "text").write[String] and
      (JsPath \ "author").write[User] and
      (JsPath \ "location").write[Location]
    )(unlift(Tweet.unapply))

  implicit val instagramWrites: Writes[Instagram] = (
    (JsPath \ "id").write[String] and
      (JsPath \ "url").write[String] and
      (JsPath \ "timestamp").write[Date] and
      (JsPath \ "photo").write[String] and
      (JsPath \ "author").write[User] and
      (JsPath \ "location").write[Location]
    )(unlift(Instagram.unapply))

  implicit val venueWrites: Writes[Venue] = (
    (JsPath \ "id").write[String] and
      (JsPath \ "name").write[String] and
      (JsPath \ "location").write[Location]
    )(unlift(Venue.unapply))

  implicit val checkinWrites: Writes[Checkin] = (
    (JsPath \ "id").write[String] and
      (JsPath \ "timestamp").write[Date] and
      (JsPath \ "author").write[User] and
      (JsPath \ "venue").write[Venue]
    )(unlift(Checkin.unapply))

  implicit val trafficMeasureWrites: Writes[TrafficMeasure] = (
    (JsPath \ "id").write[String] and
      (JsPath \ "timestamp").write[Date] and
      (JsPath \ "intensity").write[Int] and
      (JsPath \ "location").write[Location]
    )(unlift(TrafficMeasure.unapply))

  implicit val flightWrites: Writes[Flight] = (
    (JsPath \ "id").write[String] and
      (JsPath \ "timestamp").write[Date] and
      (JsPath \ "location").write[Location] and
      (JsPath \ "heading").write[Int]
    )(unlift(Flight.unapply))
  
  val uri = ElasticsearchClientUri(current.configuration.getString("persistence.url").get)
  val client = ElasticClient.remote(uri)
  val index = current.configuration.getString("persistence.index").getOrElse("madbeats")

  def version = Action {
    Ok(Json.obj("version" ->"1.0", "timestamp" -> Calendar.getInstance.getTime ))
  }
  
  def traffic = Action {
    val resp = client.execute {
      search in index -> "traffic" size 5000 query {
        rangeQuery("timestamp") from "now-5m" to "now" includeUpper true
      } searchType SearchType.QueryThenFetch
    }.await
    Logger.debug(s"Traffic results found: ${resp.getHits.getTotalHits}")
    val measures = mutable.ListBuffer[TrafficMeasure]()
    for (hit <- resp.getHits.getHits) {
      val tmp = hit.getSource
      val coord = tmp.get("coord").asInstanceOf[ArrayList[Double]].asScala.toList
      val location = new Location(coord(1), coord(0))
      measures += new TrafficMeasure(tmp.get("pm_id").asInstanceOf[String],
        new java.util.Date(tmp.get("timestamp").asInstanceOf[Long] * 1000),
        tmp.get("intensity").asInstanceOf[Int],
        location)
    }
    val json = Json.toJson(measures)
    Ok(json)
  }
  
  def twitter = Action {
    val resp = client.execute {
      search in index -> "tweet" size 5000 query {
        rangeQuery("timestamp") from "now-5m" to "now" includeUpper true
      } searchType SearchType.QueryThenFetch
    }.await
    Logger.debug(s"Tweet results found: ${resp.getHits.getTotalHits}")
    val tweets = mutable.ListBuffer[Tweet]()
    for (hit <- resp.getHits.getHits) {
      val tmp = hit.getSource
      val coord = tmp.get("coord").asInstanceOf[ArrayList[Double]].asScala.toList
      val location = new Location(coord(1), coord(0))
      val tmpUser = tmp.get("author").asInstanceOf[HashMap[String, String]].asScala
      val author = new User(tmpUser.get("id").get, tmpUser.get("name").get,
        tmpUser.get("screen_name").get, tmpUser.get("profile_pic").get)
      tweets += new Tweet(tmp.get("id").asInstanceOf[String],
        tmp.get("url").asInstanceOf[String],
        new java.util.Date(tmp.get("timestamp").asInstanceOf[Long] * 1000),
        tmp.get("text").asInstanceOf[String],
        author,
        location)
    }
    val json = Json.toJson(tweets)
    Ok(json)
  }
  
  def swarm = Action {
    val resp = client.execute {
      search in index -> "swarm" size 5000 query {
        rangeQuery("timestamp") from "now-30m" to "now" includeUpper true
      } searchType SearchType.QueryThenFetch
    }.await
    Logger.debug(s"Swarm results found: ${resp.getHits.getTotalHits}")
    val checkins = mutable.ListBuffer[Checkin]()
    for (hit <- resp.getHits.getHits) {
      val tmp = hit.getSource
      val tmpVenue = tmp.get("venue").asInstanceOf[HashMap[String, Any]].asScala
      val tmpCoord = tmpVenue.get("coord").get
      if (tmpCoord != null) {
        val coord = tmpCoord.asInstanceOf[ArrayList[Double]].asScala.toList
        val location = new Location(coord(1), coord(0))
        val venue = new Venue(tmpVenue.get("id").get.asInstanceOf[String],
          tmpVenue.get("name").get.asInstanceOf[String],
          location)
        val tmpUser = tmp.get("author").asInstanceOf[HashMap[String, String]].asScala
        val author = new User(tmpUser.get("id").get, tmpUser.get("name").get,
          tmpUser.get("screen_name").get, tmpUser.get("profile_pic").get)
        checkins += new Checkin(tmp.get("id").asInstanceOf[String],
          new java.util.Date(tmp.get("timestamp").asInstanceOf[Long] * 1000),
          author,
          venue)
      }
    }
    val json = Json.toJson(checkins)
    Ok(json)
  }

  def instagram = Action {
    val resp = client.execute {
      search in index -> "instagram" size 5000 query {
        rangeQuery("timestamp") from "now-30m" to "now" includeUpper true
      } searchType SearchType.QueryThenFetch
    }.await
    Logger.debug(s"Instagram results found: ${resp.getHits.getTotalHits}")
    val photos = mutable.ListBuffer[Instagram]()
    for (hit <- resp.getHits.getHits) {
      val tmp = hit.getSource
      val coord = tmp.get("coord").asInstanceOf[ArrayList[Double]].asScala.toList
      val location = new Location(coord(1), coord(0))
      val tmpUser = tmp.get("author").asInstanceOf[HashMap[String, String]].asScala
      val author = new User(tmpUser.get("id").get, tmpUser.get("name").get,
        tmpUser.get("screen_name").get, tmpUser.get("profile_pic").get)
      photos += new Instagram(tmp.get("id").asInstanceOf[String],
        tmp.get("url").asInstanceOf[String],
        new java.util.Date(tmp.get("timestamp").asInstanceOf[Long] * 1000),
        tmp.get("photo").asInstanceOf[String],
        author,
        location)
    }
    val json = Json.toJson(photos)
    Ok(json)
  }
  
  def flights = Action {
    val resp = client.execute {
      search in index -> "flight" size 5000 query {
        rangeQuery("timestamp") from "now-1m" to "now" includeUpper true
      } searchType SearchType.QueryThenFetch
    }.await
    Logger.debug(s"Flight results found: ${resp.getHits.getTotalHits}")
    val flights = mutable.ListBuffer[Flight]()
    for (hit <- resp.getHits.getHits) {
      val tmp = hit.getSource
      val coord = tmp.get("coord").asInstanceOf[ArrayList[Double]].asScala.toList
      val location = new Location(coord(1), coord(0))
      flights += new Flight(tmp.get("tail").asInstanceOf[String],
        new java.util.Date(tmp.get("timestamp").asInstanceOf[Long] * 1000),
        location,
        tmp.get("heading").asInstanceOf[Int])
    }
    val json = Json.toJson(flights)
    Ok(json)
  }
  
}
