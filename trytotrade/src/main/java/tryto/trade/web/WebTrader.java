package tryto.trade.web;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.imageio.ImageIO;

import org.apache.http.HttpEntity;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.ParseException;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;

public abstract class WebTrader {
	private static Logger log = LoggerFactory.getLogger(HTTrader.class);
	protected String global_config_path = "config/global.json";
	protected String config_path = "";
	protected String trade_prefix;
	protected JsonNode account_config;
	protected boolean heart_active;
	protected Heart_thread heart_thread;
	protected JsonNode config;
	protected JsonNode global_config;

	// account_info
	protected int sh_exchange_type;
	protected String sh_stock_account;
	protected int sz_exchange_type;
	protected String sz_stock_account;

	protected String fund_account;
	protected String client_risklevel;
	protected String op_station;
	protected String trdpwd;
	protected String uid;
	protected String branch_no;

	// ------
	protected JsonNode balance;// 账户资金状况
	protected JsonNode position;// 持仓
	protected JsonNode entrust;// 当日委托列表
	protected JsonNode current_deal;// 当日委托列表

	protected void init() {
		read_config();
		trade_prefix = config.get("prefix").asText();
		heart_active = true;
		heart_thread = new Heart_thread();
	}

	public void read_config(String path) {
		account_config = Helpers.file2dict(path);
	}

	/**
	 * 登录的统一接口
	 * 
	 * @param need_data
	 *            登录所需数据
	 */
	public void prepare(String need_data) {
		read_config(need_data);
		autologin();
	}

	public void autologin() {
		autologin(10);
	}

	/**
	 * 实现自动登录
	 * 
	 * @param limit=10
	 *            登录次数限制
	 */
	public void autologin(int limit) {
		boolean flag = false;
		for (int i = 0; i < limit; i++) {
			flag = login();
			if (flag) {
				break;
			}
		}
		if (!flag) {
			log.error("登录失败次数过多,请检查密码是否正确/券商服务器是否处于维护中/网络连接是否正常？");
		} else {
			keepalive();
		}
	}

	public abstract boolean login();

	/**
	 * 启动保持在线的进程
	 */
	public void keepalive() {
		if (heart_thread.isAlive()) {
			heart_active = true;
		} else {
			heart_thread.start();
		}
	}

