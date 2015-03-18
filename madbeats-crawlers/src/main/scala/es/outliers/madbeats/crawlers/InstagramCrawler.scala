package es.outliers.madbeats.crawlers

import java.util.{Date, Calendar}

import akka.actor.Actor
import akka.event.Logging
import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.source.ObjectSource
import com.sksamuel.elastic4s.{ElasticClient, ElasticsearchClientUri}
import com.typesafe.config.Config
import es.outliers.madbeats.index.{InstagramObj, Author}
import es.outliers.madbeats.utils.StartMessage
import org.jinstagram.Instagram
import org.jinstagram.auth.model.Token
import org.jinstagram.entity.common.Location
import org.jinstagram.entity.users.feed.{MediaFeedData, MediaFeed}
import scala.collection.JavaConverters._

/**
 * Created by dataclimber on 18/02/15.
 */
class InstagramCrawler (conf: Config, esConf: Config) extends Actor {
  val log = Logging(context.system, this)
  
  val accessToken = conf.getString("credentials.accessToken")
  val secret = conf.getString("credentials.clientSecret")
  val instagramClient = new Instagram(new Token(accessToken, secret))
  
  val latitude = conf.getDouble("location.lat")
  val longitude = conf.getDouble("location.lng")
  val radius = conf.getInt("location.radius")

  override def receive: Receive = {
    case StartMessage => {
      val uri = ElasticsearchClientUri(esConf.getString("uri"))
      val client = ElasticClient.remote(uri)
      try {
        val cal = Calendar.getInstance
        val now = cal.getTime
        cal.add(Calendar.MINUTE, -5)
        val nowMinus5 = cal.getTime
        val feed: MediaFeed = instagramClient.searchMedia(latitude, longitude, now, nowMinus5, radius);
        val feeds: List[MediaFeedData] = feed.getData.asScala.toList
        for (feedObj <- feeds) {
          val author = new Author(feedObj.getUser.getId, feedObj.getUser.getFullName, feedObj.getUser.getUserName, feedObj.getUser.getProfilePictureUrl)
          val location: Location = feedObj.getLocation()
          val coords = List[Double](location.getLongitude, location.getLatitude)
          val createdTime = new Date(feedObj.getCreatedTime.toLong * 1000)
          val photo = new InstagramObj(feedObj.getId, createdTime, feedObj.getLink,
            feedObj.getImages.getStandardResolution.getImageUrl, coords, author)
          client.execute {
            index into esConf.getString("index") -> "instagram" doc ObjectSource(photo) id photo.id
          }
        }
        client.flush(esConf.getString("index"))
      } catch {
        case ex: Throwable => log.error(ex.getMessage)
      } finally {
        client.close()
      }
    }
    case _ => log.error(s"Instagram crawler only accepts start messages")
  }
}
