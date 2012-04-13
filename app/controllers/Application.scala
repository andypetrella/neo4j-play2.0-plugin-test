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
                stuffsActor ! IncStuffs(1)
                Ok(toJson(Stuff.fromNode(node)))
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

  val toEventSource = Enumeratee.map[Int] {
    msg =>
      "data: " + msg.toString + """

"""
  } //two \n are needed => spec


  val stuffsActor = Akka.system.actorOf(Props[StuffsActor], name = "stuffsActor")
  val masterActor = Akka.system.actorOf(Props[MasterActor], name = "masterStuffsActor")

  class StuffsActor extends Actor {
    protected def receive = {
      case ns@NewStuff(s:Stuff) =>
      case cs@IncStuffs(n:Int) => masterActor ! cs
    }
  }
  class MasterActor extends Actor {
    
    protected def receive = {
      case (uuid:String, true) => {
        val seq: Seq[String] = Cache.getOrElse[Seq[String]]("uuids")(Nil)
        val value: Seq[String] = uuid +: seq
        Cache.set("uuids", value)
      }
      case (uuid:String, false) => {
        Cache.set("uuids", Cache.getOrElse[Seq[String]]("uuids")(Nil).filter(_ != uuid))
      }
      case (uuid:String, cs:Pushee[_]) => {
        Cache.set(uuid+"."+"count", cs)
      }
      case IncStuffs(n:Int) => {
        Cache.getOrElse[Seq[String]]("uuids")(Nil) foreach { e => {
          Cache.getAs[Pushee[Int]](e+"."+"count") map {p => p.push(n)}
        }
        }
      }
    }
  }

  def eventStream(uuid:String) = {
    println("entering the event stream")
    Enumerator.pushee[Int](
      { (pushee: Pushee[Int]) => masterActor ! (uuid, pushee) },
      { println("completed"); masterActor ! (uuid, false) }
    )
  }

  case class NewStuff(s:Stuff){}
  case class IncStuffs(n:Int = 0){}

  def nodeCount = Action {
    println("start count stream")
    val uuid: String = BigInt(1000, scala.util.Random).toString(36)

    //probably better to add the uuid at once with the pushee...
    implicit val timeout = Timeout(10 second)
    masterActor ? (uuid, true)

    SimpleResult(
      header = ResponseHeader(OK, Map(
        CONTENT_LENGTH -> "-1",
        CONTENT_TYPE -> "text/event-stream",
        CONNECTION -> "keep-alive",
        CACHE_CONTROL -> "no-cache"
      )), body = eventStream(uuid) &> toEventSource)
  }

}