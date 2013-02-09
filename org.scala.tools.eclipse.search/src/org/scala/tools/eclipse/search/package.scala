package org.scala.tools.eclipse

package object search {

  /**
   * Little utility method used for recording how many seconds
   * an operation takes.
   */
  def timed(op: => Unit): Double = {
    val now = System.currentTimeMillis()
    op
    val elapsed = ((System.currentTimeMillis() - now) * 0.001)
    elapsed
  }

  /**
   * Implicit value class that makes it easier to log or report errors
   * inside for-comprehensions.
   */
  implicit class ErrorHandlingOption[A](val op: Option[A]) extends AnyVal {
    def ifEmpty(f: => Unit): Option[A] = {
      if (op.isEmpty) {
        f
        None
      } else op
    }
  }
}