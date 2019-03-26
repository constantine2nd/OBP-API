package code.consent

import net.liftweb.common.Box
import net.liftweb.util.SimpleInjector

object Consents extends SimpleInjector {
  val consentProvider = new Inject(buildOne _) {}
  def buildOne: ConsentProvider = MappedConsentProvider
}

trait ConsentProvider {
  def getConsentByConsentId(consentId: String): Box[MappedConsent]
  def createConsent(): Box[MappedConsent]
  def updateConsent(consentId: String, jwt: String): Box[MappedConsent]
}

trait Consent {
  def consentId: String
  def secret: String
  def status: String
  def challenge: String
  def jsonWebToken: String
}

object ConsentStatus extends Enumeration {
  type ConsentStatus = Value
  val INITIATED, ACCEPTED, REJECTED, REVOKED = Value
}







