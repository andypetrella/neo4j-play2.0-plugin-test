package stress

import com.excilys.ebi.gatling.core.Predef._

import com.excilys.ebi.gatling.core.scenario.configuration.{Simulation => GSimulation}

// Contains all HTTP related methods

import com.excilys.ebi.gatling.http.Predef._

object SampleSimulations {

  def simulations(baseUrl: String): Seq[GSimulation] = Seq(

    //what might be done in play app : new SimpleSimulation(baseUrl, controllers.routes.Application.index().url),
    new SimpleSimulation(baseUrl, "")

  )


}

class SimpleSimulation(baseUrl: String, path: String) extends GSimulation {


  def apply = {

    val headers_1 = Map(
      "Accept" -> "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
      "Accept-Charset" -> "ISO-8859-1,utf-8;q=0.7,*;q=0.7"
    )

    val scn = scenario("test")
      .exec(
      http("request_1")
        .get(path)
        .headers(headers_1)
        .check(status.is(200))
    )

    val httpConf = httpConfig.baseURL(baseUrl)

    Seq(scn.configure users 10 ramp 2 protocolConfig httpConf)

  }
}
