package kkalc.service.historical

import kkalc.model.{HistoricalPrice, EquityOption, Equity, Portfolio}
import kkalc.pricing.{OneDayMarketFactorsGenerator, MarketFactorsGenerator, HistoricalVolatility, MarketFactors}
import kkalc.service.{MarketDataModule, MarketFactorsModule}

import org.joda.time.{Days, LocalDate}
import org.slf4j.LoggerFactory

trait HistoricalMarketFactors extends MarketFactorsModule with MarketDataModule with HistoricalVolatility { module =>

  protected def oneDayMarketFactors(portfolio: Portfolio, date: LocalDate)
                                  (implicit parameters: MarketFactorsParameters): MarketFactorsGenerator = {

    // Get equities
    val equities = portfolio.positions.map(_.instrument).map {
      case e: Equity => e
      case o: EquityOption => o.underlying
    }.sortBy(_.ticker)

    // Get prices at given date
    val price: Map[Equity, Double] =
      equities.
        map(equity => marketData.historicalPrice(equity, date).
        fold(
          err => sys.error(s"Market data for $equity is unavailable, error = $err"),
          priceO => priceO.map(price => (equity, price.adjusted)) getOrElse sys.error(s"Price for $equity at $date is not defined"))
        ).list.toMap

    // Get prices with defined horizon
    val historicalPrices: Map[Equity, Vector[HistoricalPrice]] =
      equities.
        map(equity => marketData.historicalPrices(equity, date.minusDays(parameters.horizon), date).
        fold(
          err => sys.error(s"Market data for $equity is unavailable, error = $err"),
          prices => (equity, prices)
        )).list.toMap

    // Calculate historical volatility
    val vol: Map[Equity, Double] = historicalPrices.mapValues(prices => volatility(prices)).map(identity)

    // get prices history for covariance matrix calculation
    val adjustedPrices = equities.map(equity => historicalPrices(equity).map(_.adjusted).toArray).stream.toArray

    new OneDayMarketFactorsGenerator(date, parameters.riskFreeRate, equities.list.toVector, price, vol, adjustedPrices)
  }

  protected def marketFactors(date: LocalDate)(implicit parameters: MarketFactorsParameters) = new MarketFactors {
    private val log = LoggerFactory.getLogger(classOf[MarketFactors])

    log.debug(s"Construct historical market factors. Date = $date, volatility horizon = ${parameters.horizon} days")

    override protected def price(equity: Equity): Option[Double] = {
      log.debug(s"Get price for $equity at $date")
      marketData.historicalPrice(equity, date).fold(_ => None, _.map(_.adjusted))
    }

    override protected def volatility(equity: Equity): Option[Double] = {
      log.debug(s"Get volatility for $equity")
      val prices = marketData.
        historicalPrices(equity, date.minusDays(parameters.horizon), date).
        fold(_ => None, p => Some(p))

      prices.map(p => module.volatility(p))
    }

    override protected def daysToMaturity(maturity: LocalDate): Option[Double] = {
      if (date.isBefore(maturity)) {
        Some(Days.daysBetween(date, maturity).getDays)

      } else None
    }

    protected def riskFreeRate: Option[Double] = Some(parameters.riskFreeRate)
  }
}