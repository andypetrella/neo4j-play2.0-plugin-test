package models

import be.nextlab.play.neo4j.rest.Node
import play.api.libs.json._

/**
 * User: noootsab
 */

case class Stuff(id: Option[Int], neo: Option[Node], foo: String, bar: Boolean, baz: Int) {

}

object Stuff {
  implicit object StuffJsonFormat extends Format[Stuff] {
    
    def reads(json: JsValue) = Stuff(
      (json \ "neo4jid").asOpt[Int],
      None,
      (json \ "foo").as[String],
      (json \ "bar").as[Boolean],
      (json \ "baz").as[Int]
    )

    def writes(stuff: Stuff) = JsObject(Seq(
      "neo4jid" -> stuff.id.map{JsNumber(_)}.getOrElse(JsUndefined("Id not defined yet")),
      "foo" -> JsString(stuff.foo),
      "bar" -> JsBoolean(stuff.bar),
      "baz" -> JsNumber(stuff.baz)
    ))
    
  }
  
  
  def toData(stuff: Stuff): JsObject = JsObject(Seq(
    "foo" -> JsString(stuff.foo),
    "bar" -> JsBoolean(stuff.bar),
    "baz" -> JsNumber(stuff.baz)
  ))

  def toNode(stuff: Stuff): Node = Node(JsObject(Seq("data" -> toData(stuff))))

  def fromNode(node: Node): Stuff = Stuff(Some(node.id), Some(node), (node.data \ "foo").as[String], (node.data \ "bar").as[Boolean], (node.data \ "baz").as[Int])

}