package org.scala.tools.eclipse.search.ui

import org.eclipse.jface.viewers.LabelProvider
import org.eclipse.jface.viewers.ViewerCell
import org.eclipse.jface.viewers.StyledString
import org.scala.tools.eclipse.search.Occurrence
import org.eclipse.jface.viewers.StyledCellLabelProvider
import org.eclipse.ui.PlatformUI
import org.eclipse.ui.ISharedImages

/**
 * Responsible for telling Eclipse how to render the results in the
 * tree view (i.e. the view that shows the results).
 */
class SearchViewLabelProvider extends StyledCellLabelProvider {

  override def update(cell: ViewerCell) {
    val elem: Object = cell.getElement()
    val text = new StyledString

    if (elem.isInstanceOf[Tuple2[_, _]]) {
      val (str, count) = elem.asInstanceOf[Tuple2[String, Int]]
      text.append(str)
      text.append(" (%s)".format(count), StyledString.COUNTER_STYLER)
      cell.setImage(PlatformUI.getWorkbench().getSharedImages().getImage(ISharedImages.IMG_OBJ_FILE))
    } else {
      val occurrence = elem.asInstanceOf[Occurrence]
      text.append("%s on line".format(occurrence.word))
    }
    cell.setText(text.toString)
    cell.setStyleRanges(text.getStyleRanges)
    super.update(cell)
  }

}