package io.github.alexstaeding.rangefinder.types.simple

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.github.plokhotnyuk.jsoniter_scala.macros.JsonCodecMaker
import io.github.alexstaeding.rangefinder.meta.*
import io.github.alexstaeding.rangefinder.network.{HashingAlgorithm, NodeId, NodeIdSpace}

import java.security.MessageDigest
import scala.util.Random

case class StringIndex(data: String)

object StringIndex {
  given codec: JsonValueCodec[StringIndex] = JsonCodecMaker.make
  given hashingAlgorithm(using idSpace: NodeIdSpace): HashingAlgorithm[StringIndex] = (value: PartialKey[StringIndex]) => {
    val hash = MessageDigest
      .getInstance("SHA1")
      .digest(value.startInclusive.data.getBytes("UTF-8") ++ value.endExclusive.data.getBytes("UTF-8"))
    NodeId(hash.take(idSpace.size))
  }
  given universe: PartialKeyUniverse[StringIndex] = StringPrefixPartialKeyUniverse.map(StringIndex(_), _.data)

  given ordering: Ordering[StringIndex] = Ordering.by(_.data)

  given matcher: PartialKeyMatcher[StringIndex] = new OrderingPartialKeyMatcher[StringIndex]

  private val allContent = Map(
    "algorithm" -> "A process or set of rules to be followed in calculations or problem-solving operations.",
    "array" -> "A data structure consisting of a collection of elements, each identified by an array index.",
    "binary" -> "A numeric system that only uses two digits — 0 and 1.",
    "cache" -> "A hardware or software component that stores data so future requests for that data can be served faster.",
    "class" -> "In object-oriented programming, a blueprint for creating objects providing initial values for state and implementations of behavior.",
    "compiler" -> "A program that translates code written in high-level programming languages to machine code.",
    "concurrency" -> "The ability of different parts or units of a program to execute out-of-order or in partial order without affecting the final outcome.",
    "database" -> "An organized collection of data, generally stored and accessed electronically from a computer system.",
    "encryption" -> "The process of converting information or data into a code to prevent unauthorized access.",
    "framework" -> "A platform for developing software applications. It provides a foundation on which software developers can build programs.",
    "function" -> "A block of organized, reusable code that is used to perform a single, related action.",
    "garbage" -> "In computing, unwanted or unused data that is no longer needed or can no longer be accessed.",
    "hash" -> "A function that converts input data of any size into a fixed-size string of text, typically a sequence of numbers and letters.",
    "inheritance" -> "A mechanism in which one class acquires the properties and behaviors of a parent class.",
    "interface" -> "A shared boundary across which two or more separate components of a computer system exchange information.",
    "iterator" -> "An object that enables a programmer to traverse a container, particularly lists.",
    "kernel" -> "The core component of an operating system, managing system resources and communication between hardware and software.",
    "library" -> "A collection of non-volatile resources used by computer programs, often for software development.",
    "module" -> "A separate unit of software or hardware. Typical characteristics of modular components are isolation, composability, and reuse.",
    "network" -> "A group of two or more computer systems linked together to share resources and information.",
    "object" -> "In object-oriented programming, an instance of a class that encapsulates data and behavior related to that data.",
    "packet" -> "A small segment of data that is bundled for network transmission.",
    "queue" -> "A linear structure which follows a particular order in which operations are performed. The order is First In First Out (FIFO).",
    "recursion" -> "The process in which a function calls itself directly or indirectly.",
    "repository" -> "A central location in which data is stored and managed.",
    "script" -> "A list of commands that are executed by a certain program or scripting engine.",
    "syntax" -> "The set of rules that defines the combinations of symbols that are considered to be correctly structured statements or expressions in a language.",
    "thread" -> "The smallest sequence of programmed instructions that can be managed independently by a scheduler.",
    "variable" -> "A storage location paired with an associated symbolic name, which contains some known or unknown quantity or information, a entry.",
    "widget" -> "A graphical control element, such as a button or scroll bar, used in GUIs.",
    "xml" -> "Extensible Markup Language, a markup language that defines a set of rules for encoding documents in a format that is both human-readable and machine-readable.",
  )

  def getContent(id: NodeId): Map[String, String] = {
    val random = new Random(id.toLong)
    val indexes = (1 to 5).map(_ => random.nextInt(allContent.size)).toSet
    indexes.map(allContent.toSeq).toMap
  }
}
