package org.scala.tools.eclipse.search.ui

import scala.Array.canBuildFrom
import org.eclipse.jface.viewers.ITreeContentProvider
import org.eclipse.jface.viewers.Viewer
import org.scala.tools.eclipse.search.Occurrence
import scala.tools.eclipse.logging.HasLogger

/**
 * Responsible for telling a TreeViewer what content to display.
 */
class SearchViewContentProvider extends ITreeContentProvider with HasLogger {

  private var data: Map[String, Seq[Occurrence]] = Map()

  override def dispose() { }

  override def inputChanged(viewer: Viewer, oldInput: Object, newInput: Object) {
    if (newInput == null) {
      logger.debug("new input was null")
    } else {
      if (newInput.isInstanceOf[Map[_, _]]) {
        data = newInput.asInstanceOf[Map[String, Seq[Occurrence]]]
      } else {
        logger.debug(
            "Tried to update the input with something that wasn't a map %s".format(newInput.toString))
      }
    }
  }

  override def getElements(inputElement: Object): Array[Object] = {
    data.map { case (str, seq) => (str, seq.size) }.toArray
  }

  override def getChildren(parentElement: Object): Array[Object] = {
    parentElement match {
      case (x: String, _) => data.get(x).map(_.toArray.map(_.asInstanceOf[Object])).getOrElse( Array[Object]() )
      case _ => Array[Object]()
    }
  }

  override def getParent(element: Object): Object = null

  override def hasChildren(element: Object): Boolean = element match {
    case (x: String, _) => data.get(x).fold(false)(_ => true)
    case _ => false
  }
}