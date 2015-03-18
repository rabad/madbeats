package es.outliers.madbeats.crawlers

import java.util

import akka.actor.{Actor, Props}
import akka.event.Logging
import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.source.ObjectSource
import com.sksamuel.elastic4s.{ElasticClient, ElasticsearchClientUri}
import com.typesafe.config.Config
import es.outliers.madbeats.index.{Author, Checkin, Venue}
import es.outliers.madbeats.utils.{ProcessStatus, StartMessage, StopMessage, VenueResponse}
import org.json4s._
import org.json4s.native.JsonMethods._
import twitter4j._
import scala.collection.JavaConversions.mapAsScalaMap
import scala.collection.JavaConverters._
import scala.collection.mutable.ArrayBuffer


/**
 * Created by dataclimber on 30/01/15.
 */

class SwarmCheckinProcessor(esConf: Config, fsqConf: Config) extends Actor {
  val log = Logging(context.system, this)

  def createUser(author: User): Author = new Author(author.getId.toString, author.getName, author.getScreenName, author.getBiggerProfileImageURL)

  def getVenueId(status: Status): String = {
    var swarmUri = ""
    var venuesId = ""

    try {
      for (url <- status.getURLEntities) {
        if (url.getExpandedURL.matches("""https://www.swarmapp.com/c/\w+""")) {
          swarmUri = url.getExpandedURL
        }
      }
      if (swarmUri != "") {
        val tmp = scala.io.Source.fromURL(swarmUri, "UTF8")
        val response = tmp.mkString
        tmp.close
        val startUri = response indexOf "https://foursquare.com/v"
        if (startUri != -1) {
          venuesId = response.substring(startUri).split('"').head.split('/').last
        }
      }
    } catch {
      case io: java.io.IOException => log.error(s"Error retrieving URL 1: ${io.getMessage}")
      case ex: Throwable => log.error(ex.getMessage)
    }
    venuesId
  }

  def getVenueInfo(venueId: String): Venue = {
    implicit val formats = DefaultFormats

    val uri = ElasticsearchClientUri(esConf.getString("uri"))
    val client = ElasticClient.remote(uri)
    var venueObj: Venue = null
    try {
      val url = s"https://api.foursquare.com/v2/venues/${venueId}?v=${fsqConf.getString("version")}&client_id=${fsqConf.getString("clientId")}&client_secret=${fsqConf.getString("clientSecret")}"
      log.debug(s"FSQ API URL: ${url}")
      val urlSource = scala.io.Source.fromURL(url, "UTF8")
      val response = urlSource.mkString
      urlSource.close
      log.debug(s"FSQ Response: ${response}")
      val json = parse(response)
      val tmp = json.extract[VenueResponse]
      val tmpVenue = tmp.response.get.venue.get
      val tmpLocation = tmpVenue.location.get
      var categories = List[Map[String, String]]()
      for (cat <- tmpVenue.categories.get) {
        categories = categories :+ Map("id" -> cat.id.get.toString, "name" -> cat.name.get.toString)
      }
      venueObj = new Venue(tmpVenue.id.get, tmpVenue.name.get, tmpLocation.country.get,
        tmpLocation.cc.get, List(tmpLocation.lng.get, tmpLocation.lat.get), categories)
      client.execute {
        index into esConf.getString("index") -> "venues" doc ObjectSource(venueObj) id venueObj.id
      }
      client.flush(esConf.getString("index"))
    } catch {
      case io: java.io.IOException => log.error(s"Error retrieving URL 2: ${io.getMessage}")
      case ex: Throwable => log.error(ex.getMessage)
    } finally {
      client.close()
    }
    venueObj
  }

  def createVenue(status: Status): Venue = {
    var venue: Venue = null
    val uri = ElasticsearchClientUri(esConf.getString("uri"))
    val client = ElasticClient.remote(uri)
    val venueId = getVenueId(status)
    if (venueId != "") {
      val resp = client.execute {
        get id venueId from esConf.getString("index") -> "venues"
      }
      val result = resp.await
      if (result.isExists) {
        val tmp = result.getSource
        val categories = tmp.get("categories").asInstanceOf[util.ArrayList[util.HashMap[String, String]]]
          .asScala
          .toList
          .map { c =>
            mapAsScalaMap[String, String](c)
              .toMap
          }
        venue = new Venue(tmp.get("id").toString, tmp.get("name").toString, tmp.get("country").toString,
          tmp.get("cc").toString, tmp.get("coords").asInstanceOf[List[Double]], categories)
      } else {
        venue = getVenueInfo(venueId)
      }
    }
    client.close()
    venue
  }

