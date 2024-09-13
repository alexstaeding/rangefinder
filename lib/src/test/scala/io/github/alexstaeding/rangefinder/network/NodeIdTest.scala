package io.github.alexstaeding.rangefinder.network

import io.github.alexstaeding.rangefinder.network.NodeId.DistanceOrdering
import org.scalatest.funsuite.AnyFunSuite

import scala.math.Ordering.Implicits.infixOrderingOps

class NodeIdTest extends AnyFunSuite {

  private def node(id: String)(using nodeIdSpace: NodeIdSpace): NodeId = NodeId.fromHex(id).get

  test("xor ordering") {
    implicit val nodeIdSpace: NodeIdSpace = NodeIdSpace(4)
    val targetId = node("2317ce74")
    implicit val ordering: Ordering[NodeId] = DistanceOrdering(targetId)

    assert(node("00000000") < node("aaaaaaaa"))
    assert(node("60b420bb") < node("aaaaaaaa"))
    assert(node("55555555") < node("aaaaaaaa"))

    assert(node("aaaaaaaa") > node("00000000"))
    assert(node("aaaaaaaa") > node("60b420bb"))
    assert(node("aaaaaaaa") > node("55555555"))
  }
}
