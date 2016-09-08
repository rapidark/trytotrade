package tryto.trade.web;

import tryto.trade.Broker;

public class Api {
	/**
	 * 用于生成特定的券商对象
	 * 
	 * @param broker
	 *            券商名支持 ['ht', 'HT', '华泰’] ['yjb', 'YJB', ’佣金宝'] ['yh', 'YH', '银河'] ['gf', 'GF', '广发']
	 * @param remove_zero
	 *            ht 可用参数，是否移除 08 账户开头的 0, 默认 True
	 */
	public static WebTrader use(String broker, boolean remove_zero) {
		broker = broker.toLowerCase();

		switch (broker) {
		case "ht":
		case "华泰":
			return new HTTrader(remove_zero);
		case "yjb":
		case "佣金宝":
			return new YJBTrader();
		case "yh":
		case "银河":
			return new YHTrader();
		case "xq":
		case "雪球":
			return new XueQiuTrader();
		case "gf":
		case "广发":
			return new GFTrader();
		default:
			return null;
		}
	}

}
