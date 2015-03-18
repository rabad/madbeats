package es.outliers.madbeats

import akka.actor.{ActorSystem, Props}
import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.mappings.FieldType._
import com.sksamuel.elastic4s.{ElasticClient, ElasticsearchClientUri}
import com.typesafe.config.ConfigFactory
import es.outliers.madbeats.crawlers.{InstagramCrawler, SwarmCrawler, TrafficCrawler, TwitterCrawler}
import es.outliers.madbeats.utils.StartMessage

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

/**
 * Created by dataclimber on 30/01/15.
 */
object Main extends App {
  def setupES = {
    val uriStr = conf.getString("persistence.uri")
    log.debug(s"ElasticSearch URI: ${uriStr}")
    val uri = ElasticsearchClientUri(uriStr)
    log.debug(s"URI: ${uri.toString}")
    val client = ElasticClient.remote(uri)
    val index = conf.getString("persistence.index")
    if (!client.exists(index).await.isExists) {
      client.execute {
        create index conf.getString("persistence.index") shards conf.getInt("persistence.shards") replicas conf.getInt("persistence.replicas")
      }
    }
    // Update mappings.
    client.execute {
      put mapping conf.getString("persistence.index") / "tweet" as (
        "id" typed StringType index NotAnalyzed,
        "timestamp" typed DateType,
        "url" typed StringType index NotAnalyzed,
        "text" typed StringType,
        "coord" typed GeoPointType geohash true,
        "author" typed ObjectType as(
          "id" typed StringType index NotAnalyzed,
          "name" typed StringType index NotAnalyzed,
          "screen_name" typed StringType index NotAnalyzed,
          "profile_pict" typed StringType index NotAnalyzed
          )
      ) ignoreConflicts true
    }
    client.execute {
      put mapping conf.getString("persistence.index") / "swarm" as (
        "id" typed StringType index NotAnalyzed,
        "timestamp" typed DateType,
        "venue" typed ObjectType as(
          "id" typed StringType index NotAnalyzed,
          "name" typed StringType index NotAnalyzed,
          "country" typed StringType index NotAnalyzed,
          "cc" typed StringType index NotAnalyzed,
          "coord" typed GeoPointType geohash true,
          "categories" typed NestedType as(
            "id" typed StringType index NotAnalyzed,
            "name" typed StringType index NotAnalyzed
            )
          ),
        "author" typed ObjectType as(
          "id" typed StringType index NotAnalyzed,
          "name" typed StringType index NotAnalyzed,
          "screen_name" typed StringType index NotAnalyzed,
          "profile_pict" typed StringType index NotAnalyzed
          )
        ) ignoreConflicts true
    }
    client.execute {
      put mapping conf.getString("persistence.index") / "venues" as (
        "id" typed StringType index NotAnalyzed,
        "name" typed StringType index NotAnalyzed,
        "country" typed StringType index NotAnalyzed,
        "cc" typed StringType index NotAnalyzed,
        "coord" typed GeoPointType geohash (true),
        "categories" typed NestedType as(
          "id" typed StringType index NotAnalyzed,
          "name" typed StringType index NotAnalyzed
          )
      ) ignoreConflicts true
    }
    client.execute {
      put mapping conf.getString("persistence.index") / "traffic" as (
        "id" typed StringType index NotAnalyzed,
        "pm_id" typed StringType index NotAnalyzed,
        "timestamp" typed DateType,
        "coord" typed GeoPointType geohash true,
        "intensity" typed IntegerType,
        "occupation" typed IntegerType,
        "load" typed IntegerType
      ) ignoreConflicts true
    }
    client.execute {
      put mapping conf.getString("persistence.index") / "flight" as (
        "timestamp" typed DateType,
        "coord" typed GeoPointType geohash true,
        "altitude" typed IntegerType,
        "heading" typed IntegerType,
        "speed" typed IntegerType,
        "model" typed StringType index NotAnalyzed,
        "callsign" typed StringType index NotAnalyzed,
        "tail" typed StringType index NotAnalyzed,
        "flight_no" typed StringType index NotAnalyzed,
        "route" typed StringType index NotAnalyzed
        ) ignoreConflicts true
    }
    client.execute {
      put mapping conf.getString("persistence.index") / "instagram" as (
        "id" typed StringType index NotAnalyzed,
        "timestamp" typed DateType,
        "url" typed StringType index NotAnalyzed,
        "photo" typed StringType,
        "coord" typed GeoPointType geohash true,
        "author" typed ObjectType as(
          "id" typed StringType index NotAnalyzed,
          "name" typed StringType index NotAnalyzed,
          "screen_name" typed StringType index NotAnalyzed,
          "profile_pict" typed StringType index NotAnalyzed
          )
        ) ignoreConflicts true
    }
    client.close
  }
  val conf = ConfigFactory.load()

  val system = ActorSystem("madbeats-system")
  val log = system.log
  // Setup ElasticSearch index.
  setupES
  val swarm = system.actorOf(Props(new SwarmCrawler(conf.getConfig("crawlers.swarm"), conf.getConfig("persistence"))))
  swarm ! StartMessage
  val twitter = system.actorOf(Props(new TwitterCrawler(conf.getConfig("crawlers.tweet"), conf.getConfig("persistence"))))
  twitter ! StartMessage
  val traffic = system.actorOf(Props(new TrafficCrawler(conf.getConfig("crawlers.traffic"), conf.getConfig("persistence"))))
  system.scheduler.schedule(0 seconds, 5 minutes, traffic, StartMessage)
  val instagram = system.actorOf(Props(new InstagramCrawler(conf.getConfig("crawlers.instagram"), conf.getConfig("persistence"))))
  system.scheduler.schedule(0 seconds, 5 minutes, instagram, StartMessage)
  // NOTE: Flights have been blocked.
  //val flights = system.actorOf(Props(new FlightCrawler(conf.getConfig("crawlers.flight"), conf.getConfig("persistence"))))
  //system.scheduler.schedule(0 seconds, 1 minute, flights, StartMessage)
}
