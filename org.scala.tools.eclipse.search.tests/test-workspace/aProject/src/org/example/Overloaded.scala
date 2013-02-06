package org.example

object Overloaded {

	def testing(x: Int): String = x.toString()
	def testing(x: String): String = x

	def main = {
		testing(42)
		testing("Hi there")
	}
}