package controllers

import java.util.UUID
import org.postgresql.PGConnection
import org.squeryl.PrimitiveTypeMode.using
import org.squeryl.Session
import com.jolbox.bonecp.ConnectionHandle
import models.orm.SquerylPostgreSqlAdapter
import models.upload.LO
import models.upload.OverviewUpload
import play.api.db.DB
import play.api.http.HeaderNames._
import play.api.libs.iteratee.Done
import play.api.libs.iteratee.Input
import play.api.libs.iteratee.Iteratee
import play.api.mvc.RequestHeader
import play.api.mvc.Result
import play.api.mvc.Results.BadRequest
import play.api.mvc.Results.InternalServerError
import play.api.Play.current
import scala.util.control.Exception._
import org.apache.commons.lang.NotImplementedException
import java.sql.SQLException

/**
 * Manages the upload of a file. Responsible for making sure the OverviewUpload object
 * is in sync with the LargeObject where the file is stored.
 */
trait FileUploadIteratee {

  /** package for information extracted from request header */
  private case class UploadRequest(filename: String, start: Long, contentLength: Long)

  /** extract useful information from request header */
  private object UploadRequest {
    def apply(header: RequestHeader): Option[UploadRequest] = {
      def defaultContentRange(length: String) = Some("0-%1$s/%1$s".format(length))

      val disposition = "[^=]*=\"?([^\"]*)\"?".r // attachment ; filename="foo.bar" (optional quotes) TODO: Handle quoted quotes
      val range = """(\d+)-(\d+)/\d+""".r // start-end/length

      for {
        contentDisposition <- header.headers.get(CONTENT_DISPOSITION)
        contentLength <- header.headers.get(CONTENT_LENGTH)
        contentRange <- header.headers.get(CONTENT_RANGE).orElse(defaultContentRange(contentLength))
        disposition(filename) <- disposition findFirstIn contentDisposition
        range(start, end) <- range findFirstIn contentRange
      } yield UploadRequest(filename, start.toLong, contentLength.toLong)
    }
  }

  /**
   * Checks the validity of the requests and processes the upload.
   */
  def store(userId: Long, guid: UUID, requestHeader: RequestHeader): Iteratee[Array[Byte], Either[Result, OverviewUpload]] = {

    val uploadRequest = UploadRequest(requestHeader).toRight(BadRequest)

    uploadRequest.fold(
      errorStatus => Done(Left(errorStatus), Input.Empty),
      request => handleUploadRequest(userId, guid, request))
  }

  /**
   * @return an Iteratee for processing an upload request specified by info
   * The Iteratee will continue to consume the uploaded data even if an
   * error is encountered, but will not ignore the data received after the
   * error occurs.
   */
  private def handleUploadRequest(userId: Long, guid: UUID, request: UploadRequest): Iteratee[Array[Byte], Either[Result, OverviewUpload]] = {
    val initialUpload = findValidUploadRestart(userId, guid, request)
      .getOrElse(createUpload(userId, guid, request.filename, request.contentLength).toRight(InternalServerError))

    Iteratee.fold(initialUpload) { (upload, chunk) =>
      upload.right.flatMap(u => appendChunk(u, chunk).toRight(InternalServerError))
    }
  }

  /**
   * If the upload exists, verify the validity of the restart.
   * @return None if upload does not exist, otherwise an Either containing
   * an error status if request is invalid or the valid OverviewUpload.
   * If start is 0, any previously uploaded data is truncated.
   */
  private def findValidUploadRestart(userId: Long, guid: UUID, info: UploadRequest): Option[Either[Result, OverviewUpload]] =
    findUpload(userId, guid).map(u =>
      info.start match {
        case 0 => Right(u.truncate)
        case n if n == u.bytesUploaded => Right(u)
        case _ => {
          ignoring(classOf[SQLException]) { cancelUpload(u) } 
          Left(BadRequest)
        }
      })

  // Find an existing upload attempt
  def findUpload(userId: Long, guid: UUID): Option[OverviewUpload]

  // create a new upload attempt
  def createUpload(userId: Long, guid: UUID, filename: String, contentLength: Long): Option[OverviewUpload]

  // process a chunk of file data. @return the current OverviewUpload status, or None on failure	  
  def appendChunk(upload: OverviewUpload, chunk: Array[Byte]): Option[OverviewUpload]

  // Remove all data from previously started upload
  def cancelUpload(upload: OverviewUpload)
}

/** Implementation that writes to database */
object FileUploadIteratee extends FileUploadIteratee {

  def findUpload(userId: Long, guid: UUID) = withPgConnection { implicit c => OverviewUpload.find(userId, guid) }

  def createUpload(userId: Long, guid: UUID, filename: String, contentLength: Long): Option[OverviewUpload] = withPgConnection { implicit c =>
    LO.withLargeObject { lo => OverviewUpload(userId, guid, filename, contentLength, lo.oid).save }
  }

  def appendChunk(upload: OverviewUpload, chunk: Array[Byte]): Option[OverviewUpload] = withPgConnection { implicit c =>
    LO.withLargeObject(upload.contentsOid) { lo => upload.withUploadedBytes(lo.add(chunk)).save }
  }

  def cancelUpload(upload: OverviewUpload) = withPgConnection { implicit c =>
    LO.delete(upload.contentsOid)
    upload.delete
  }

  /**
   * Duplicates functionality in TransactionActionController, but in a way that
   * enables us to get a hold of a PGConnection.
   * DB.withConnection gives us a Play AutoCleanConnection, which we can't cast.
   * DB.getConnection gives us a BoneCP ConnectionHandle, which can
   * be converted to the PGConnection we need for dealing with Postgres
   * LargeObjects.
   */
  private def withPgConnection[A](f: PGConnection => A) = {
    val connection = DB.getConnection(autocommit = false)
    try {
      val adapter = new SquerylPostgreSqlAdapter()
      val session = new Session(connection, adapter)
      using(session) {
        val connectionHandle = connection.asInstanceOf[ConnectionHandle]
        val pgConnection = connectionHandle.getInternalConnection.asInstanceOf[PGConnection]

        val r = f(pgConnection)
        connection.commit // simply closing the connection does not seem to commit the transaction.
        r
      }
    } finally {
      connection.close
    }
  }

}
