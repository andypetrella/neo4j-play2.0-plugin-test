package models

import play.api.libs.json._
import be.nextlab.play.neo4j.rest.{Neo4JEndPoint, Node}

/**
 * User: noootsab
 */

case class Stuff(id: Option[Int], neo: Option[Node], foo: String, bar: Boolean, baz: Int, creation:Long = System.currentTimeMillis()) {

}

object Stuff {

  implicit object StuffJsonFormat extends Format[Stuff] {
    
    def reads(json: JsValue) = Stuff(
      (json \ "neo4jid").asOpt[Int],
      None,
      (json \ "foo").as[String],
      (json \ "bar").as[Boolean],
      (json \ "baz").as[Int],
      (json \ "creation").asOpt[Long] getOrElse (System.currentTimeMillis())
    )

    def writes(stuff: Stuff) = JsObject(Seq(
      "neo4jid" -> stuff.id.map{JsNumber(_)}.getOrElse(JsUndefined("Id not defined yet")),
      "foo" -> JsString(stuff.foo),
      "bar" -> JsBoolean(stuff.bar),
      "baz" -> JsNumber(stuff.baz),
      "creation" -> JsNumber(System.currentTimeMillis())
    ))
    
  }
  
  
  def toData(stuff: Stuff): JsObject = JsObject(Seq(
    "foo" -> JsString(stuff.foo),
    "bar" -> JsBoolean(stuff.bar),
    "baz" -> JsNumber(stuff.baz),
    "creation" -> JsNumber(System.currentTimeMillis())
  ))

  def toNode(stuff: Stuff): Node = Node(JsObject(Seq("data" -> toData(stuff))))

  def fromNode(node: Node): Stuff = Stuff(
    Some(node.id),
    Some(node),
    (node.data \ "foo").as[String],
    (node.data \ "bar").as[Boolean],
    (node.data \ "baz").as[Int],
    (node.data \ "creation").asOpt[Long] getOrElse (System.currentTimeMillis())
  )

}