package bootstrap.liftweb

import java.sql.{Connection, DriverManager}

import net.liftweb.common.{Box, Empty, Failure, Full, Logger}
import net.liftweb.db.ConnectionManager
import net.liftweb.util.ConnectionIdentifier
import net.liftweb.util.Helpers.tryo

/**
 * The standard DB vendor.
 *
 * @param driverName the name of the database driver
 * @param dbUrl the URL for the JDBC data connection
 * @param dbUser the optional username
 * @param dbPassword the optional db password
 */
class CustomDBVendor(driverName: String,
                     dbUrl: String,
                     dbUser: Box[String],
                     dbPassword: Box[String]) extends CustomProtoDBVendor {

  private val logger = Logger(classOf[CustomDBVendor])

  protected def createOne: Box[Connection] =  {
    tryo{t:Throwable => logger.error("Cannot load database driver: %s".format(driverName), t)}{Class.forName(driverName);()}

    (dbUser, dbPassword) match {
      case (Full(user), Full(pwd)) =>
        tryo{t:Throwable => logger.error("Unable to get database connection. url=%s, user=%s".format(dbUrl, user),t)}(DriverManager.getConnection(dbUrl, user, pwd))
      case _ =>
        tryo{t:Throwable => logger.error("Unable to get database connection. url=%s".format(dbUrl),t)}(DriverManager.getConnection(dbUrl))
    }
  }
}

trait CustomProtoDBVendor extends ConnectionManager {
  private val logger = Logger(classOf[CustomProtoDBVendor])
  private var pool: List[Connection] = Nil
  private var poolSize = 0
  private var tempMaxSize = maxPoolSize

  /**
   * Override and set to false if the maximum pool size can temporarily be expanded to avoid pool starvation
   */
  protected def allowTemporaryPoolExpansion = true

  /**
   *  Override this method if you want something other than
   * 20 connections in the pool
   */
  protected def maxPoolSize = 20

  /**
   * The absolute maximum that this pool can extend to
   * The default is 40.  Override this method to change.
   */
  protected def doNotExpandBeyond = 30

  /**
   * The logic for whether we can expand the pool beyond the current size.  By
   * default, the logic tests allowTemporaryPoolExpansion &amp;&amp; poolSize &lt;= doNotExpandBeyond
   */
  protected def canExpand_? : Boolean = allowTemporaryPoolExpansion && poolSize <= doNotExpandBeyond

  /**
   *   How is a connection created?
   */
  protected def createOne: Box[Connection]

  /**
   * Test the connection.  By default, setAutoCommit(false),
   * but you can do a real query on your RDBMS to see if the connection is alive
   */
  protected def testConnection(conn: Connection) {
    conn.setAutoCommit(false)
  }

  // Tail Recursive function in order to avoid Stack Overflow
  // PLEASE NOTE: Changing this function you can break the above named feature
  def newConnection(name: ConnectionIdentifier): Box[Connection] = {
    val (connection: Box[Connection], error: Boolean) = commonPart(name)
    error match {
      case false => connection
      case true => newConnection(name)
    }
  }
  
  def commonPart(name: ConnectionIdentifier): (Box[Connection], Boolean) = synchronized {
   val (connection: Box[Connection], error: Boolean) = pool match {
      case Nil if poolSize < tempMaxSize =>
        val ret = createOne
        ret.foreach(_.setAutoCommit(false))
        poolSize = poolSize + 1
        logger.debug("Created new pool entry. name=%s, poolSize=%d".format(name, poolSize))
        (ret, false)

      case Nil =>
        val curSize = poolSize
        logger.trace("No connection left in pool, waiting...")
        wait(50L)
        // if we've waited 50 ms and the pool is still empty, temporarily expand it
        if (pool.isEmpty && poolSize == curSize && canExpand_?) {
          tempMaxSize += 1
          logger.debug("Temporarily expanding pool. name=%s, tempMaxSize=%d".format(name, tempMaxSize))
        }
        (Empty, true)

      case x :: xs =>
        logger.trace("Found connection in pool, name=%s".format(name))
        pool = xs
        try {
          this.testConnection(x)
          (Full(x), false)
        } catch {
          case e: Exception => try {
            logger.debug("Test connection failed, removing connection from pool, name=%s".format(name))
            poolSize = poolSize - 1
            tryo(x.close)
            (Empty, true)
          } catch {
            case e: Exception => (Empty, true)
          }
        }
    }
    (connection, error)
  }

  def releaseConnection(conn: Connection): Unit = synchronized {
    if (tempMaxSize > maxPoolSize) {
      tryo {conn.close()}
      tempMaxSize -= 1
      poolSize -= 1
    } else {
      pool = conn :: pool
    }
    logger.debug("Released connection. poolSize=%d".format(poolSize))
    notifyAll
  }

  def closeAllConnections_!(): Unit = _closeAllConnections_!(0)


  private def _closeAllConnections_!(cnt: Int): Unit = synchronized {
    logger.info("Closing all connections")
    if (poolSize <= 0 || cnt > 10) ()
    else {
      pool.foreach {c => tryo(c.close); poolSize -= 1}
      pool = Nil

      if (poolSize > 0) wait(250)

      _closeAllConnections_!(cnt + 1)
    }
  }
}
