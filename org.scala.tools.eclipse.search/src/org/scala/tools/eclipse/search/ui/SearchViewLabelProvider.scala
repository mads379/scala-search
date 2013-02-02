package org.scala.tools.eclipse.search.ui

import org.eclipse.jface.viewers.LabelProvider

/**
 * Responsible for telling Eclipse how to render the results in the
 * tree view (i.e. the view that shows the results).
 */
class SearchViewLabelProvider extends LabelProvider {

  override def getText(element: Object): String = {
    element.toString()
  }

}