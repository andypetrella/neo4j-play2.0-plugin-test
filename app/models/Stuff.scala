package models

import play.api.libs.json._
import play.api.libs.concurrent.Promise
import be.nextlab.play.neo4j.rest.{Relation, CypherResult, Neo4JEndPoint, Node}

/**
 * User: noootsab
 */

object Group extends Enumeration {
  type Group = Value
  val first = Value("First")
  val second = Value("Second")
  val third = Value("Third")
  val fourth = Value("Fourth")
  val fifth = Value("Fifth")
}

import Group._

case class Stuff(
                  id: Option[Int],
                  neo: Option[Node],
                  foo: String,
                  bar: Boolean,
                  baz: Int,
                  group: Option[Group],
                  pokes: Seq[PokeStuff] = Nil,
                  creation: Long = System.currentTimeMillis()) {}

case class PokeStuff(poked: Stuff, how: String) {}

object Stuff {

  implicit object StuffJsonFormat extends Format[Stuff] {

    def reads(json: JsValue) = Stuff(
      (json \ "neo4jid").asOpt[Int],
      None,
      (json \ "foo").as[String],
      (json \ "bar").as[Boolean],
      (json \ "baz").as[Int],
      (json \ "group").asOpt[String] map {
        Group.withName(_)
      },
      Nil,
      (json \ "creation").asOpt[Long] getOrElse (System.currentTimeMillis())
    )

    def writes(stuff: Stuff) = JsObject(Seq(
      "neo4jid" -> stuff.id.map {
        JsNumber(_)
      }.getOrElse(JsUndefined("Id not defined yet")),
      "foo" -> JsString(stuff.foo),
      "bar" -> JsBoolean(stuff.bar),
      "baz" -> JsNumber(stuff.baz),
      "group" -> stuff.group.map((g: Group) => JsString(g.toString)).getOrElse(JsUndefined("No Group")),
      "creation" -> JsNumber(System.currentTimeMillis())
    ))

  }


  def toData(stuff: Stuff): JsObject = JsObject(Seq(
    "foo" -> JsString(stuff.foo),
    "bar" -> JsBoolean(stuff.bar),
    "baz" -> JsNumber(stuff.baz),
    "group" -> stuff.group.map((g: Group) => JsString(g.toString)).getOrElse(JsUndefined("No Group")),
    "creation" -> JsNumber(System.currentTimeMillis())
  ))

  def toNode(stuff: Stuff): Node = Node(JsObject(Seq("data" -> toData(stuff))))

  def fromNode(node: Node): Stuff = Stuff(
    Some(node.id),
    Some(node),
    (node.data \ "foo").as[String],
    (node.data \ "bar").as[Boolean],
    (node.data \ "baz").as[Int],
    (node.data \ "group").asOpt[String] map {
      Group.withName(_)
    },
    Nil,
    (node.data \ "creation").asOpt[Long] getOrElse (System.currentTimeMillis())
  )

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
      case cr: CypherResult => cr.result map {
        l =>
          Stuff.fromNode(Node(l.find(_._1 == "n").get._2.asInstanceOf[JsObject]))
      }
      case _ => Nil
    })

  def create(stuff: Stuff)(implicit neo: Neo4JEndPoint) =
    for (
      r <- neo.root;
      ref <- r.referenceNode;
      s <- r.createNode(Some(Stuff.toNode(stuff)));
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
        st
      }
      case _ => throw new IllegalStateException("Cannot create stuff...")
    }

  def get(id: Int)(implicit neo: Neo4JEndPoint) = for (
    r <- neo.root;
    n <- r.getNode(id)
  ) yield n match {
      case node: Node => Some(Stuff.fromNode(node))
      case _ => None
    }

  def full(implicit neo:Neo4JEndPoint) = for (
    r <- neo.root;
    ref <- r.referenceNode;
    c <- r.cypher(JsObject(Seq(
      "query" -> JsString("start init=node({reference}) match init-[:STUFF]->(n)-[r?]->nn return n, r, nn"),
      "params" -> JsObject(Seq(
        "reference" -> JsNumber(ref.asInstanceOf[Node].id) // just for test: ".asIn..." directly
      ))
    )))
  ) yield (c match {
      case cr: CypherResult => cr.result.foldLeft(Map():Map[Int, Stuff]) { (map, row) => {
        val node: Node = Node(row.find(_._1 == "n").get._2.asInstanceOf[JsObject])
        val stuff = map.get(node.id) match {
          case None => Stuff.fromNode(node)
          case Some(s) => s
        }
        row.find(_._1 == "r") match {
          case None => stuff
          case Some(rel) => {
            val end = Stuff.fromNode(Node(row.find(_._1 == "nn").get._2.asInstanceOf[JsObject]))
            stuff.copy(pokes = PokeStuff(end, rel) +: stuff.pokes)
          }
        }


      }}
      case _ => Nil
    })


  def withPokes(stuff:Stuff)(implicit neo:Neo4JEndPoint) = PokeStuff.forStuff(stuff).map{s => stuff.copy(pokes = s)}
}

object PokeStuff {
  implicit object PokeStuffJsonFormat extends Format[PokeStuff] {

    def reads(json: JsValue) = PokeStuff(
      Stuff(None, None, "", false, 0, None, Nil, 0),//todo
      (json \ "how").as[String]
    )

    def writes(pokeStuff: PokeStuff) = JsObject(Seq(
      "how" -> JsString(pokeStuff.how),
      "poked" -> JsNumber(pokeStuff.poked.id.get)
    ))

  }

  def create(stuff:Stuff, pokeStuff:PokeStuff)(implicit neo: Neo4JEndPoint) =
    for (
      r <- neo.root;
      l <- stuff.neo.get.createRelationship(Relation(JsObject(Seq(
        "type" -> JsString(pokeStuff.how),
        "end" -> JsString(pokeStuff.poked.neo.get.self),
        "data" -> JsObject(Seq())
      ))))
    ) yield l match {
      case node: Relation => pokeStuff
      case _ => throw new IllegalStateException("Cannot create poke stuff...")
    }

  def forStuff(stuff:Stuff)(implicit neo:Neo4JEndPoint) = for (
    r <- neo.root;
    ref <- r.referenceNode;
    c <- r.cypher(JsObject(Seq(
      "query" -> JsString("start init=node({stuffId}) match init-[r]->(n) return r, n"),
      "params" -> JsObject(Seq(
        "stuffId" -> JsNumber(stuff.id.get)
      ))
    )))
  ) yield (c match {
      case cr: CypherResult => cr.result map {
        l => {
          PokeStuff(
            Stuff.fromNode(Node(l.find(_._1 == "n").get._2.asInstanceOf[JsObject])),
            (l.find(_._1 == "r").get._2.asInstanceOf[JsObject] \ "type").as[String]
          )
        }
      }
      case _ => Nil
    })

}