package code.util

import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

import code.api.util.{APIUtil, CustomJsonFormats}
import code.util.Helper.MdcLoggable
import org.apache.http.client.config.RequestConfig
import org.apache.http.client.methods.{HttpGet, HttpPost, HttpPut, HttpDelete, HttpRequestBase}
import org.apache.http.entity.StringEntity
import org.apache.http.impl.client.{HttpClientBuilder, CloseableHttpClient}
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager
import org.apache.http.util.EntityUtils
import org.apache.http.{HttpEntity, HttpResponse}

import scala.concurrent.{Future, Promise}
import scala.util.{Try, Success, Failure}
import com.openbankproject.commons.ExecutionContext.Implicits.global

object StandardHttpClient extends MdcLoggable with CustomJsonFormats {

  // HTTP method enumeration
  object HttpMethods {
    sealed trait HttpMethod
    case object GET extends HttpMethod
    case object POST extends HttpMethod
    case object PUT extends HttpMethod
    case object DELETE extends HttpMethod
  }
  type HttpMethod = HttpMethods.HttpMethod

  // Response case class
  case class HttpClientResponse(
    statusCode: Int,
    body: String,
    headers: Map[String, String] = Map.empty
  )

  private val httpRequestTimeout = APIUtil.getPropsAsIntValue("rest2019_connector_timeout").openOr(59)

  // Connection pool manager
  private val connectionManager = new PoolingHttpClientConnectionManager()
  connectionManager.setMaxTotal(200)
  connectionManager.setDefaultMaxPerRoute(20)
  connectionManager.closeIdleConnections(30, TimeUnit.SECONDS)

  // Request configuration
  private val requestConfig = RequestConfig.custom()
    .setSocketTimeout(httpRequestTimeout * 1000)
    .setConnectTimeout(httpRequestTimeout * 1000)
    .setConnectionRequestTimeout(httpRequestTimeout * 1000)
    .build()

  // HTTP client instance
  private val httpClient: CloseableHttpClient = HttpClientBuilder.create()
    .setConnectionManager(connectionManager)
    .setDefaultRequestConfig(requestConfig)
    .setRetryHandler((exception, executionCount, context) => {
      executionCount < 3 && exception.isInstanceOf[java.net.SocketTimeoutException]
    })
    .build()

  /**
   * Create HTTP request based on method and parameters
   */
  private def createRequest(uri: String, method: HttpMethod, body: String = ""): HttpRequestBase = {
    val request: HttpRequestBase = method match {
      case HttpMethods.GET => new HttpGet(uri)
      case HttpMethods.POST => 
        val post = new HttpPost(uri)
        if (body.nonEmpty) {
          val entity = new StringEntity(body, StandardCharsets.UTF_8)
          entity.setContentType("application/json")
          post.setEntity(entity)
        }
        post
      case HttpMethods.PUT => 
        val put = new HttpPut(uri)
        if (body.nonEmpty) {
          val entity = new StringEntity(body, StandardCharsets.UTF_8)
          entity.setContentType("application/json")
          put.setEntity(entity)
        }
        put
      case HttpMethods.DELETE => new HttpDelete(uri)
    }
    
    // Set common headers
    request.setHeader("Accept", "application/json")
    request.setHeader("User-Agent", "OBP-API-StandardHttpClient/1.0")
    
    request
  }

  /**
   * Execute HTTP request and return Future[HttpClientResponse]
   */
  def makeHttpRequest(uri: String, method: HttpMethod, body: String = ""): Future[HttpClientResponse] = {
    val promise = Promise[HttpClientResponse]()
    
    Future {
      val request = createRequest(uri, method, body)
      logger.debug(s"Making HTTP request: ${method} ${uri}")
      
      Try {
        val response: HttpResponse = httpClient.execute(request)
        val statusCode = response.getStatusLine.getStatusCode
        
        val responseBody = Option(response.getEntity) match {
          case Some(entity) =>
            val content = EntityUtils.toString(entity, StandardCharsets.UTF_8)
            EntityUtils.consume(entity) // Ensure entity is fully consumed
            content
          case None => ""
        }
        
        val headers = response.getAllHeaders.map(header => 
          header.getName -> header.getValue
        ).toMap
        
        logger.debug(s"HTTP response: ${statusCode} for ${method} ${uri}")
        HttpClientResponse(statusCode, responseBody, headers)
        
      } match {
        case Success(response) => promise.success(response)
        case Failure(exception) => 
          logger.error(s"HTTP request failed: ${method} ${uri}", exception)
          promise.failure(exception)
      }
    }
    
    promise.future
  }

  /**
   * Convenience method for GET requests
   */
  def get(uri: String): Future[HttpClientResponse] = {
    makeHttpRequest(uri, HttpMethods.GET)
  }

  /**
   * Convenience method for POST requests
   */
  def post(uri: String, body: String): Future[HttpClientResponse] = {
    makeHttpRequest(uri, HttpMethods.POST, body)
  }

  /**
   * Convenience method for PUT requests
   */
  def put(uri: String, body: String): Future[HttpClientResponse] = {
    makeHttpRequest(uri, HttpMethods.PUT, body)
  }

  /**
   * Convenience method for DELETE requests
   */
  def delete(uri: String): Future[HttpClientResponse] = {
    makeHttpRequest(uri, HttpMethods.DELETE)
  }

  /**
   * Shutdown the HTTP client and cleanup resources
   */
  def shutdown(): Unit = {
    Try {
      httpClient.close()
      connectionManager.close()
    }.recover {
      case exception => logger.error("Error shutting down StandardHttpClient", exception)
    }
  }

  // Cleanup hook
  sys.addShutdownHook {
    shutdown()
  }
}