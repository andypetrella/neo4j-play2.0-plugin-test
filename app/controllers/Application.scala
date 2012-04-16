package controllers

import play.api._
import cache.Cache
import libs.concurrent._
import libs.iteratee._
import libs.iteratee.Enumerator.Pushee
import play.api.mvc._
import play.api.data._
import play.api.data.Forms._
import play.api.Play._
import play.api.libs.json._
import play.api.libs.json.Json._

import models._
import be.nextlab.play.neo4j.rest._
import collection.Seq
import akka.actor._
import models.Stuff._


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
      "baz" -> number,
      "group" -> optional[String](text)
    )(
      (id, foo, bar, baz, group) => Stuff(id, None, foo, bar, baz, group map {Group.withName(_)})
    )(
      (stuff) => Some((stuff.id, stuff.foo, stuff.bar, stuff.baz, stuff.group map {_.toString}))
    )
  )

  val pokeStuffForm = Form[(Stuff, PokeStuff)](
    mapping(
      "stuff" -> number,
      "how" -> text,
      "poked" -> number
    )(
      (stuff, how, poked) => (for {
        s <- Stuff.get(stuff);
        p <- Stuff.get(poked)
      } yield (s.get, PokeStuff(p.get, how))).await.get  //AOUTCH
    )(
      (s:(Stuff, PokeStuff)) => Some((s._1.id.get, s._2.how, s._2.poked.id.get))
    )
  )

  def index = Action {
    Ok(views.html.index("Your new application is ready."))
  }

  def allStuffs = Action {
    implicit request =>
      Async {
        Stuff.all map {
          list => Ok(toJson(list))
        }
      }
  }

  def createStuff = Action {
    implicit request =>
      Async {
        stuffForm.bindFromRequest.fold(
          formWithErrors => Promise.pure(BadRequest("Missing Information to create Stuff")),
          stuff => Stuff.create(stuff) map {
            st => {
              stuffsActor ! NewStuff(st)
              stuffsActor ! IncStuffs(1, st.creation)
              Ok(toJson(st))
            }
          }
        )
      }
  }

  def getStuff(id: Int) = Action {
    Async {
      Stuff.get(id) map {
        _ match {
          case Some(stuff) => Ok(toJson(stuff))
          case None => NotFound("Stuff not found")
        }
      }
    }
  }
  
  def pokeStuff = Action {
    implicit request =>
      Async {
        pokeStuffForm.bindFromRequest.fold(
          formWithErrors => Promise.pure(BadRequest("Missing Information to poke stuff")),
          {case (stuff:Stuff, pokeStuff:PokeStuff) => PokeStuff.create(stuff, pokeStuff) map {
              pokeStuff => {
                stuffsActor ! NewPokeStuff(pokeStuff)
                Ok(toJson(pokeStuff))
              }
            }
          }
        )
      }
  }

  def pokeForStuff(id:Int) = Action {
    implicit request =>
      Async {
        for (
          s <- Stuff.get(id);
          u <- Stuff.withPokes(s.get)
        ) yield Ok(toJson(u.pokes))
      }
  }


  def stuffAdd = Action {
    println("start add stream")

    val uuid: String = BigInt(1000, scala.util.Random).toString(36)

    SimpleResult(
      header = ResponseHeader(OK, Map(
        CONTENT_LENGTH -> "-1",
        CONTENT_TYPE -> "text/event-stream",
        CONNECTION -> "keep-alive",
        CACHE_CONTROL -> "no-cache"
      )), body = addEventStream(uuid) &> toAddEventSource)
  }

  def stuffsCount = Action {
    println("start count stream")

    val uuid: String = BigInt(1000, scala.util.Random).toString(36)

    SimpleResult(
      header = ResponseHeader(OK, Map(
        CONTENT_LENGTH -> "-1",
        CONTENT_TYPE -> "text/event-stream",
        CONNECTION -> "keep-alive",
        CACHE_CONTROL -> "no-cache"
      )), body = countEventStream(uuid) &> toEventSource)
  }


  val stuffsActor = Akka.system.actorOf(Props[StuffsActor], name = "stuffsActor")
  val masterActor = Akka.system.actorOf(Props[MasterActor], name = "masterStuffsActor")

  case class NewStuff(s: Stuff) {}
  case class NewPokeStuff(s: PokeStuff) {}
  case class IncStuffs(n: Int = 0, date: Long) {}

  class StuffsActor extends Actor {
    protected def receive = {
      case ns@NewStuff(s: Stuff) => masterActor ! ns
      case nps@NewPokeStuff(s: PokeStuff) => masterActor ! nps
      case i@IncStuffs(n: Int, d: Long) => masterActor ! i
    }
  }

  class MasterActor extends Actor {

    protected def receive = {
      case (uuid:String, t:String) => {
        Cache.set("uuids", Cache.getOrElse[Seq[String]]("uuids")(Nil).filter(_ != uuid))
        Cache.set(uuid + "." + "t", None)
      }

      case (uuid: String, t: String, cs: Pushee[_]) => {
        Cache.set("uuids", uuid +: Cache.getOrElse[Seq[String]]("uuids")(Nil))
        Cache.set(uuid + "." + t, Some(cs))
      }


      case NewStuff(s: Stuff) => Cache.getOrElse[Seq[String]]("uuids")(Nil) foreach {
        e => Cache.getAs[Option[Pushee[Stuff]]](e + "." + "add") map {
          op => op map {
            _.push(s)
          }
        }
      }
      case NewPokeStuff(s: PokeStuff) => Cache.getOrElse[Seq[String]]("uuids")(Nil) foreach {
        e => Cache.getAs[Option[Pushee[PokeStuff]]](e + "." + "pokes") map {
          op => op map {
            _.push(s)
          }
        }
      }
      case IncStuffs(n: Int, l: Long) => Cache.getOrElse[Seq[String]]("uuids")(Nil) foreach {
        e => Cache.getAs[Option[Pushee[(Int, Long)]]](e + "." + "count") map {
          op => op map {
            _.push((n, l))
          }
        }
      }
    }
  }

  val toEventSource = Enumeratee.map[(Int, Long)] {
    msg =>
      "data: " + stringify(JsObject(Seq("n" -> JsNumber(msg._1), "d" -> JsNumber(msg._2)))) + """

"""
  }

  val toAddEventSource = Enumeratee.map[Stuff] {
    msg =>
      "data: " + stringify(toJson(msg)) + """

"""
  }

  val toPokesEventSource = Enumeratee.map[Stuff] {
    msg =>
      "data: " + stringify(toJson(msg)) + """

"""
  }

  def countEventStream(uuid: String) = {
    println("entering the event stream")
    Enumerator.pushee[(Int, Long)](
    {
      (pushee: Pushee[(Int, Long)]) => masterActor !(uuid, "count", pushee)
    }, {
      println("completed count");
      masterActor ! (uuid, "count")
    }
    )
  }

  def addEventStream(uuid: String) = {
    println("entering the add event stream")
    Enumerator.pushee[Stuff](
    {
      (pushee: Pushee[Stuff]) => masterActor !(uuid, "add", pushee)
    }, {
      println("completed add");
      masterActor ! (uuid, "add")
    }
    )
  }

  def pokesEventStream(uuid: String) = {
    println("entering the pokes event stream")
    Enumerator.pushee[PokeStuff](
    {
      (pushee: Pushee[PokeStuff]) => masterActor !(uuid, "pokes", pushee)
    }, {
      println("completed pokes");
      masterActor ! (uuid, "pokes")
    }
    )
  }



}