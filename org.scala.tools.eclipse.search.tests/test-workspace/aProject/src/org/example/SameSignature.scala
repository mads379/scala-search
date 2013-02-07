package org.example

object SameSignature {

  object ObjectOne {
    def testing(x: String): String = x
  }

  object ObjectTwo {
    def testing(x: String): String = x
  }

  def main = {
    val s = ObjectOne.testing("Hi") + ObjectTwo.testing("Hi")
  }
}