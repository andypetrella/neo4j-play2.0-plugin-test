package controllers

import play.api._
import cache.Cache
import libs.concurrent._
import libs.iteratee._
import akka.util.duration._
import libs.iteratee.Enumerator.Pushee
import play.api.mvc._
import play.api.data._
import play.api.data.Forms._
import play.api.Play._
import play.api.libs.json._
import play.api.libs.json.Json._

import models._
import be.nextlab.play.neo4j.rest.Relation._
import be.nextlab.play.neo4j.rest._
import collection.Seq
import akka.actor._
import akka.pattern.ask
import java.util.UUID
import akka.dispatch.Await
import akka.util.Timeout


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

  def all(implicit neo: Neo4JEndPoint): Promise[Seq[Stuff]] = for (
    r <- neo.root;
    ref <- r.referenceNode;
    c <- r.cypher(JsObject(Seq(
      "query" -> JsString("start init=node({reference}) match init-[:STUFF]->(n) return n"),
      "params" -> JsObject(Seq(
        "reference" -> JsNumber(ref.asInstanceOf[Node].id) // just for test: ".asIn..." directly
      ))
    )))
  ) yield (c match {
      case cr: CypherResult => cr.result map { l => 
        Stuff.fromNode(Node(l.find(_._1 == "n").get._2.asInstanceOf[JsObject]))
      }
      case _ => Nil
    })

  def allStuffs = Action { implicit request =>
    Async {
      all map { list => Ok(toJson(list)) }
    }
  }
  
  def createStuff = Action {
    implicit request =>
      Async {
        stuffForm.bindFromRequest.fold(
          formWithErrors => Promise.pure(BadRequest("Missing Information to create Stuff")),
          stuff => for (
            r <- neo.root;
            ref <- r.referenceNode;
            s <- {
              val s: Node = Stuff.toNode(stuff)
              println(s.data)
              r.createNode(Some(s))
            };
            l <- ref.asInstanceOf[Node].createRelationship(
              Relation(
                JsObject(
                  Seq(
                    "type" -> JsString("STUFF"),
                    "end" -> JsString(s.asInstanceOf[Node].self),
                    "data" -> JsObject(Seq())
                  )))) //quick cast
          ) yield s match {
              case node: Node => {
                val st: Stuff = Stuff.fromNode(node)
                stuffsActor ! IncStuffs(1, st.creation)
                Ok(toJson(st))
              }
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

  val toEventSource = Enumeratee.map[(Int, Long)] {
    msg =>
      "data: " + stringify(JsObject(Seq("n"->JsNumber(msg._1), "d" ->JsNumber(msg._2)))) + """

"""
  } //two \n are needed => spec


  val stuffsActor = Akka.system.actorOf(Props[StuffsActor], name = "stuffsActor")
  val masterActor = Akka.system.actorOf(Props[MasterActor], name = "masterStuffsActor")

  class StuffsActor extends Actor {
    protected def receive = {
      case ns@NewStuff(s:Stuff) =>
      case i@IncStuffs(n:Int, d:Long) => masterActor ! i
    }
  }
  class MasterActor extends Actor {
    
    protected def receive = {
      case uuid:String => {
        Cache.set("uuids", Cache.getOrElse[Seq[String]]("uuids")(Nil).filter(_ != uuid))
        Cache.set(uuid+"."+"count", None)
      }
      case (uuid:String, cs:Pushee[_]) => {
        Cache.set("uuids", uuid +: Cache.getOrElse[Seq[String]]("uuids")(Nil))
        Cache.set(uuid+"."+"count", Some(cs))
      }
      case IncStuffs(n:Int, l:Long) => Cache.getOrElse[Seq[String]]("uuids")(Nil) foreach { e => Cache.getAs[Option[Pushee[(Int, Long)]]](e+"."+"count") map {op => op map {_.push((n,l))}}}
    }
  }

  def eventStream(uuid:String) = {
    println("entering the event stream")
    Enumerator.pushee[(Int, Long)](
      { (pushee: Pushee[(Int, Long)]) => masterActor ! (uuid, pushee) },
      { println("completed"); masterActor ! uuid }
    )
  }

  case class NewStuff(s:Stuff){}
  case class IncStuffs(n:Int = 0, date:Long){}

  def nodeCount = Action {
    println("start count stream")

    val uuid: String = BigInt(1000, scala.util.Random).toString(36)

    SimpleResult(
      header = ResponseHeader(OK, Map(
        CONTENT_LENGTH -> "-1",
        CONTENT_TYPE -> "text/event-stream",
        CONNECTION -> "keep-alive",
        CACHE_CONTROL -> "no-cache"
      )), body = eventStream(uuid) &> toEventSource)
  }

}