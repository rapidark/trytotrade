package tryto.trade.ui;

import com.jfinal.core.JFinal;

import tryto.trade.web.Api;
import tryto.trade.web.WebTrader;

public class TrytoTrade {

	public static void main(String[] args) {
		String webAppDir="src/main/webapp";
		int port=80;
		String context="/";
		int scanIntervalSeconds = 5;
		if(args.length>0){
			webAppDir =args[0];
			port=Integer.parseInt(args[1]);
		}
		JFinal.start(webAppDir, port, "/", scanIntervalSeconds);
	}

}
