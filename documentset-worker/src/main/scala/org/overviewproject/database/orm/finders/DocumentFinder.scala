package org.overviewproject.database.orm.finders

import org.overviewproject.database.orm.Schema
import org.overviewproject.postgres.SquerylEntrypoint._
import org.overviewproject.tree.orm.Document
import org.overviewproject.tree.orm.finders.{ Finder, FinderResult }

object DocumentFinder extends Finder {

  type DocumentFinderResult = FinderResult[Document]
  
  def byDocumentSetAndDocumentCloudIds(documentSet: Long, documentCloudIds: Iterable[String]): DocumentFinderResult = {
    Schema.documents.where(d => d.documentSetId === documentSet and (d.documentcloudId in (documentCloudIds)))
  }
}