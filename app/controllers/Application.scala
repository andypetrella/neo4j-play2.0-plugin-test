package controllers

import play.api._
import libs.concurrent.Promise
import play.api.mvc._
import play.api.data._
import play.api.data.Forms._
import play.api.Play._
import play.api.libs.json._
import play.api.libs.json.Json._

import models._

import be.nextlab.play.neo4j.rest.{Node, Neo4JRestPlugin, Neo4JEndPoint}

object Application extends Controller {

  //quicky crappy thing
  implicit val neo: Neo4JEndPoint = application.plugin[Neo4JRestPlugin] map {
    _.neo4j
  } get


  val stuffForm = Form[Stuff](
    mapping(
      "neo4jid" -> optional[Int](number),
      "foo" -> text,
      "bar" -> boolean,
      "baz" -> number
    )(
      (id, foo, bar, baz) => Stuff(id, None, foo, bar, baz)
    )(
      (stuff) => Some((stuff.id, stuff.foo, stuff.bar, stuff.baz))
    )
  )

  def index = Action {
    Ok(views.html.index("Your new application is ready."))
  }

  def createStuff = Action {
    implicit request =>
      Async {
        stuffForm.bindFromRequest.fold(
          formWithErrors => Promise.pure(BadRequest("Missing Information to create Stuff")),
          stuff => for (
            r <- neo.root;
            s <- r.createNode(Some(Stuff.toNode(stuff)))
          ) yield s match {
              case node: Node => Ok(toJson(Stuff.fromNode(node)))
              case _ => InternalServerError("Cannot create stuff...")
            }
        )
      }
  }

  def getStuff(id: Int) = Action {
    Async {
      for (
        r <- neo.root;
        n <- r.getNode(id)
      ) yield n match {
        case node: Node => Ok(toJson(Stuff.fromNode(node)))
        case _ => NotFound("Stuff not found")
      }
    }
  }

}