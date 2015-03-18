package es.outliers.madbeats.crawlers

import akka.actor.{Actor, Props}
import akka.event.Logging
import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.source.ObjectSource
import com.sksamuel.elastic4s.{ElasticClient, ElasticsearchClientUri}
import com.typesafe.config.Config
import es.outliers.madbeats.index.{Author, Tweet}
import es.outliers.madbeats.utils.{ProcessStatus, StartMessage, StopMessage}
import twitter4j._

import scala.collection.JavaConverters._
import scala.collection.mutable.ArrayBuffer


/**
 * Created by dataclimber on 30/01/15.
 */

class TwitterProcessor(esConf: Config) extends Actor {
  val log = Logging(context.system, this)

  def createUser(author: User): Author = new Author(author.getId.toString, author.getName, author.getScreenName, author.getBiggerProfileImageURL)

  def createTweet(status: Status): Tweet = {
    var tweet: Tweet = null
    if (status.getGeoLocation != null) {
      val user = createUser(status.getUser)
      val tweetUrl = s"https://twitter.com/${user.screen_name}/status/${status.getId}"
      val coord = List[Double](status.getGeoLocation.getLongitude, status.getGeoLocation.getLatitude)
      tweet = new Tweet(status.getId.toString, status.getCreatedAt, status.getText, tweetUrl, coord, user)
    }
    tweet
  }

  def isSwarm(status: Status): Boolean = {
    var res = false
    var swarmappRegex = """https://www.swarmapp.com/c/\w+""".r
    for (url <- status.getURLEntities) {
      log.debug(s"Trying URL: ${url.getExpandedURL}")
      url.getExpandedURL match {
        case swarmappRegex => {res = true}
      }
    }
    log.debug(s"isSwarm?: ${res}")
    res
  }

  def indexCheckin(status: Status) = {
    if (!isSwarm(status)) {
      val tweet = createTweet(status)
      if (tweet != null) {
        val uri = ElasticsearchClientUri(esConf.getString("uri"))
        val client = ElasticClient.remote(uri)
        client.execute {
          index into esConf.getString("index") -> "tweet" doc ObjectSource(tweet) id status.getId.toString
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

class TwitterCrawler(conf: Config, esConf: Config) extends Actor {

  val log = Logging(context.system, this)

  val uri = ElasticsearchClientUri(esConf.getString("uri"))
  val client = ElasticClient.remote(uri)

  val twitterConfig = new twitter4j.conf.ConfigurationBuilder()
    .setOAuthConsumerKey(conf.getString("credentials.consumerKey"))
    .setOAuthConsumerSecret(conf.getString("credentials.consumerSecret"))
    .setOAuthAccessToken(conf.getString("credentials.accessToken"))
    .setOAuthAccessTokenSecret(conf.getString("credentials.accessTokenSecret"))
    .build

  def twitterStatusListener = new StatusListener() {
    def onStatus(status: Status) {
      val processor = context.system.actorOf(Props(new TwitterProcessor(esConf)))
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
  stream.addListener(twitterStatusListener)

  override def receive: Receive = {
    case StartMessage => {
      log.info("Going to start Twitter crawler")
      val fq = new FilterQuery()
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
      log.info("Going to stop Twitter crawler")
      client.close
      stream.cleanUp
      stream.shutdown
    }
    case _ => log.error(s"Crawlers only accept start and stop messages")
  }
}