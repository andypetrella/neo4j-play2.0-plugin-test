package stress

import org.specs2.Specification

import play.api.test._
import play.api.test.Helpers.{status => pStatus, _}

import controllers._

import be.nextlab.play.gatling.Util
import be.nextlab.play.gatling.Util._
import com.excilys.ebi.gatling.core.Predef._
import com.excilys.ebi.gatling.http.Predef._
import com.excilys.ebi.gatling.core.scenario.configuration.{Simulation => GSimulation}
import org.specs2.specification.Step
import models._
import play.api.libs.json.JsObject
import play.api.data.Form
import com.excilys.ebi.gatling.http.request.builder.PostHttpRequestBuilder

/**
 *
 * User: noootsab
 * Date: 12/03/12
 * Time: 22:28
 */

class TestStress extends Specification {
  val baseUrl = "http://localhost:3333"

  val headers_1 = Map(
    "Accept" -> "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
    "Accept-Charset" -> "ISO-8859-1,utf-8;q=0.7,*;q=0.7"
  )
  val headers_2 = headers_1 + (com.excilys.ebi.gatling.http.Predef.CONTENT_ENCODING -> "application/x-www-form-urlencoded")

  val server = Util.createServer(3333)

  def startServer: Unit = server.start()

  def stopServer: Unit = server.stop()

  def is =
    "stress html pages" ^ Step(startServer) ^ {
      "root url" ! GatlingApp(
        new GSimulation() {
          def apply() = {

            val scn = scenario("root url")
              .exec(
              http("root_url_simple")
                .get("")
                .headers(headers_1)
                .check(status.is(200))
            )

            val httpConf = httpConfig.baseURL(baseUrl)

            Seq(scn.configure users 10 ramp 2 protocolConfig httpConf)
          }
        }) {
        println("done 1")
        ok("")
      }
    } ^ end ^ {
      "stress rest stuff urls" ^ {
        "create" ! GatlingApp(new GSimulation() {
          def apply() = {
            println("start 2")
            val fill: Form[Stuff] = Application.stuffForm.fill(Stuff(None, None, "test", false, 1, Some(Group.first), Nil, System.currentTimeMillis()))

            val mkString: String = fill.data.map(e => e._1 + "=" + e._2).mkString("&")

            println(mkString)

            val h: PostHttpRequestBuilder = http("rest_create_stuff")
              .post(routes.Application.createStuff.url)
              .headers(headers_2)
            val headers = fill.data.foldLeft(h) {(acc, e) => acc.param(e._1, e._2)}

            val scn = scenario("rest create stuff").exec(headers.check(status.is(200)))

            val httpConf = httpConfig.baseURL(baseUrl)

            val seq = Seq(scn.configure users 10 ramp 2 protocolConfig httpConf)
            println("end 2")
            seq
          }
        }) {

          //SampleSimulations.simulations(baseUrl, routes.Application.createStuff().url) foreach Util.gatling
          
          println(routes.Application.createStuff.url)
          println("done 2")
          ok("")

        }
      }
    } ^
      Step(stopServer) ^
      end
     /* */

}
