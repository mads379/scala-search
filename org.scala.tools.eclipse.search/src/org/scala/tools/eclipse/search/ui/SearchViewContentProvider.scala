package org.scala.tools.eclipse.search.ui

import scala.Array.canBuildFrom

import org.eclipse.jface.viewers.ITreeContentProvider
import org.eclipse.jface.viewers.Viewer

class SearchViewContentProvider extends ITreeContentProvider {

  val data = Map(
    ("First Occurrence" -> List("x","y","z")),
    ("Second Occurrence" -> List("x","y","z")),
    ("Third Occurrence" -> List("x","y","z"))
  )

  override def dispose() {

  }

  override def inputChanged(viewer: Viewer, oldInput: Object, newInput: Object) {

  }

  override def getElements(inputElement: Object): Array[Object] = {
    data.keys.toArray
  }

  override def getChildren(parentElement: Object): Array[Object] = {
    parentElement match {
      case x: String => data.get(x).map(_.toArray.map(_.asInstanceOf[Object])).getOrElse( Array[Object]() )
      case _ => Array[Object]()
    }
  }

  override def getParent(element: Object): Object = null

  override def hasChildren(element: Object): Boolean = element match {
    case x: String => data.get(x).fold(false)(_ => true)
    case _ => false
  }
}