package es.outliers.madbeats.crawlers

import java.util.Calendar

import akka.actor.Actor
import akka.event.Logging
import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.source.ObjectSource
import com.sksamuel.elastic4s.{ElasticClient, ElasticsearchClientUri}
import com.typesafe.config.Config
import es.outliers.madbeats.index.TrafficMeasure
import es.outliers.madbeats.utils.{MeasurePoints, StartMessage}
import org.json4s.DefaultFormats
import org.json4s.native.JsonMethods._
import com.roundeights.hasher.Implicits._
import scala.language.postfixOps
import scala.xml.XML

/**
 * Created by dataclimber on 03/02/15.
 */

class TrafficCrawler(conf: Config, esConf: Config) extends Actor {
  val log = Logging(context.system, this)

  implicit val formats = DefaultFormats

  val pmsFile = scala.io.Source.fromURL(getClass.getResource("/madrid_pms.json")).mkString
  val json = parse(pmsFile)
  val pms = json.extract[MeasurePoints].pms

  override def receive: Receive = {
    case StartMessage => {
      log.info("Going to run Traffic crawler")
      val uri = ElasticsearchClientUri(esConf.getString("uri"))
      val client = ElasticClient.remote(uri)
      val now = Calendar.getInstance.getTime
      try {
        val xml = XML.load(conf.getString("uri"))
        val currentPms = (xml \\ "pm")
        for (pm <- currentPms) {
          val code = (pm \\ "codigo").text
          val intensity: Int = (pm \\ "intensidad").text.toInt
          val load: Int = (pm \\ "carga").text.toInt
          val occupation: Int = (pm \\ "ocupacion").text.toInt
          val id = s"${code}-${now.toString}".sha256.hex
          if (pms.contains(code)) {
            val pm = pms.get(code).get
            val tm = new TrafficMeasure(id, code, now, intensity, load, occupation, List(pm.lng, pm.lat))
            client.execute {
              index into esConf.getString("index") -> "traffic" doc ObjectSource(tm) id tm.id
            }
          }
        }
        client.flush(esConf.getString("index"))
      } catch {
        case xe: org.xml.sax.SAXParseException => log.error(s"XML error: ${xe.getMessage}")
        case ex: Throwable => log.error(ex.getMessage)
      } finally {
        client.close()
      }
    }
    case _ => log.error(s"Traffic crawler only accepts start messages")
  }
}
