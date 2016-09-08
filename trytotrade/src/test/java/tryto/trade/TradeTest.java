package tryto.trade;

import java.util.concurrent.CountDownLatch;

import tryto.trade.web.Api;
import tryto.trade.web.WebTrader;

public class TradeTest {
	private static final CountDownLatch shutdownLatch = new CountDownLatch(1);

	public static void main(String[] args) {
		WebTrader trader = Api.use("华泰", true);

		trader.prepare("config/ht.json");

		try {
			shutdownLatch.await();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

}
