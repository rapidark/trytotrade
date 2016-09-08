package tryto.trade.ui;

import com.jfinal.config.Constants;
import com.jfinal.config.Handlers;
import com.jfinal.config.Interceptors;
import com.jfinal.config.JFinalConfig;
import com.jfinal.config.Plugins;
import com.jfinal.config.Routes;
import com.jfinal.ext.handler.UrlSkipHandler;

public class TradeConfig extends JFinalConfig {
	public void configConstant(Constants me) {
		me.setDevMode(true);
	}

	public void configRoute(Routes me) {
		me.add("/", IndexController.class);// 第三个参数为该Controller的视图存放路径
		me.add("/trade", TradeController.class);
	}

	public void configPlugin(Plugins me) {
		loadPropertyFile("config.txt");
		
	}

	public void configInterceptor(Interceptors me) {
	}

	public void configHandler(Handlers me) {
		me.add(new UrlSkipHandler("/ws/*",false));
	}
}