  def createCheckin(status: Status): Checkin = {
    val venue = createVenue(status)
    var checkin: Checkin = null
    if (venue != null) {
      val user = createUser(status.getUser)
      checkin = new Checkin(status.getId.toString, status.getCreatedAt, user, venue)
    }
    checkin
  }

  def isSwarm(status: Status): Boolean = {
    var res = false
    
    for (url <- status.getURLEntities) {
      log.debug(s"Trying URL: ${url.getExpandedURL}")
      if (url.getExpandedURL.matches("""https://www.swarmapp.com/c/\w+""")) {
        res = true
      }
    }
    log.debug(s"isSwarm?: ${res}")
    res
  }

  def indexCheckin(status: Status) = {
    if (isSwarm(status)) {
      val checkin = createCheckin(status)
      if (checkin != null) {
        val uri = ElasticsearchClientUri(esConf.getString("uri"))
        val client = ElasticClient.remote(uri)
        client.execute {
          index into esConf.getString("index") -> "swarm" doc ObjectSource(checkin) id status.getId.toString
        }
        client.flush(esConf.getString("index"))
        client.close()
      }
    }
  }

  override def receive: Actor.Receive = {
    case ProcessStatus(status) => {
      log.debug(s"Processor received status with text: ${status.getText}")
      indexCheckin(status)
      context.stop(self)
    }
    case _ => log.error(s"Processors only accept process messages")
  }
}

class SwarmCrawler(conf: Config, esConf: Config) extends Actor {

  val log = Logging(context.system, this)

  val twitterConfig = new twitter4j.conf.ConfigurationBuilder()
    .setOAuthConsumerKey(conf.getString("credentials.consumerKey"))
    .setOAuthConsumerSecret(conf.getString("credentials.consumerSecret"))
    .setOAuthAccessToken(conf.getString("credentials.accessToken"))
    .setOAuthAccessTokenSecret(conf.getString("credentials.accessTokenSecret"))
    .build

  def swarmStatusListener = new StatusListener() {
    def onStatus(status: Status) {
      val processor = context.system.actorOf(Props(new SwarmCheckinProcessor(esConf, conf.getConfig("foursquare"))))
      processor ! ProcessStatus(status)
    }
    def onDeletionNotice(statusDeletionNotice: StatusDeletionNotice) = {
      log.debug(s"Got a status deletion notice ID: ${statusDeletionNotice.getStatusId}")
    }
    def onTrackLimitationNotice(numberOfLimitedStatuses: Int): Unit = {
      log.warning(s"Got track limitation notice: ${numberOfLimitedStatuses}")
    }
    def onException(ex: Exception) = {
      log.error(ex, ex.getMessage)
    }
    def onScrubGeo(userId: Long, upToStatusId: Long) = {
      log.debug(s"Got scrub_geo event userId: ${userId} upToStatusId: ${upToStatusId}")
    }
    def onStallWarning(warning: StallWarning) = {
      log.error(s"Stall warning received. Message: ${warning.getMessage} Code: ${warning.getCode}")
    }
  }

  val stream = new TwitterStreamFactory(twitterConfig).getInstance
  stream.addListener(swarmStatusListener)

  override def receive: Receive = {
    case StartMessage => {
      log.info("Going to start Swarm crawler")
      val fq = new FilterQuery()
      //fq.track(conf.getStringList("filter.track").asScala.toArray[String])
      val tmpLocations = conf.getDoubleList("filter.locations").asScala.toList
      val locations = ArrayBuffer[Array[Double]]()
      for (i <- 0 to (tmpLocations.size - 1) by 2) {
        val tmp = ArrayBuffer[Double]()
        tmp += tmpLocations(i)
        tmp += tmpLocations(i+1)
        locations += tmp.toArray
      }
      fq.locations(locations.toArray)
      stream.filter(fq)
    }
    case StopMessage => {
      log.info("Going to stop Swarm crawler")
      stream.cleanUp
      stream.shutdown
    }
    case _ => log.error(s"Crawlers only accept start and stop messages")
  }
}