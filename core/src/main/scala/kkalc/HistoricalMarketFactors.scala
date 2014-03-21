package kkalc

import kkalc.model.Equity
import kkalc.pricing.{VolatilityOps, MarketFactors}
import kkalc.service.MarketDataModule
import org.joda.time.{Days, LocalDate}
import org.slf4j.LoggerFactory

abstract class HistoricalMarketFactors(date: LocalDate, rate: BigDecimal = 0.02, volatilityHorizon: Int = 1000)
  extends MarketFactors with VolatilityOps with MarketDataModule {

  private[this] val log = LoggerFactory.getLogger(classOf[HistoricalMarketFactors])

  log.debug(s"Construct historical market factors. Date = $date, volatility horizon = $volatilityHorizon days")

  override protected def price(equity: Equity): Option[BigDecimal] = {
    log.debug(s"Get price for $equity at $date")
    marketData.historicalPrice(equity, date).fold(_ => None, _.map(_.adjusted))
  }

  override protected def volatility(equity: Equity): Option[BigDecimal] = {
    log.debug(s"Get volatility for $equity")

    val prices = marketData.
      historicalPrices(equity, date.minusDays(volatilityHorizon), date).
      fold(_ => None, p => Some(p))

    prices.map(p => volatility(p))
  }

  override protected def daysToMaturity(maturity: LocalDate): Option[BigDecimal] = {
    if (date.isBefore(maturity)) {
      Some(BigDecimal(Days.daysBetween(date, maturity).getDays))

    } else None
  }

  protected def riskFreeRate: Option[BigDecimal] = Some(rate)
}