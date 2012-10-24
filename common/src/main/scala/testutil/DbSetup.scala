package testutil

import anorm._
import java.sql.Connection


object DbSetup {
  
  private def failInsert = { throw new Exception("failed insert") }
  
  def insertDocumentSet(query: String)(implicit c: Connection) : Long = {
    SQL("""
      INSERT INTO document_set (title, query)
      VALUES ({title}, {query})
      """).on(
        'title -> ("From query: " + query),
        'query -> query
      ).executeInsert().getOrElse(failInsert)
  }

  def insertNode(documentSetId: Long, parentId: Option[Long], description: String)
                (implicit c: Connection) : Long = {
    SQL("""
      INSERT INTO node (document_set_id, parent_id, description)
      VALUES ({document_set_id}, {parent_id}, {description})
      """).on(
        'document_set_id -> documentSetId,
        'parent_id -> parentId,
        'description -> description
      ).executeInsert().getOrElse(failInsert)
  }

  def insertDocument(documentSetId: Long, title: String, documentCloudId: String)
                     (implicit connection: Connection) : Long = {
    SQL("""
        INSERT INTO document(document_set_id, title, documentcloud_id) VALUES 
          ({documentSetId}, {title}, {documentCloudId})
        """).on("documentSetId" -> documentSetId,
                "title" -> title, "documentCloudId" -> documentCloudId).
             executeInsert().getOrElse(failInsert)
  }
  
  def insertNodeDocument(nodeId: Long, documentId: Long)(implicit c: Connection) : Long = {
    SQL("""
        INSERT INTO node_document(node_id, document_id) VALUES ({nodeId}, {documentId})
        """).on("nodeId" -> nodeId, "documentId" -> documentId).
             executeInsert().getOrElse(failInsert)
  }
  
  def insertTag(documentSetId: Long, name: String)(implicit c: Connection): Long = {
    SQL("""
        INSERT INTO tag (name, document_set_id)
        VALUES ({name}, {documentSetId})
        """).on("name" -> name, "documentSetId" -> documentSetId).
        executeInsert().getOrElse(failInsert)
  }

  def insertDocumentWithNode(documentSetId: Long,
                             title: String, documentCloudId: String, nodeId: Long)
                            (implicit connection: Connection) : Long = {
    val documentId = insertDocument(documentSetId, title, documentCloudId)
    insertNodeDocument(nodeId, documentId)
    
    documentId
  }
  
  def insertNodes(documentSetId: Long, count: Int)(implicit c: Connection): Seq[Long] = {
    for (i <- 1 to count) yield insertNode(documentSetId, None, "node-" + i)
  }

  def insertDocumentsForeachNode(documentSetId: Long, nodeIds: Seq[Long], 
                                 documentCount: Int)(implicit c: Connection): Seq[Long] = {

    nodeIds.flatMap(n =>
      for (i <- 1 to documentCount) yield insertDocumentWithNode(documentSetId,
        "title-" + i, "documentcloudId-" + i, n))
  }

  def insertDocuments(documentSetId: Long, count: Int)
                     (implicit c: Connection): Seq[Long] = {
    for (i <- 1 to count) yield 
      insertDocument(documentSetId, "title-" + i, "documentCloudId-" + i)
  }

  
  def tagDocuments(tagId: Long, documentIds: Seq[Long])(implicit c: Connection) : Long = {
    SQL("""
        INSERT INTO document_tag (document_id, tag_id)
        SELECT id, {tagId} FROM document
        WHERE id IN """ + documentIds.mkString("(", ",", ")")
        ).on("tagId" -> tagId).executeUpdate()
  }
}