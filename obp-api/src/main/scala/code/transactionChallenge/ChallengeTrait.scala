package code.transactionChallenge

import net.liftweb.util.SimpleInjector





object Challenges extends SimpleInjector {

  val ChallengeProvider = new Inject(buildOne _) {}

  def buildOne: ChallengeProvider = MappedChallengeProvider
}


