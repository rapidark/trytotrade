package tryto.trade.ui;

import com.jfinal.core.Controller;
 
public class IndexController extends Controller {
	public void index() {
		redirect("/index.html");
	}
	
}