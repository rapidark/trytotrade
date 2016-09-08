package tryto.trade.ui;

import com.jfinal.core.Controller;

import tryto.trade.web.Api;
import tryto.trade.web.WebTrader;

public class TradeController extends Controller {
	public void index() {
		redirect("/trade/index.html");
	}
}