package stress

import org.specs2.Specification

import play.api.test._
import play.api.test.Helpers.{status => pStatus, _}

import controllers._

import be.nextlab.play.gatling.Util
import be.nextlab.play.gatling.Util._
import com.excilys.ebi.gatling.core.Predef._
import com.excilys.ebi.gatling.http.Predef._
import com.excilys.ebi.gatling.core.structure._
import com.excilys.ebi.gatling.core.scenario.configuration.{Simulation => GSimulation}
import org.specs2.specification.Step
import models._
import play.api.libs.json.JsObject
import play.api.data.Form
import com.excilys.ebi.gatling.http.request.builder.PostHttpRequestBuilder
import com.excilys.ebi.gatling.core.result.writer.DataWriter
import akka.actor.{Actor, ActorRef}
import com.excilys.ebi.gatling.core.action.system

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
  val headers_2 = headers_1 + (CONTENT_ENCODING -> "application/x-www-form-urlencoded")

  //creates a fake Play 2.0 server running on 3333 which defines the gatling plugin automatically
  val server = Util.createServer(3333)

  //function that starts the server, should be used in a specs Step BEFORE the whole specification starts
  def startServer {
    server.start()
  }

  //function that stops the server, should be used in a specs Step AFTER the whole specification has ran
  def stopServer {
    server.stop()
  }

  //function that cleans all gatling ressources used, including its Akka actor system (same remarks than for stopServer)
  def cleanGatling {
    // shut all actors down
    system.shutdown

    // closes all the resources used during simulation
    //not in 1.1.4-SNAPSHOT... ResourceRegistry.closeAll
  }

  def fromToParams[T](h: PostHttpRequestBuilder, f: Form[T], t: T) =
    f.fill(t).data.foldLeft(h) {
      (acc, e) => acc.param(e._1, e._2)
    }

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
        ok("")
      }
    } ^ end ^ {
      "stress rest stuff urls" ^ {
        "create" ! GatlingApp(new GSimulation() {
          def apply() = {
            val headers = fromToParams(
              http("rest_create_stuff").post(routes.Application.createStuff.url).headers(headers_2),
              Application.stuffForm,
              Stuff(None, None, "test", false, 1, Some(Group.first), Nil, System.currentTimeMillis())
            )


            val scn = scenario("rest create stuff").exec(headers.check(status.is(200)))

            val httpConf = httpConfig.baseURL(baseUrl)

            Seq(scn.configure users 10 ramp 2 protocolConfig httpConf)
          }
        }) {
          ok("")
        } ^
          "create a lot" ! GatlingApp(new GSimulation() {
            def apply() = {
              val headers = fromToParams(
                http("rest_create_stuff").post(routes.Application.createStuff.url).headers(headers_2),
                Application.stuffForm,
                Stuff(None, None, "test", false, 1, Some(Group.first), Nil, System.currentTimeMillis())
              )


              val scn = scenario("rest create stuff").loop(
                chain.exec(
                headers
                  .check(status.is(200))
              )) counterName("manyCreationIn10s") during(10, SECONDS)

              val httpConf = httpConfig.baseURL(baseUrl)

              Seq(scn.configure users 10 ramp 2 protocolConfig httpConf)
            }
          }) {
            ok("")
          }
      }
    } ^
      Step(stopServer) ^
      Step(cleanGatling) ^
      end
}
