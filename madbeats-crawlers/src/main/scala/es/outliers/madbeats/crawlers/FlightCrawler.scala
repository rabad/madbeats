package es.outliers.madbeats.crawlers

import java.util.Calendar

import akka.actor.Actor
import akka.event.Logging
import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.source.ObjectSource
import com.sksamuel.elastic4s.{ElasticClient, ElasticsearchClientUri}
import com.typesafe.config.Config
import es.outliers.madbeats.index.Flight
import es.outliers.madbeats.utils.{FlightsResponse, StartMessage}
import org.json4s._
import org.json4s.native.JsonMethods._

/**
 * Created by dataclimber on 03/02/15.
 */
class FlightCrawler(conf: Config, esConf: Config) extends Actor {
  val log = Logging(context.system, this)

  override def receive: Receive = {
    case StartMessage => {
      implicit val formats = DefaultFormats

      log.info(s"Starting flight data retrieval")
      val uri = ElasticsearchClientUri(esConf.getString("uri"))
      val client = ElasticClient.remote(uri)
      val now = Calendar.getInstance.getTime
      val urlSource = scala.io.Source.fromURL(conf.getString("uri"))
      val response = urlSource.mkString
      urlSource.close
      log.debug(s"Flights Response: ${response}")
      val json = parse(response)
      val tmp = json.extract[FlightsResponse]
      if (tmp.planes.length > 0) {
        val flights = tmp.planes(0).values
        for (tempFlight <- flights) {
          val model = tempFlight(0)
          val tail = tempFlight(1)
          val callsign = tempFlight(2)
          val coord = List(tempFlight(4).toDouble, tempFlight(3).toDouble)
          val altitude = tempFlight(5).toInt
          val heading = tempFlight(6).toInt
          val speed = tempFlight(7).toInt
          val flightNo = tempFlight(10)
          val route = tempFlight(11)
          val flight = new Flight(now, model, tail, callsign, coord, altitude: Int, heading, speed, flightNo, route)
          client.execute {
            index into esConf.getString("index") -> "flight" doc ObjectSource(flight)
          }
        }
        client.flush(esConf.getString("index"))
        client.close()
      }
    }
    case _ => log.error(s"Flights crawler only accepts start messages")
  }
}