	/**
	 * 每隔10秒查询指定接口保持 token 的有效性
	 */
	public void send_heartbeat() {
		try {
			while (true) {
				if (heart_active) {
					try {
						JsonNode response = heartbeat();
						check_account_live(response);
					} catch (Exception e) {
						autologin();
					}
					Thread.sleep(10000);
				} else {
					Thread.sleep(1);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public JsonNode heartbeat() {
		return get_balance();
	}

	public abstract  void check_account_live(JsonNode response);


	/**
	 * 结束保持 token 在线的进程
	 */
	public void exit() {
		heart_active = false;
	}

	/**
	 * 读取 config
	 */
	private void read_config() {
		config = Helpers.file2dict(config_path);
		global_config = Helpers.file2dict(global_config_path);
		// self.config.update(self.global_config)

	}

	/**
	 * 获取账户资金状况
	 * 
	 * @return
	 */
	public JsonNode get_balance() {
		balance = self_do(config.get("balance"));
		return balance;
	}

	/**
	 * 获取持仓
	 * 
	 * @return
	 */
	public JsonNode get_position() {
		position = self_do(config.get("position"));
		return position;
	}

	/**
	 * 获取当日委托列表
	 * 
	 * @return
	 */
	public JsonNode get_entrust() {
		entrust = self_do(config.get("entrust"));
		return entrust;
	}

	/**
	 * 获取当日委托列表
	 * 
	 * @return
	 */
	public JsonNode get_current_deal() {
		// current_deal = entrust=self_do(config.get("current_deal"));
		// TODO 目前仅在 佣金宝子类 中实现
		log.info("目前仅在 佣金宝/银河子类 中实现, 其余券商需要补充");
		return current_deal;
	}

	/**
	 * 发起对 api 的请求并过滤返回结果
	 * 
	 * @param params
	 *            交易所需的动态参数
	 * @return
	 */
	public JsonNode self_do(JsonNode params) {
		JsonNode return_data = null;
		StringBuilder request_params = create_basic_params();
		update(request_params,params);
		String response_data = request(request_params.toString());
		JsonNode format_json_data = format_response_data(response_data);
		if (format_json_data != null) {
			return_data = fix_error_data(format_json_data);
			// try:
			// self.check_login_status(return_data)
			// except NotLoginError:
			// self.autologin()
		}
		return return_data;
	}
	public void update(StringBuilder request_params,JsonNode params){
		Iterator<String> keys = params.fieldNames();
		while (keys.hasNext()) {
			String fieldName = keys.next();
			request_params.append("&").append(fieldName).append("=").append(params.path(fieldName).asText());
		}
	}

	/**
	 * 生成基本的参数
	 */
	public abstract StringBuilder create_basic_params();

	/**
	 * 请求并获取 JSON 数据
	 * 
	 * @param params
	 *            Get 参数
	 * @return
	 */
	public abstract String request(String params);

	/**
	 * 格式化返回的 json 数据
	 * 
	 * @param data
	 *            请求返回的数据
	 * @return
	 */
	public abstract JsonNode format_response_data(String data);

	/**
	 * 若是返回错误移除外层的列表
	 * 
	 * @param data
	 *            需要判断是否包含错误信息的数据
	 * @return
	 */
	public abstract JsonNode fix_error_data(JsonNode data);

	/**
	 * 格式化返回的值为正确的类型
	 * 
	 * @param response_data
	 * @return
	 */
	public JsonNode format_response_data_type(JsonNode response_data) {
//        if type(response_data) is not list:
//            return response_data
		String int_match_str = "/"+config.get("response_format").get("int").asText();
		String float_match_str = "/"+config.get("response_format").get("float").asText();
		for (int i = 0; i < response_data.size(); i++) {
			JsonNode item = response_data.get(i);
			for (int j = 0; j < item.size(); j++) {
				String key = item.get(j).asText();
				if(int_match_str.indexOf(key)>=0){
					
				}else if(float_match_str.indexOf(key)>=0){
					
				}
			}
		}
		return response_data;
		
	}

	public abstract void check_login_status(JsonNode return_data);
	// ===============================================================================================================

	/**
	 * 监视线程
	 */
	protected class Heart_thread extends Thread {
		protected Heart_thread() {
			setName("MINA连接监视线程");
			this.setDaemon(true);
		}

		@Override
		public void run() {
			send_heartbeat();
		}
	}

	protected CloseableHttpClient httpclient = HttpClients.createDefault();

	/**
	 * 发送 get请求
	 */
	public String get(String url) {
		String str = null;
		try {
			// 创建httpget.
			HttpGet httpget = new HttpGet(url);
			httpget.setHeader("User-Agent", "Mozilla/5.0 (Windows NT 6.1; WOW64; Trident/7.0; rv:11.0) like Gecko");
			// 执行get请求.
			CloseableHttpResponse response = httpclient.execute(httpget);
			log.debug("{}: {}", url, response.getStatusLine());
			if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
				// 获取响应实体
				HttpEntity entity = response.getEntity();
				if (entity != null) {
					str = EntityUtils.toString(entity, "utf-8");
					// 打印响应内容
					// System.out.println(EntityUtils.toString(entity, "utf-8"));
				}
			}
		} catch (ClientProtocolException e) {
			e.printStackTrace();
		} catch (ParseException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return str;
	}

	public String post(String url, List<NameValuePair> params) {
		String str = null;
		HttpPost post = new HttpPost(url);
		try {
			// url格式编码
			post.setEntity(new UrlEncodedFormEntity(params, "UTF-8"));
			CloseableHttpResponse response = httpclient.execute(post);
			if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
				// 获取响应实体
				HttpEntity entity = response.getEntity();
				if (entity != null) {
					str = EntityUtils.toString(entity, "utf-8");
					// 打印响应内容
					// System.out.println(str);
				}
			}
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		return str;
	}

	/**
	 * 发送 get请求
	 */
	public BufferedImage getBufferedImage(String url) {
		BufferedImage image = null;
		try {
			// 创建httpget.
			HttpGet httpget = new HttpGet(url);
			// 执行get请求.
			CloseableHttpResponse response = httpclient.execute(httpget);
			if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
				// 获取响应实体
				HttpEntity entity = response.getEntity();
				// // 打印响应状态
				InputStream is = entity.getContent();
				image = ImageIO.read(is);
			}
		} catch (ClientProtocolException e) {
			e.printStackTrace();
		} catch (ParseException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return image;
	}
}
